package com.homeia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeia.domain.DeviceProvider;
import com.homeia.domain.DeviceProtocol;
import com.homeia.integration.EdgeDiscoveryEvent;
import com.homeia.integration.MqttGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Inventario temporal de candidatos enviados por HomeIA Edge. Descubrir no implica emparejar ni dar acceso. */
@Slf4j
@Service
public class DeviceDiscoveryService {
    public record Candidate(String id, String name, String provider, String protocol, String address,
                            String manufacturer, String model, String service, List<String> capabilities, Instant discoveredAt) {}
    private final MqttGateway mqtt;
    private final ObjectMapper mapper;
    private final Map<String,Candidate> candidates = new ConcurrentHashMap<>();
    public DeviceDiscoveryService(MqttGateway mqtt, ObjectMapper mapper) { this.mqtt = mqtt; this.mapper = mapper; }
    public Map<String,Object> scan() {
        if (!mqtt.enabled() || !mqtt.connected()) return Map.of("accepted", false, "message", "MQTT no está conectado; verifica HomeIA Edge y el broker local");
        mqtt.publish("homeia/edge/scan", "{\"requestedAt\":\"" + Instant.now() + "\"}");
        return Map.of("accepted", true, "message", "Solicitud enviada a HomeIA Edge. Los candidatos aparecerán al recibirse el siguiente resultado.");
    }
    public Map<String,Object> permitZigbeeJoin(int seconds) {
        if (!mqtt.enabled() || !mqtt.connected()) throw ApiException.unavailable("MQTT no está conectado");
        mqtt.publish("zigbee2mqtt/bridge/request/permit_join", "{\"time\":" + seconds + "}");
        return Map.of("accepted", true, "seconds", seconds);
    }
    public List<Candidate> list() {
        Instant cutoff = Instant.now().minusSeconds(300);
        candidates.values().removeIf(c -> c.discoveredAt().isBefore(cutoff));
        return candidates.values().stream().sorted(Comparator.comparing(Candidate::discoveredAt).reversed()).toList();
    }
    public Optional<Candidate> get(String id) { return Optional.ofNullable(candidates.get(id)); }
    @EventListener public void onEdgeDiscovery(EdgeDiscoveryEvent event) {
        try {
            JsonNode root = mapper.readTree(event.payload());
            JsonNode list = root.isArray() ? root : root.path("candidates");
            if (!list.isArray()) return;
            for (JsonNode n : list) {
                String address = text(n,"address"); String provider = text(n,"provider"); String protocol = text(n,"protocol");
                if (address == null || provider == null || protocol == null) continue;
                try { DeviceProvider.valueOf(provider.toUpperCase(Locale.ROOT)); DeviceProtocol.valueOf(protocol.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException invalid) { log.debug("Proveedor/protocolo de Edge no soportado: {}/{}", provider, protocol); continue; }
                String name = Optional.ofNullable(text(n,"name")).orElse(address);
                String identity = provider + "|" + address + "|" + Optional.ofNullable(text(n,"service")).orElse("");
                String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
                List<String> capabilities = new ArrayList<>();
                JsonNode caps = n.path("capabilities");
                if (caps.isArray()) caps.forEach(c -> { if (c.isTextual()) capabilities.add(c.asText()); });
                candidates.put(id, new Candidate(id,name,provider,protocol,address,text(n,"manufacturer"),text(n,"model"),text(n,"service"),List.copyOf(capabilities),Instant.now()));
            }
        } catch (Exception e) { log.warn("Mensaje de descubrimiento Edge inválido: {}", e.getMessage()); }
    }
    private static String text(JsonNode n,String field) { JsonNode v=n.get(field); return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText(); }
    public DeviceProvider provider(Candidate c) { return DeviceProvider.valueOf(c.provider().toUpperCase(Locale.ROOT)); }
    public DeviceProtocol protocol(Candidate c) { return DeviceProtocol.valueOf(c.protocol().toUpperCase(Locale.ROOT)); }
}
