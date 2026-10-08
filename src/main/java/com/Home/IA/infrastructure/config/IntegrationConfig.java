package com.home.ia.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.core.MessagingTemplate;

@Configuration
public class IntegrationConfig {

    @Bean
    public MessagingTemplate messagingTemplate() {
        return new MessagingTemplate();
    }
}
