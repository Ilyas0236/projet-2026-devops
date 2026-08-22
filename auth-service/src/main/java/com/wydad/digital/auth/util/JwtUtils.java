package com.wydad.digital.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("id", userId)
                .claim("role", role)
                .claim("typ", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(Long userId, String email) {
        return Jwts.builder()
                .subject(email)
                .claim("id", userId)
                .claim("typ", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        return parseToken(token).getPayload().get("id", Long.class);
    }

    public String getEmailFromToken(String token) {
        return parseToken(token).getPayload().getSubject();
    }

    public String getRoleFromToken(String token) {
        return parseToken(token).getPayload().get("role", String.class);
    }

    public boolean validateToken(String token) {
        return validateToken(token, "access");
    }

    public boolean validateRefreshToken(String token) {
        // Un access token ne doit pas pouvoir servir de refresh token
        return validateToken(token, "refresh");
    }

    public boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = parseToken(token).getPayload();
            String typ = claims.get("typ", String.class);
            if (!expectedType.equals(typ)) {
                return false;
            }
            return true;
            } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Jws<Claims> parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
    }
}