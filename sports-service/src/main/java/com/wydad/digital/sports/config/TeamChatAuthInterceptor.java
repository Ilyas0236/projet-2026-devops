package com.wydad.digital.sports.config;

import com.wydad.digital.sports.ws.TeamChatWsController;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Phase 4 — authentification des frames STOMP. L'identité vient des
 * en-têtes X-User-* posés par la gateway (JWT déjà validé en amont) ;
 * sans identité, le CONNECT est refusé. Chaque SEND repasse ensuite par
 * les règles d'adhésion du service (défense en profondeur).
 */
@Component
@RequiredArgsConstructor
public class TeamChatAuthInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String userId = accessor.getFirstNativeHeader(TeamChatWsController.HDR_USER_ID);
            String role = accessor.getFirstNativeHeader(TeamChatWsController.HDR_USER_ROLE);
            if (userId == null || role == null) {
                // Handshake anonyme : refusé (le client recevra un ERROR STOMP).
                throw new IllegalArgumentException("Connexion chat non authentifiée");
            }
            Principal principal = new TeamChatPrincipal(Long.parseLong(userId), role);
            accessor.setUser(principal);
        }
        return message;
    }

    /** Identité immuable attachée à la session WebSocket. */
    public record TeamChatPrincipal(Long userId, String role) implements Principal {
        @Override
        public String getName() {
            return String.valueOf(userId);
        }
    }
}
