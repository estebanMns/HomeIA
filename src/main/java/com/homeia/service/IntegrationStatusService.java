package com.homeia.service;

import com.homeia.domain.CommandStatus;
import com.homeia.domain.ConnectionStatus;
import com.homeia.domain.DeviceProvider;
import com.homeia.domain.EdgeStatus;
import com.homeia.config.HomeIaProperties;
import com.homeia.integration.MqttGateway;
import com.homeia.integration.tuya.TuyaGateway;
import com.homeia.persistence.DeviceCommandRepository;
import com.homeia.persistence.DeviceEntity;
import com.homeia.persistence.DeviceRepository;
import com.homeia.persistence.EdgeNodeEntity;
import com.homeia.persistence.EdgeNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Estado de cada integración por separado (broker, Edge, coordinador Zigbee, cada proveedor) y diagnóstico
 * paso a paso para el asistente de configuración. Nunca infiere que Zigbee funciona solo porque MQTT conectó.
 */
@Service
@RequiredArgsConstructor
public class IntegrationStatusService {

    /** Orden de gravedad: el diagnóstico global es el peor de sus pasos. */
    public enum CheckStatus { OK, PENDING, WARN, FAIL }

    public record Check(String id, String label, CheckStatus status, String detail, String hint) {
    }

    public record Diagnostic(String integration, CheckStatus overall, List<Check> checks) {
    }

    private final MqttGateway mqtt;
    private final TuyaGateway tuya;
    private final HomeIaProperties properties;
    private final EdgeNodeRepository edges;
    private final DeviceRepository devices;
    private final DeviceCommandRepository commands;
    private final DeviceDiscoveryService discovery;
    private final DeviceCredentialService credentials;

