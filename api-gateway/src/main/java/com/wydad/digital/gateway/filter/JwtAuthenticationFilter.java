package com.wydad.digital.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    // Pas de fallback committé : un secret par défaut serait une porte dérobée
    // si la gateway démarre hors docker-compose. L'absence de JWT_SECRET doit
    // faire echouer le demarrage, pas signer avec une cle publique.
    @Value("${jwt.secret:${JWT_SECRET:}}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Sécurité : supprimer TOUJOURS les headers d'identité fournis par le client
        // (sinon un utilisateur peut s'attribuer X-User-Role: ADMIN et contourner
        // les @PreAuthorize des microservices qui font confiance à ces headers).
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Email");
                    h.remove("X-User-Role");
                    h.remove("X-User-Id");
                })
                .build();
        final ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();
        exchange = sanitizedExchange;

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        // Bypass OPTIONS requests for CORS preflight : on pose nous-mêmes les
        // headers CORS ici, car ce filtre tourne avec un ordre -1 (avant le
        // CorsWebFilter de Spring), et sans ça le preflight arrive nu en aval
        // et le navigateur reçoit 403 vide — cf. bug "PUT/DELETE impossible
        // depuis le dashboard admin" (30/08/2026).
        if ("OPTIONS".equals(method)) {
            org.springframework.http.HttpHeaders cors = new org.springframework.http.HttpHeaders();
            cors.add("Access-Control-Allow-Origin", request.getHeaders().getOrigin() != null
                ? request.getHeaders().getOrigin() : "*");
            cors.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
            cors.add("Access-Control-Allow-Headers", request.getHeaders().getFirst("Access-Control-Request-Headers") != null
                ? request.getHeaders().getFirst("Access-Control-Request-Headers") : "*");
            cors.add("Access-Control-Allow-Credentials", "true");
            cors.add("Access-Control-Max-Age", "3600");
            cors.add("Vary", "Origin, Access-Control-Request-Method, Access-Control-Request-Headers");
            exchange.getResponse().getHeaders().putAll(cors);
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK);
            return exchange.getResponse().setComplete();
        }

        // Phase 4 — handshake SockJS du chat de groupe : un upgrade WebSocket
        // ne peut pas porter de header Authorization depuis un navigateur.
        // Le JWT transite en en-tête natif STOMP du CONNECT et est validé par
        // sports-service lui-même (TeamChatAuthInterceptor) — la gateway
        // laisse donc passer le handshake sans en-tête d'identité.
        if (path.startsWith("/ws/team-chat")) {
            return gatewayBypass(exchange, chain);
        }

        // Auth-service : public endpoints (member-card et attestation exigent désormais un JWT)
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.equals("/api/auth/register-press")
                || path.equals("/api/auth/refresh") || path.equals("/api/auth/otp/send") || path.equals("/api/auth/otp/verify")
                || path.equals("/api/auth/password/reset")
                || path.equals("/api/auth/kyc/register-upload")
                // Catalogue public des abonnements (l'admin pilote, le visiteur consulte).
                // /plans : SubscriptionPlanController.listActive (coté auth-service : permitAll).
                // /zones : SubscriptionController.listZones (lecture publique SOLD_OUT masque).
                || path.equals("/api/auth/subscriptions/plans")
                || path.equals("/api/auth/subscriptions/plans/")
                || path.equals("/api/auth/subscriptions/zones")
                || path.equals("/api/auth/subscriptions/zones/")) { // KYC post-inscription : authentifié par email+password dans la requête, pas par JWT
            return chain.filter(exchange);
        }

        // member-card / attestation : JWT requis, et un utilisateur ne peut consulter que SA carte
        if (path.startsWith("/api/auth/member-card") || path.startsWith("/api/auth/attestation")) {
            return validateAndForward(exchange, chain);
        }

        // Content-service GET : public (lecture sans compte). Mais si un token
        // est fourni, il doit quand meme etre valide pour transmettre
        // l'identite au service : certains GET (ex. listing mediatheque) sont
        // reserves aux ADMIN via @PreAuthorize et dependent des headers
        // d'identite poses ici.
        if (path.startsWith("/api/content/") && "GET".equals(method)) {
            String contentAuthHeader = request.getHeaders().getFirst("Authorization");
            if (contentAuthHeader != null && contentAuthHeader.startsWith("Bearer ")) {
                return validateAndForward(exchange, chain);
            }
            return chain.filter(exchange);
        }

        // Gouvernance (election-service) : lecture publique sans compte.
        // Exigence B.8 — resultats publies visibles y compris des visiteurs non
        // connects ; sondages actifs en lecture libre ; elections en cours
        // (resultats partiels X/Y, scrutin OPEN) consultables sans compte.
        // Le service revalide : vote/cloture restent authentifies cote @PreAuthorize.
        if ("GET".equals(method)
                && (path.equals("/api/polls/active")
                    || path.startsWith("/api/elections/published")
                    || path.equals("/api/elections/open"))) {
            String governanceAuthHeader = request.getHeaders().getFirst("Authorization");
            if (governanceAuthHeader != null && governanceAuthHeader.startsWith("Bearer ")) {
                return validateAndForward(exchange, chain);
            }
            return chain.filter(exchange);
        }

        // Boutique & billetterie : catalogue consultable sans compte (pages
        // publiques boutique/billetterie). Les services revalident deja via
        // permitAll cote SecurityConfig ; achat/scan restent authentifies.
        if ("GET".equals(method)
                && (path.equals("/api/shop/products")
                    || path.matches("/api/shop/products/\\d+")
                    || path.startsWith("/api/ticket/events")
                    // Grille tarifaire BDD (TRIBUNE_OFFICIELLE, VIP, etc.) :
                    // alimente le <select> du formulaire admin et la home
                    // publique (Billetterie-fix). C'est une donnée catalogue,
                    // lue sans compte.
                    || path.equals("/api/ticket/categories"))) {
            String catalogAuthHeader = request.getHeaders().getFirst("Authorization");
            if (catalogAuthHeader != null && catalogAuthHeader.startsWith("Bearer ")) {
                return validateAndForward(exchange, chain);
            }
            return chain.filter(exchange);
        }

        // Gamification : leaderboard consultable sans compte — le service fait
        // déjà permitAll sur ce GET (SecurityConfig gamification) ; la gateway
        // doit laisser passer l'anonyme, sinon incohérence 401 côté visiteur.
        if ("GET".equals(method) && path.equals("/api/gamification/leaderboard")) {
            String gamAuthHeader = request.getHeaders().getFirst("Authorization");
            if (gamAuthHeader != null && gamAuthHeader.startsWith("Bearer ")) {
                return validateAndForward(exchange, chain);
            }
            return chain.filter(exchange);
        }

        // Newsletter : inscription anonyme depuis le footer public du site.
        // Le service revalide deja via permitAll + validation serveur du format
        // email et de l'unicite (NewsletterSecurityTest) — la gateway ne doit
        // pas exiger de JWT pour ce POST, sinon le pied de page est casse pour
        // tout visiteur non connecte.
        if (path.startsWith("/api/notification/newsletter/")) {
            return chain.filter(exchange);
        }

        // For all other routes, enforce JWT (this covers /api/auth/me, /api/auth/admin/**, and all other services)
        return validateAndForward(exchange, chain);

    }

    /** Bypass authentification : transmet la requête telle quelle (headers déjà nettoyés). */
    private Mono<Void> gatewayBypass(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange);
    }

    private Mono<Void> validateAndForward(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst("Authorization");

        // Secret JWT absent (JWT_SECRET non defini) : refuser plutot que
        // valider les tokens avec une cle vide.
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Jws<Claims> claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);

            String email = claims.getPayload().getSubject();
            String role = claims.getPayload().get("role", String.class);
            Long userId = claims.getPayload().get("id", Long.class); // peut être null pour les anciens tokens

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.set("X-User-Email", email);
                        h.set("X-User-Role", role != null ? role : "VISITEUR");
                        if (userId != null) {
                            h.set("X-User-Id", userId.toString());
                        }
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException | IllegalArgumentException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}