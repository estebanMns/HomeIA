package com.homeia.service;

import com.homeia.domain.DeviceCapability;
import com.homeia.domain.DeviceProvider;
import com.homeia.domain.DeviceType;
import com.homeia.service.DeviceCapabilityPolicy.Action;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeviceCapabilityPolicyTest {

    @Test
    void brilloSoloEnLucesRegulables() {
        assertTrue(DeviceCapabilityPolicy.check(DeviceType.LIGHT, DeviceProvider.TUYA, Set.of(), Action.BRIGHTNESS).isEmpty());
        // Un relé Shelly no regula brillo salvo que lo anuncie
        assertTrue(DeviceCapabilityPolicy.check(DeviceType.LIGHT, DeviceProvider.SHELLY, Set.of(), Action.BRIGHTNESS).isPresent());
        assertTrue(DeviceCapabilityPolicy.check(DeviceType.LIGHT, DeviceProvider.SHELLY,
                EnumSet.of(DeviceCapability.POWER, DeviceCapability.BRIGHTNESS), Action.BRIGHTNESS).isEmpty());
        assertTrue(DeviceCapabilityPolicy.check(DeviceType.SMART_OUTLET, DeviceProvider.MQTT, Set.of(), Action.BRIGHTNESS).isPresent());
    }

    @Test
    void lasCapacidadesAnunciadasMandanSobreElTipo() {
        // Bombillo Zigbee que solo anuncia POWER: no se le envía brillo aunque sea LIGHT
        assertTrue(DeviceCapabilityPolicy.check(DeviceType.LIGHT, DeviceProvider.ZIGBEE,
                EnumSet.of(DeviceCapability.POWER), Action.BRIGHTNESS).isPresent());
        // Sensor Zigbee: no se enciende ni se apaga
        assertTrue(DeviceCapabilityPolicy.check(DeviceType.LIGHT, DeviceProvider.ZIGBEE,
                EnumSet.of(DeviceCapability.TEMPERATURE), Action.POWER).isPresent());
    }

    @Test
    void sensoresSoloConsultanEstado() {
        assertTrue(DeviceCapabilityPolicy.check(DeviceType.PRESENCE_SENSOR, DeviceProvider.MQTT, Set.of(), Action.POWER).isPresent());
        assertTrue(DeviceCapabilityPolicy.check(DeviceType.PRESENCE_SENSOR, DeviceProvider.MQTT, Set.of(), Action.STATUS).isEmpty());
    }

    @Test
    void ignoraNombresDesconocidos() {
        assertEquals(EnumSet.of(DeviceCapability.POWER, DeviceCapability.BRIGHTNESS),
                DeviceCapabilityPolicy.parse(List.of("power", "BRIGHTNESS", "TELEPORT")));
    }
}
