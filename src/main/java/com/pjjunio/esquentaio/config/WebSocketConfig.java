package com.pjjunio.esquentaio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configura WebSocket com STOMP para atualizações em tempo real no lobby.
 * Clientes se conectam em /ws (com fallback SockJS) e subscrevem
 * tópicos como /topic/lobby/{codigo} para receber notificações.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");          // broker em memória
        config.setApplicationDestinationPrefixes("/app"); // prefixo p/ envio do cliente
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS(); // SockJS como fallback
    }
}
