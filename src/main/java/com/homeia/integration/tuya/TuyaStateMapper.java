package com.homeia.integration.tuya;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce entre los valores de Tuya y el formato de estado de HomeIA
 * ({"powerState":"ON","consumptionWatts":12.5,"brightness":80}).
 *
 * Códigos de Tuya que entiende (los estándar de enchufes, interruptores y bombillos):
 * - Encendido: switch_1, switch, switch_led, switch_led_1.
 * - Potencia: cur_power, con su escala (scale 1 = décimas de vatio).
 * - Brillo: bright_value_v2 (10–1000) o bright_value (25–255), según la especificación del dispositivo.
 */
public final class TuyaStateMapper {

    static final List<String> POWER_CODES = List.of("switch_1", "switch", "switch_led", "switch_led_1");
    static final List<String> BRIGHTNESS_CODES = List.of("bright_value_v2", "bright_value");
    static final String CURRENT_POWER = "cur_power";
    /** La mayoría de enchufes Tuya reportan cur_power en décimas de vatio. */
    static final int DEFAULT_POWER_SCALE = 1;

    private TuyaStateMapper() {
    }

    /** Estado en el formato de HomeIA, solo con los valores que el dispositivo reportó. */
    public static Map<String, Object> toReport(List<TuyaDataPoint> points, Map<String, TuyaScale> scales) {
        Map<String, TuyaDataPoint> byCode = new LinkedHashMap<>();
        points.forEach(p -> byCode.put(p.code(), p));
        Map<String, Object> report = new LinkedHashMap<>();

        powerCode(points).ifPresent(code -> {
            Object value = byCode.get(code).value();
            if (value instanceof Boolean on) {
                report.put("powerState", on ? "ON" : "OFF");
            }
        });
        TuyaDataPoint power = byCode.get(CURRENT_POWER);
        if (power != null && power.value() instanceof Number raw) {
            int scale = scales.containsKey(CURRENT_POWER) ? scales.get(CURRENT_POWER).scale() : DEFAULT_POWER_SCALE;
            report.put("consumptionWatts", Math.round(raw.doubleValue() / Math.pow(10, scale) * 10) / 10.0);
        }
        brightnessCode(points).ifPresent(code -> {
            if (byCode.get(code).value() instanceof Number raw) {
                double max = maxFor(code, scales);
                report.put("brightness", (int) Math.max(0, Math.min(100, Math.round(raw.doubleValue() / max * 100))));
            }
        });
        return report;
    }

    /** Código de encendido que usa este dispositivo (switch_1 en enchufes, switch_led en bombillos…). */
    public static Optional<String> powerCode(List<TuyaDataPoint> points) {
        return POWER_CODES.stream().filter(c -> points.stream().anyMatch(p -> p.code().equals(c))).findFirst();
    }

    public static Optional<String> brightnessCode(List<TuyaDataPoint> points) {
        return BRIGHTNESS_CODES.stream().filter(c -> points.stream().anyMatch(p -> p.code().equals(c))).findFirst();
    }

    /** Comando para encender o apagar. */
    public static TuyaDataPoint power(String powerCode, boolean on) {
        return new TuyaDataPoint(powerCode, on);
    }

    /** Comando de brillo a partir de un porcentaje 0–100, respetando el mínimo y máximo del dispositivo. */
    public static TuyaDataPoint brightness(String brightnessCode, int percent, Map<String, TuyaScale> scales) {
        double max = maxFor(brightnessCode, scales);
        double min = scales.containsKey(brightnessCode) ? scales.get(brightnessCode).min()
                : ("bright_value".equals(brightnessCode) ? 25 : 10);
        long value = Math.round(Math.max(min, Math.min(max, percent / 100.0 * max)));
        return new TuyaDataPoint(brightnessCode, value);
    }

    private static double maxFor(String code, Map<String, TuyaScale> scales) {
        TuyaScale s = scales.get(code);
        if (s != null && s.max() > 0) return s.max();
        return "bright_value".equals(code) ? 255 : 1000;
    }
}
