package com.home.ia.infrastructure.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        log.info("Configurando Message Broker para WebSocket");

        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");

        log.info("Simple Message Broker habilitado");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        log.info("Registrando STOMP endpoints");

        registry.addEndpoint("/ws/notifications")
                .setAllowedOrigins("*")
                .withSockJS();

        registry.addEndpoint("/ws/devices")
                .setAllowedOrigins("*")
                .withSockJS();

        registry.addEndpoint("/ws/mqtt")
                .setAllowedOrigins("*")
                .withSockJS();

        log.info("STOMP endpoints registrados: /ws/notifications, /ws/devices, /ws/mqtt");
    }
}
