package com.wydad.digital.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B.1 — Tests de verification JWT (auth-service) :
 * 1. un token signe par la bonne cle est valide et restitue id / email / role ;
 * 2. un token modifie (payload falsifie, ex. role eleve a ADMIN) est rejete
 *    par la verification de signature ;
 * 3. un token expire est rejete ;
 * 4. un access token ne peut pas servir de refresh token (claim typ).
 */
class JwtUtilsTest {

    private static final String SECRET =
            "wydad-secret-key-2024-ne-pas-utiliser-en-production-tres-long-min-256-bits";

    private JwtUtils jwtUtils;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtUtils, "refreshExpiration", 86_400_000L);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("un access token valide restitue id / email / role")
    void accessTokenValideRestitueLesClaims() {
        String token = jwtUtils.generateAccessToken(42L, "fan@wydad.ma", "ADHERENT");

        assertTrue(jwtUtils.validateToken(token));
        assertEquals(42L, jwtUtils.getUserIdFromToken(token));
        assertEquals("fan@wydad.ma", jwtUtils.getEmailFromToken(token));
        assertEquals("ADHERENT", jwtUtils.getRoleFromToken(token));
    }

    @Test
    @DisplayName("un payload falsifie (role eleve a ADMIN) casse la signature : token rejete")
    void payloadFalsifieEstRejeteParLaSignature() {
        String token = jwtUtils.generateAccessToken(42L, "fan@wydad.ma", "ADHERENT");

        // On decode le payload base64url, on remplace le role par ADMIN,
        // on renvoie le token sans resigner : la verification HMAC doit echouer.
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8);
        String forgedPayload = payload.replace("ADHERENT", "ADMIN");
        assertFalse(payload.equals(forgedPayload), "Le payload doit reellement avoir ete modifie");
        String forged = parts[0] + "." +
                java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertFalse(jwtUtils.validateToken(forged),
                "Un token dont le role a ete falsifie doit etre rejete");
        assertThrows(Exception.class, () -> jwtUtils.getRoleFromToken(forged),
                "Lire les claims d'un token falsifie doit echouer");
    }

    @Test
    @DisplayName("un token expire est rejete")
    void tokenExpireEstRejete() {
        // Token construit a la main avec expiration passee, meme cle :
        String expired = Jwts.builder()
                .subject("fan@wydad.ma")
                .claim("id", 42L)
                .claim("role", "ADHERENT")
                .claim("typ", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000L))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000L))
                .signWith(signingKey)
                .compact();

        assertFalse(jwtUtils.validateToken(expired), "Un token expire doit etre rejete");
    }

    @Test
    @DisplayName("un access token ne peut pas servir de refresh token (et vice versa)")
    void accessTokenNeSertPasDeRefreshToken() {
        String access = jwtUtils.generateAccessToken(42L, "fan@wydad.ma", "ADHERENT");
        String refresh = jwtUtils.generateRefreshToken(42L, "fan@wydad.ma");

        assertFalse(jwtUtils.validateRefreshToken(access),
                "Un access token ne doit pas passer pour refresh token");
        assertFalse(jwtUtils.validateToken(refresh),
                "Un refresh token ne doit pas passer pour access token");
        assertTrue(jwtUtils.validateRefreshToken(refresh));
        assertTrue(jwtUtils.validateToken(access));

        // Un token signe mais sans claim typ n'est accepte dans aucun circuit
        String noTyp = Jwts.builder()
                .subject("fan@wydad.ma")
                .signWith(signingKey)
                .compact();
        assertFalse(jwtUtils.validateToken(noTyp));
        assertFalse(jwtUtils.validateRefreshToken(noTyp));
    }
}
