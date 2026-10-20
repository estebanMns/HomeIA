package com.homeia.scheduler;

import com.homeia.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lee periódicamente el estado de los dispositivos Tuya (encendido, consumo, brillo y conexión),
 * que alimenta el dashboard, las alertas y las decisiones de la IA. El intervalo es TUYA_SYNC_SECONDS.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TuyaSyncScheduler {

    private final DeviceService deviceService;

    @Scheduled(fixedDelayString = "${homeia.tuya.sync-seconds:60}000",
            initialDelayString = "${homeia.tuya.sync-seconds:60}000")
    public void sync() {
        try {
            int updated = deviceService.syncTuyaDevices();
            if (updated > 0) {
                log.debug("Tuya: {} dispositivos actualizados", updated);
            }
        } catch (Exception e) {
            log.warn("No se pudo sincronizar con Tuya: {}", e.getMessage());
        }
    }
}
