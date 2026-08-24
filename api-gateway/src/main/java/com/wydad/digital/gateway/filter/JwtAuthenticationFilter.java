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

        // Bypass OPTIONS requests for CORS preflight
        if ("OPTIONS".equals(method)) {
            return chain.filter(exchange);
        }

        // Auth-service : public endpoints (member-card et attestation exigent désormais un JWT)
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.equals("/api/auth/refresh") || path.equals("/api/auth/otp/send") || path.equals("/api/auth/otp/verify")
                || path.equals("/api/auth/password/reset")) { // S6 : reset de mot de passe protégé par l'OTP, pas par le JWT
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

        // For all other routes, enforce JWT (this covers /api/auth/me, /api/auth/admin/**, and all other services)
        return validateAndForward(exchange, chain);

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