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

    @Value("${jwt.secret:wydad-secret-key-2024-ne-pas-utiliser-en-production-tres-long-min-256-bits}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        // Bypass OPTIONS requests for CORS preflight
        if ("OPTIONS".equals(method)) {
            return chain.filter(exchange);
        }

        // Auth-service : public endpoints
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register") || path.equals("/api/auth/refresh") || path.equals("/api/auth/otp/send") || path.equals("/api/auth/otp/verify") || path.startsWith("/api/auth/member-card") || path.startsWith("/api/auth/attestation")) {
            return chain.filter(exchange);
        }

        // Content-service GET : public (pas besoin de JWT)
        if (path.startsWith("/api/content/") && "GET".equals(method)) {
            return chain.filter(exchange);
        }

        // For all other routes, enforce JWT (this covers /api/auth/me, /api/auth/admin/**, and all other services)
        return validateAndForward(exchange, chain);

    }

    private Mono<Void> validateAndForward(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst("Authorization");

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

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Email", email)
                    .header("X-User-Role", role != null ? role : "VISITEUR")
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