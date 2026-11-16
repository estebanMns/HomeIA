package com.homeia.service;

import com.homeia.domain.DeviceCapability;
import com.homeia.domain.DeviceProvider;
import com.homeia.domain.DeviceType;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Valida cada orden contra lo que el dispositivo realmente soporta antes de enviarla.
 * Si el dispositivo anunció capacidades (Zigbee exposes, ESPHome entities…) mandan esas; si no anunció
 * ninguna (dispositivos MQTT/Tuya antiguos) se usa lo que el tipo y el proveedor permiten.
 * Debe coincidir con homeIafrontend/src/lib/capabilities.ts.
 */
public final class DeviceCapabilityPolicy {

    public enum Action { POWER, BRIGHTNESS, STATUS }

    /** Proveedores cuyo adaptador sabe regular el brillo sin que el dispositivo lo anuncie. */
    private static final Set<DeviceProvider> DIMMABLE_BY_DEFAULT =
            EnumSet.of(DeviceProvider.MQTT, DeviceProvider.TUYA, DeviceProvider.TASMOTA, DeviceProvider.WLED);

    private DeviceCapabilityPolicy() {
    }

    /** Capacidades efectivas: las anunciadas o, si no hay, las inferidas del tipo y del proveedor. */
    public static Set<DeviceCapability> effective(DeviceType type, DeviceProvider provider, Set<DeviceCapability> announced) {
        if (announced != null && !announced.isEmpty()) {
            return EnumSet.copyOf(announced);
        }
        Set<DeviceCapability> inferred = EnumSet.noneOf(DeviceCapability.class);
        if (type == null) return inferred;
        switch (type) {
            case LIGHT -> {
                inferred.add(DeviceCapability.POWER);
                if (provider == null || DIMMABLE_BY_DEFAULT.contains(provider)) inferred.add(DeviceCapability.BRIGHTNESS);
            }
            case SMART_OUTLET -> {
                inferred.add(DeviceCapability.POWER);
                inferred.add(DeviceCapability.ENERGY);
            }
            case PRESENCE_SENSOR -> inferred.add(DeviceCapability.PRESENCE);
        }
        return inferred;
    }

    /** Vacío si la acción está permitida; si no, el motivo para el usuario. */
    public static Optional<String> check(DeviceType type, DeviceProvider provider, Set<DeviceCapability> announced,
                                         Action action) {
        if (action == Action.STATUS) return Optional.empty();
        Set<DeviceCapability> caps = effective(type, provider, announced);
        return switch (action) {
            case POWER -> caps.contains(DeviceCapability.POWER)
                    ? Optional.empty()
                    : Optional.of("Este dispositivo no admite encender ni apagar");
            case BRIGHTNESS -> caps.contains(DeviceCapability.BRIGHTNESS)
                    ? Optional.empty()
                    : Optional.of("Este dispositivo no admite regular el brillo");
            case STATUS -> Optional.empty();
        };
    }

    /** Convierte nombres de capacidades (JSON) ignorando los desconocidos. */
    public static Set<DeviceCapability> parse(Iterable<String> names) {
        Set<DeviceCapability> result = EnumSet.noneOf(DeviceCapability.class);
        if (names == null) return result;
        for (String name : names) {
            if (name == null) continue;
            try {
                result.add(DeviceCapability.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // capacidad que esta versión aún no conoce
            }
        }
        return result;
    }
}
