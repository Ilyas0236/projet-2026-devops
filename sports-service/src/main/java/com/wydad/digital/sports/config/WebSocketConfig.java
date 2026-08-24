package com.wydad.digital.sports.config;

import com.wydad.digital.sports.ws.TeamChatWsController;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Phase 4 — broker STOMP embarqué (texte uniquement). Le handshake exige
 * un JWT valide (voir {@link TeamChatWsController} pour la résolution de
 * l'identité) ; l'adhésion au groupe est revérifiée à CHAQUE envoi côté
 * service — le socket ne donne aucun droit, il n'est qu'un canal.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TeamChatAuthInterceptor teamChatAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Destinations de diffusion (serveur -> clients) et file d'envoi client.
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/team-chat")
                .setAllowedOriginPatterns("*")   // origine réellement bornée par la gateway/CORS
                .withSockJS();                   // fallback long-polling (proxies/réseaux restrictifs)
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Authentifie chaque frame CONNECT/SEND à partir du JWT transmis
        // par la gateway dans les en-têtes X-User-*.
        registration.interceptors(teamChatAuthInterceptor);
    }
}
