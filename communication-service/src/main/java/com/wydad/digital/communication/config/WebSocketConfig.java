package com.wydad.digital.communication.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Broker STOMP embarqué (texte uniquement). Le handshake n'exige rien au
 * niveau HTTP : l'identité est exigée par frame STOMP CONNECT (JWT validé
 * par {@link TeamChatAuthInterceptor}) et l'adhésion au groupe est
 * revérifiée à CHAQUE envoi côté service — le socket ne donne aucun droit,
 * il n'est qu'un canal.
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
        // Authentifie chaque frame CONNECT à partir du JWT en en-tête natif STOMP.
        registration.interceptors(teamChatAuthInterceptor);
    }
}
