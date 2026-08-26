package com.wydad.digital.communication.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

/**
 * Authentification des frames STOMP. L'upgrade WebSocket ne transporte pas
 * de header Authorization (impossible à poser depuis un navigateur) : le
 * client transmet donc son JWT en en-tête natif STOMP du frame CONNECT, et
 * CE SERVICE valide la signature lui-même — aucune confiance dans un
 * header X-User-* falsifiable, et la gateway n'a pas à laisser passer une
 * requête non authentifiée.
 *
 * <p>Le principal est posé au CONNECT puis attaché à la session. Les
 * SUBSCRIBE aux topics de groupe sont vérifiés contre le roster (un compte
 * authentifié ne peut pas écouter le chat d'une autre équipe) ; chaque SEND
 * repasse ensuite par les règles d'adhésion du service (défense en
 * profondeur).</p>
 */
@Component
public class TeamChatAuthInterceptor implements ChannelInterceptor {

    public static final String HDR_AUTHORIZATION = "Authorization";

    @Value("${wydad.jwt-secret:${JWT_SECRET:}}")
    private String jwtSecret;

    /** Adhésion au groupe (sport+catégorie), interrogée sur sports-service. */
    private final com.wydad.digital.communication.client.RosterClient rosterClient;

    public TeamChatAuthInterceptor(
            com.wydad.digital.communication.client.RosterClient rosterClient,
            @Value("${wydad.jwt-secret:${JWT_SECRET:}}") String jwtSecret) {
        this.rosterClient = rosterClient;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            verifyTopicMembership(accessor);
        }
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> auth = accessor.getNativeHeader(HDR_AUTHORIZATION);
            String token = (auth == null || auth.isEmpty())
                    ? null
                    : auth.get(0).replaceFirst("(?i)^Bearer\\s+", "");
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("Connexion chat sans JWT");
            }
            if (jwtSecret == null || jwtSecret.isBlank()) {
                throw new IllegalArgumentException("JWT_SECRET absent : chat indisponible");
            }
            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Jws<Claims> claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token);
                Long userId = claims.getPayload().get("id", Long.class);
                String role = claims.getPayload().get("role", String.class);
                if (userId == null) {
                    throw new IllegalArgumentException("Token sans identifiant utilisateur");
                }
                accessor.setUser(new TeamChatPrincipal(userId,
                        role != null ? role : "VISITEUR"));
            } catch (JwtException | IllegalArgumentException e) {
                throw new IllegalArgumentException("JWT invalide pour le chat", e);
            }
        }
        return message;
    }

    /**
     * Un SUBSCRIBE sur un topic de groupe n'est accepté que si la fiche
     * roster de l'abonné correspond à ce groupe (ADMIN : supervision).
     * Sinon le SUBSCRIBE est refusé — le client ne recevra jamais les
     * messages d'une équipe dont il ne fait pas partie.
     */
    private void verifyTopicMembership(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/chat/")) {
            // Seuls les topics de chat sont protégés ; autres destinations :
            // refus par précaution (aucune diffusion publique attendue).
            throw new IllegalArgumentException("Destination non autorisée");
        }
        java.util.regex.Matcher m = TOPIC_PATTERN.matcher(destination);
        if (!m.matches()) {
            throw new IllegalArgumentException("Topic de chat invalide");
        }
        String sport = m.group(1);
        String category = m.group(2);

        Principal principal = accessor.getUser();
        if (!(principal instanceof TeamChatPrincipal me)) {
            throw new IllegalArgumentException("Session non authentifiée");
        }
        if ("ADMIN".equals(me.role())) {
            return; // supervision
        }
        var mine = rosterClient.findMembership(me.userId());
        boolean member = mine != null
                && ("JOUEUR".equals(mine.rosterRole()) || "STAFF".equals(mine.rosterRole()))
                && sport.equalsIgnoreCase(mine.sportType())
                && category.equalsIgnoreCase(mine.category());
        if (!member) {
            throw new IllegalArgumentException("Ce groupe ne correspond pas à votre équipe");
        }
    }

    /** /topic/chat/{sport}/{category} (segments simples). */
    private static final java.util.regex.Pattern TOPIC_PATTERN =
            java.util.regex.Pattern.compile("^/topic/chat/([^/]+)/([^/]+)$");

    /** Identité immuable attachée à la session WebSocket. */
    public record TeamChatPrincipal(Long userId, String role) implements Principal {
        @Override
        public String getName() {
            return String.valueOf(userId);
        }
    }
}
