package com.homeia.service;

import com.homeia.domain.DeviceType;
import com.homeia.service.DeviceCommandPolicy.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceCommandPolicyTest {

    private static Outcome evaluar(boolean admin, DeviceType type, boolean critical,
                                   boolean changesPowerState, boolean turnsOff, boolean confirm) {
        return DeviceCommandPolicy.evaluate(admin, type, critical, "Nevera", changesPowerState, turnsOff, confirm)
                .outcome();
    }

    @Test
    void unSensorNoSeEnciendeNiSeApaga() {
        assertEquals(Outcome.INVALID, evaluar(true, DeviceType.PRESENCE_SENSOR, false, true, false, false));
        assertEquals(Outcome.INVALID, evaluar(true, DeviceType.PRESENCE_SENSOR, false, true, true, false));
    }

    @Test
    void unSensorSiAdmiteConsultarSuEstado() {
        assertEquals(Outcome.ALLOWED, evaluar(false, DeviceType.PRESENCE_SENSOR, false, false, false, false));
    }

    @Test
    void unMiembroNoPuedeDeshabilitarTomacorrientes() {
        assertEquals(Outcome.FORBIDDEN, evaluar(false, DeviceType.SMART_OUTLET, false, true, true, false));
    }

    @Test
    void unMiembroSiPuedeEncenderTomacorrientesYControlarLuces() {
        assertEquals(Outcome.ALLOWED, evaluar(false, DeviceType.SMART_OUTLET, false, true, false, false));
        assertEquals(Outcome.ALLOWED, evaluar(false, DeviceType.LIGHT, false, true, true, false));
    }

    @Test
    void apagarUnCriticoExigeConfirmacionInclusoAlAdministrador() {
        assertEquals(Outcome.NEEDS_CONFIRMATION, evaluar(true, DeviceType.SMART_OUTLET, true, true, true, false));
        assertEquals(Outcome.ALLOWED, evaluar(true, DeviceType.SMART_OUTLET, true, true, true, true));
    }

    @Test
    void laConfirmacionNoLeDaPermisoAUnMiembro() {
        assertEquals(Outcome.FORBIDDEN, evaluar(false, DeviceType.SMART_OUTLET, true, true, true, true));
    }

    @Test
    void encenderUnCriticoNoNecesitaConfirmacion() {
        assertEquals(Outcome.ALLOWED, evaluar(true, DeviceType.SMART_OUTLET, true, true, false, false));
    }
}
