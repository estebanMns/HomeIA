package com.home.ia.infrastructure.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttConfig {

    private final MqttProperties mqttProperties;

    @Bean
    public MqttAdapter mqttAdapter() {
        log.info("Inicializando MQTT Adapter");
        MqttAdapter adapter = new MqttAdapter(mqttProperties);
        adapter.connectToBroker();
        return adapter;
    }

    @Component
    @org.springframework.boot.context.properties.ConfigurationProperties(prefix = "mqtt")
    public static class MqttProperties {
        private String brokerUrl;
        private String username;
        private String password;
        private String clientId;
        private boolean enabled;

        public String getBrokerUrl() {
            return brokerUrl;
        }

        public void setBrokerUrl(String brokerUrl) {
            this.brokerUrl = brokerUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