    @Transactional(readOnly = true)
    public Map<String, Object> status(UUID homeId) {
        List<DeviceEntity> all = devices.findByHomeIdOrderByNameAsc(homeId);
        List<EdgeNodeEntity> homeEdges = edges.findByHomeIdOrderByCreatedAtAsc(homeId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("broker", Map.of(
                "state", mqttState(),
                "tls", properties.mqtt() != null && properties.mqtt().tls(),
                "mutualTls", properties.mqtt() != null && properties.mqtt().clientCertPath() != null
                        && !properties.mqtt().clientCertPath().isBlank()));
        result.put("tuya", Map.of("configured", tuya.configured()));
        List<Map<String, Object>> zigbee = new ArrayList<>();
        for (EdgeNodeEntity e : homeEdges) {
            if (e.getZigbeeBridgeState() == null) continue;
            Map<String, Object> z = new LinkedHashMap<>();
            z.put("edgeId", e.getId());
            z.put("edgeName", e.getName());
            z.put("edgeStatus", e.getStatus());
            z.put("bridgeState", e.getZigbeeBridgeState());
            z.put("permitJoin", e.isZigbeePermitJoin());
            z.put("permitJoinUntil", e.getZigbeePermitJoinUntil());
            zigbee.add(z);
        }
        result.put("zigbee", zigbee);
        List<Map<String, Object>> providers = new ArrayList<>();
        for (DeviceProvider provider : DeviceProvider.values()) {
            List<DeviceEntity> ofProvider = all.stream().filter(d -> d.getProvider() == provider).toList();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("provider", provider);
            p.put("viaEdge", provider.viaEdge());
            p.put("devices", ofProvider.size());
            p.put("online", ofProvider.stream().filter(d -> d.getConnectionStatus() == ConnectionStatus.ONLINE).count());
            p.put("candidates", discovery.list(homeId).stream().filter(c -> c.provider().equals(provider.name())).count());
            providers.add(p);
        }
        result.put("providers", providers);
        result.put("pendingCommands", commands.countByHomeIdAndStatus(homeId, CommandStatus.SENT)
                + commands.countByHomeIdAndStatus(homeId, CommandStatus.PENDING));
        result.put("checkedAt", Instant.now());
        return result;
    }

    @Transactional(readOnly = true)
    public Diagnostic diagnose(UUID homeId, String integration) {
        String key = integration == null ? "" : integration.trim().toUpperCase(Locale.ROOT);
        List<Check> checks = new ArrayList<>();
        List<EdgeNodeEntity> homeEdges = edges.findByHomeIdOrderByCreatedAtAsc(homeId);
        List<DeviceEntity> all = devices.findByHomeIdOrderByNameAsc(homeId);
        switch (key) {
            case "TUYA" -> {
                checks.add(new Check("tuya-config", "Credenciales del proyecto Tuya IoT",
                        tuya.configured() ? CheckStatus.OK : CheckStatus.FAIL,
                        tuya.configured() ? "TUYA_ACCESS_ID y TUYA_ACCESS_SECRET definidos" : "Faltan las credenciales",
                        "Crea un proyecto Cloud en iot.tuya.com, vincula tu cuenta Smart Life y define TUYA_ACCESS_ID/TUYA_ACCESS_SECRET."));
                deviceChecks(checks, all, DeviceProvider.TUYA, homeId);
            }
            case "MQTT" -> {
                brokerChecks(checks);
                deviceChecks(checks, all, DeviceProvider.MQTT, homeId);
            }
            case "EDGE" -> {
                brokerChecks(checks);
                edgeChecks(checks, homeEdges);
            }
            case "ZIGBEE" -> {
                brokerChecks(checks);
                edgeChecks(checks, homeEdges);
                List<EdgeNodeEntity> withZigbee = homeEdges.stream().filter(e -> e.getZigbeeBridgeState() != null).toList();
                if (withZigbee.isEmpty()) {
                    checks.add(new Check("z2m-bridge", "Zigbee2MQTT detectado por el Edge", CheckStatus.FAIL,
                            "Ningún Edge informa el estado de Zigbee2MQTT",
                            "Levanta zigbee2mqtt con compose.edge.yaml, verifica ZIGBEE_SERIAL_DEVICE y que comparta broker local con el Edge."));
                }
                for (EdgeNodeEntity e : withZigbee) {
                    boolean online = "online".equalsIgnoreCase(e.getZigbeeBridgeState());
                    checks.add(new Check("z2m-bridge-" + e.getId(), "Coordinador Zigbee en " + e.getName(),
                            online ? CheckStatus.OK : CheckStatus.FAIL, "bridge/state = " + e.getZigbeeBridgeState(),
                            online ? null : "Revisa el log de zigbee2mqtt: puerto serie, adapter (ember/zstack) y firmware del coordinador."));
                    checks.add(new Check("z2m-permit-" + e.getId(), "Emparejamiento cerrado en " + e.getName(),
                            e.isZigbeePermitJoin() ? CheckStatus.WARN : CheckStatus.OK,
                            e.isZigbeePermitJoin() ? "permit_join está abierto" : "permit_join cerrado",
                            e.isZigbeePermitJoin() ? "Ciérralo cuando termines de emparejar." : null));
                }
                deviceChecks(checks, all, DeviceProvider.ZIGBEE, homeId);
            }
            case "SHELLY", "TASMOTA", "ESPHOME", "WLED" -> {
                DeviceProvider provider = DeviceProvider.valueOf(key);
                brokerChecks(checks);
                edgeChecks(checks, homeEdges);
                long candidates = discovery.list(homeId).stream().filter(c -> c.provider().equals(key)).count();
                checks.add(new Check("discovery", "Equipos " + key + " detectados en la LAN",
                        candidates > 0 ? CheckStatus.OK : CheckStatus.PENDING,
                        candidates + " candidato(s) en los últimos 10 minutos",
                        candidates > 0 ? null : "Pulsa \"Buscar dispositivos\". Si no aparecen, verifica que el Edge corra con red del host "
                                + "(network_mode: host) en la misma LAN y que el equipo anuncie mDNS. También puedes registrarlo por IP."));
                deviceChecks(checks, all, provider, homeId);
                if (provider == DeviceProvider.ESPHOME) {
                    long withKey = all.stream().filter(d -> d.getProvider() == provider && credentials.has(d.getId())).count();
                    long total = all.stream().filter(d -> d.getProvider() == provider).count();
                    if (total > 0) {
                        checks.add(new Check("esphome-key", "Clave de cifrado de la API nativa",
                                withKey == total ? CheckStatus.OK : CheckStatus.WARN, withKey + " de " + total + " con clave",
                                withKey == total ? null : "Si el firmware usa api: encryption: key, cárgala en Credenciales del dispositivo."));
                    }
                }
            }
            default -> throw ApiException.badRequest("Integración desconocida: " + integration);
        }
        CheckStatus overall = checks.stream().map(Check::status).reduce(CheckStatus.OK, IntegrationStatusService::worst);
        return new Diagnostic(key, overall, checks);
    }

    private void brokerChecks(List<Check> checks) {
        boolean enabled = mqtt.enabled();
        boolean connected = mqtt.connected();
        checks.add(new Check("mqtt-enabled", "MQTT habilitado en el backend", enabled ? CheckStatus.OK : CheckStatus.FAIL,
                enabled ? "MQTT_ENABLED=true" : "MQTT_ENABLED=false",
                enabled ? null : "Define MQTT_ENABLED=true, MQTT_BROKER_URL y el usuario MQTT del backend, y reinicia."));
        checks.add(new Check("mqtt-connected", "Backend conectado al broker", connected ? CheckStatus.OK : CheckStatus.FAIL,
                connected ? "Conectado" : "Sin conexión",
                connected ? null : "Comprueba MQTT_BROKER_URL, usuario/contraseña, ACL y que el puerto sea alcanzable (VPN o mTLS)."));
        boolean tls = properties.mqtt() != null && properties.mqtt().tls();
        checks.add(new Check("mqtt-tls", "Canal cifrado con el broker", tls ? CheckStatus.OK : CheckStatus.WARN,
                tls ? "ssl:// activo" : "Conexión sin TLS",
                tls ? null : "Sin TLS solo es aceptable dentro de una VPN o en la misma máquina. Ver docs/SEGURIDAD_MQTT.md."));
    }

    private void edgeChecks(List<Check> checks, List<EdgeNodeEntity> homeEdges) {
        if (homeEdges.isEmpty()) {
            checks.add(new Check("edge-enrolled", "HomeIA Edge enrolado", CheckStatus.FAIL, "No hay Edge en esta casa",
                    "Enrola un Edge en esta página y copia HOMEIA_EDGE_ID / HOMEIA_EDGE_SECRET en el equipo de la LAN."));
            return;
        }
        Duration timeout = Duration.ofSeconds(properties.edge() == null ? 90 : properties.edge().heartbeatTimeout());
        for (EdgeNodeEntity e : homeEdges) {
            if (e.getStatus() == EdgeStatus.REVOKED) continue;
            boolean online = e.getStatus() == EdgeStatus.ONLINE && e.getLastHeartbeatAt() != null
                    && e.getLastHeartbeatAt().plus(timeout).isAfter(Instant.now());
            checks.add(new Check("edge-" + e.getId(), "Heartbeat de " + e.getName(),
                    online ? CheckStatus.OK : (e.getStatus() == EdgeStatus.PENDING ? CheckStatus.PENDING : CheckStatus.FAIL),
                    e.getLastHeartbeatAt() == null ? "Nunca se ha conectado" : "Último heartbeat " + e.getLastHeartbeatAt(),
                    online ? null : "Revisa systemctl status homeia-edge (o docker logs), el reloj (NTP) y que el secreto coincida."));
            if (online && e.getQueueDepth() > 0) {
                checks.add(new Check("edge-queue-" + e.getId(), "Cola local de " + e.getName(), CheckStatus.WARN,
                        e.getQueueDepth() + " mensaje(s) pendientes", "El Edge está reenviando eventos acumulados durante una desconexión."));
            }
        }
    }

    private void deviceChecks(List<Check> checks, List<DeviceEntity> all, DeviceProvider provider, UUID homeId) {
        List<DeviceEntity> ofProvider = all.stream().filter(d -> d.getProvider() == provider).toList();
        long online = ofProvider.stream().filter(d -> d.getConnectionStatus() == ConnectionStatus.ONLINE).count();
        checks.add(new Check("devices-" + provider, "Dispositivos " + provider + " registrados",
                ofProvider.isEmpty() ? CheckStatus.PENDING : CheckStatus.OK, ofProvider.size() + " registrado(s)",
                ofProvider.isEmpty() ? "Incorpora un dispositivo para completar la integración." : null));
        if (!ofProvider.isEmpty()) {
            checks.add(new Check("devices-online-" + provider, "Dispositivos " + provider + " reportando estado",
                    online == ofProvider.size() ? CheckStatus.OK : (online == 0 ? CheckStatus.FAIL : CheckStatus.WARN),
                    online + " de " + ofProvider.size() + " en línea",
                    online == ofProvider.size() ? null : "Abre el historial del dispositivo para ver el último error."));
        }
    }

    private String mqttState() {
        if (!mqtt.enabled()) return "DISABLED";
        return mqtt.connected() ? "CONNECTED" : "DISCONNECTED";
    }

    static CheckStatus worst(CheckStatus a, CheckStatus b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
