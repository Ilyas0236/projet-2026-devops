package com.wydad.digital.sports.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 5 — génère les jetons d'accès LiveKit (format JWT vidéo : claims
 * video.grants) signés HMAC-SHA256 avec la clé/secret du projet LiveKit
 * Cloud. Le serveur est seul à décider du rôle dans la room :
 *  - organisateur  → roomAdmin + roomCreate (contrôle total)
 *  - participant   → canPublish/canSubscribe/canPublishData (rejoindre)
 * La room est pré-créée logiquement par l'appel (roomCreate sur le jeton
 * organisateur) ; LiveKit crée la room à la première connexion.
 *
 * Mode dégradé : si les clés LIVEKIT_* sont absentes du .env, la
 * génération échoue avec une erreur explicite (l'appel reste programmable,
 * seule la connexion média est indisponible).
 */
@Slf4j
@Service
public class LiveKitTokenService {

    private final SecretKey signingKey;
    private final String apiKey;
    private final boolean configured;

    public LiveKitTokenService(
            @Value("${livekit.api-key:}") String apiKey,
            @Value("${livekit.api-secret:}") String apiSecret) {
        this.apiKey = apiKey == null ? "" : apiKey;
        boolean secretPresent = apiSecret != null && !apiSecret.isBlank();
        this.signingKey = secretPresent
                ? Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8))
                : null;
        this.configured = !this.apiKey.isBlank() && secretPresent;
        if (!configured) {
            log.warn("LiveKit non configuré (LIVEKIT_API_KEY/LIVEKIT_API_SECRET absents) - jetons indisponibles");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Jeton d'accès à une room. Durée de vie courte (ttlSeconds) : le client
     * la redemande à chaque « rejoindre », jamais stockée.
     */
    public String createToken(String roomName, long identity, String displayName,
                              boolean asOrganizer, long ttlSeconds) {
        if (!configured) {
            throw new IllegalStateException("Service d'appels vidéo momentanément indisponible");
        }

        Map<String, Object> videoGrants = new HashMap<>();
        videoGrants.put("canPublish", true);
        videoGrants.put("canSubscribe", true);
        videoGrants.put("canPublishData", true);
        if (asOrganizer) {
            videoGrants.put("roomCreate", true);
            videoGrants.put("roomList", true);
            videoGrants.put("roomRecord", false);
            videoGrants.put("roomAdmin", true);
        }
        videoGrants.put("roomJoin", true);
        videoGrants.put("room", roomName);

        Map<String, Object> claims = new HashMap<>();
        claims.put("video", videoGrants);
        claims.put("name", displayName);

        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(apiKey)
                .subject(String.valueOf(identity))
                .id("call-" + identity + "-" + now.getEpochSecond())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .claims(claims)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
