package com.homeia.edge;

import com.homeia.domain.CommandStatus;
import com.homeia.domain.EdgeStatus;
import com.homeia.integration.MqttGateway;
import com.homeia.persistence.DeviceCommandRepository;
import com.homeia.persistence.EdgeNodeRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Métricas operativas para Prometheus/Grafana: broker, Edge en línea/desconectados y órdenes sin confirmar.
 * Alertar si homeia_edges_offline > 0 durante varios minutos o si homeia_commands_open crece sin parar.
 */
@Component
public class EdgeMetrics {

    public EdgeMetrics(MeterRegistry registry, MqttGateway mqtt, EdgeNodeRepository edges, DeviceCommandRepository commands) {
        Gauge.builder("homeia.mqtt.connected", () -> mqtt.connected() ? 1 : 0)
                .description("1 si el backend está conectado al broker MQTT").register(registry);
        Gauge.builder("homeia.edges.online", () -> edges.findByStatusIn(EnumSet.of(EdgeStatus.ONLINE)).size())
                .description("HomeIA Edge con heartbeat reciente").register(registry);
        Gauge.builder("homeia.edges.offline", () -> edges.findByStatusIn(EnumSet.of(EdgeStatus.OFFLINE)).size())
                .description("HomeIA Edge sin heartbeat").register(registry);
        Gauge.builder("homeia.commands.open",
                        () -> commands.findByStatusIn(EnumSet.of(CommandStatus.PENDING, CommandStatus.SENT)).size())
                .description("Órdenes a dispositivos esperando confirmación").register(registry);
    }
}
