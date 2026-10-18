package com.homeia.service;

import com.homeia.integration.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEnergyPlannerTest {

    private static final UUID KNOWN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UNKNOWN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void keepsOnlyKnownDevicesWithValidActions() {
        String json = """
                {"decisions":[
                  {"deviceId":"%s","action":"off","reason":"habitación vacía"},
                  {"deviceId":"%s","action":"OFF","reason":"no existe en la casa"},
                  {"deviceId":"%s","action":"DIMMER","reason":"acción inventada"},
                  {"deviceId":"no-es-uuid","action":"ON","reason":"id inválido"}
                ]}
                """.formatted(KNOWN, UNKNOWN, KNOWN);

        List<AiEnergyPlanner.Decision> decisions = AiEnergyPlanner.parse(Json.read(json), Set.of(KNOWN));

        assertEquals(1, decisions.size());
        assertEquals(KNOWN, decisions.get(0).deviceId());
        assertEquals("OFF", decisions.get(0).action());
    }

    @Test
    void missingDecisionsYieldsNothing() {
        assertTrue(AiEnergyPlanner.parse(Json.read("{}"), Set.of(KNOWN)).isEmpty());
    }

    @Test
    void longReasonIsTruncated() {
        String reason = "x".repeat(1000);
        String json = "{\"decisions\":[{\"deviceId\":\"" + KNOWN + "\",\"action\":\"KEEP\",\"reason\":\"" + reason + "\"}]}";

        AiEnergyPlanner.Decision decision = AiEnergyPlanner.parse(Json.read(json), Set.of(KNOWN)).get(0);

        assertEquals(300, decision.reason().length());
    }
}
