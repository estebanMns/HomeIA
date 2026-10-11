package com.homeia.integration;

import java.util.UUID;

/** Estado reportado por un dispositivo a través del broker MQTT. */
public record DeviceReportEvent(UUID deviceId, String payload) {
}
