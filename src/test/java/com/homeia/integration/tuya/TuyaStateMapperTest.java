package com.homeia.integration.tuya;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuyaStateMapperTest {

    private static TuyaDataPoint dp(String code, Object value) {
        return new TuyaDataPoint(code, value);
    }

    @Test
    void enchufeConMedidorReportaEncendidoYVatiosConSuEscala() {
        List<TuyaDataPoint> enchufe = List.of(dp("switch_1", true), dp("countdown_1", 0L),
                dp("cur_current", 680L), dp("cur_power", 1503L), dp("cur_voltage", 1192L));
        Map<String, Object> report = TuyaStateMapper.toReport(enchufe, Map.of("cur_power", new TuyaScale(1, 0, 99999)));
        assertEquals("ON", report.get("powerState"));
        assertEquals(150.3, report.get("consumptionWatts"));
        assertFalse(report.containsKey("brightness"));
    }

    @Test
    void sinEspecificacionSuponeDecimasDeVatio() {
        Map<String, Object> report = TuyaStateMapper.toReport(List.of(dp("switch_1", false), dp("cur_power", 0L)), Map.of());
        assertEquals("OFF", report.get("powerState"));
        assertEquals(0.0, report.get("consumptionWatts"));
    }

    @Test
    void respetaUnaEscalaDistinta() {
        Map<String, Object> report = TuyaStateMapper.toReport(List.of(dp("cur_power", 15L)),
                Map.of("cur_power", new TuyaScale(0, 0, 99999)));
        assertEquals(15.0, report.get("consumptionWatts"));
    }

    @Test
    void bombilloReportaEncendidoYBrilloEnPorcentaje() {
        List<TuyaDataPoint> bombillo = List.of(dp("switch_led", true), dp("work_mode", "white"),
                dp("bright_value_v2", 500L), dp("temp_value_v2", 300L));
        Map<String, Object> report = TuyaStateMapper.toReport(bombillo,
                Map.of("bright_value_v2", new TuyaScale(0, 10, 1000)));
        assertEquals("ON", report.get("powerState"));
        assertEquals(50, report.get("brightness"));
        assertEquals("switch_led", TuyaStateMapper.powerCode(bombillo).orElseThrow());
    }

    @Test
    void bombilloAntiguoUsaBrilloDe25A255() {
        Map<String, Object> report = TuyaStateMapper.toReport(List.of(dp("switch_led", true), dp("bright_value", 255L)), Map.of());
        assertEquals(100, report.get("brightness"));
    }

    @Test
    void elComandoDeBrilloRespetaElMinimoDelDispositivo() {
        Map<String, TuyaScale> spec = Map.of("bright_value_v2", new TuyaScale(0, 10, 1000));
        assertEquals(new TuyaDataPoint("bright_value_v2", 750L), TuyaStateMapper.brightness("bright_value_v2", 75, spec));
        assertEquals(new TuyaDataPoint("bright_value_v2", 10L), TuyaStateMapper.brightness("bright_value_v2", 0, spec));
        assertEquals(new TuyaDataPoint("bright_value", 25L), TuyaStateMapper.brightness("bright_value", 0, Map.of()));
    }

    @Test
    void usaElPrimerCodigoDeEncendidoDisponible() {
        assertEquals("switch_1", TuyaStateMapper.powerCode(List.of(dp("switch_1", true), dp("switch_2", false))).orElseThrow());
        assertEquals("switch", TuyaStateMapper.powerCode(List.of(dp("switch", true))).orElseThrow());
        assertTrue(TuyaStateMapper.powerCode(List.of(dp("temp_current", 210L))).isEmpty(), "un sensor no tiene encendido");
    }
}
