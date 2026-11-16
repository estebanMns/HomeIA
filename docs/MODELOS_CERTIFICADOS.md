# Registro de modelos validados

Un modelo solo se marca como **validado** después de probarlo físicamente con la checklist de abajo. Los adaptadores
están implementados según la documentación pública de cada fabricante y probados con respuestas simuladas, pero
**ningún modelo de esta tabla se ha validado todavía con hardware real**.

| Proveedor | Fabricante / modelo | Firmware | Tipo en HomeIA | Capacidades verificadas | Fecha | Responsable | Estado |
|---|---|---|---|---|---|---|---|
| SHELLY | Shelly Plus 1PM | 1.x | Tomacorriente | | | | Pendiente |
| SHELLY | Shelly Plug S (Gen1) | 1.14 | Tomacorriente | | | | Pendiente |
| SHELLY | Shelly Dimmer 2 (Gen1) | | Luz | | | | Pendiente |
| TASMOTA | Sonoff Basic R2 | 14.x | Luz | | | | Pendiente |
| TASMOTA | Athom Plug V2 | 14.x | Tomacorriente | | | | Pendiente |
| WLED | ESP32 + WS2812B | 0.15 | Luz | | | | Pendiente |
| ESPHOME | ESP32 + relé | 2025.x | Luz | | | | Pendiente |
| ZIGBEE | IKEA TRÅDFRI LED1836G9 | | Luz | | | | Pendiente |
| ZIGBEE | Aqara MCCGQ11LM (contacto) | | Sensor | | | | Pendiente |
| ZIGBEE | Sonoff SNZB-02 (temperatura) | | Sensor | | | | Pendiente |
| ZIGBEE (coordinador) | Sonoff ZBDongle-E (ember) | 7.4.x | — | | | | Pendiente |
| ZIGBEE (coordinador) | Sonoff ZBDongle-P (zstack) | | — | | | | Pendiente |

## Checklist por modelo

1. Descubrimiento: aparece como candidato con el proveedor correcto (o se registra por IP/friendly_name).
2. Incorporación: las capacidades guardadas coinciden con lo que el equipo soporta.
3. Órdenes: ON, OFF, TOGGLE (y BRILLO si aplica) con `ack` OK y estado confirmado en menos de 3 s.
4. Cambio manual (botón físico o app del fabricante) reflejado en HomeIA en menos de 60 s.
5. Consumo/telemetría coherente con un medidor de referencia (±5 %).
6. Desconexión: al desenchufarlo, el dispositivo pasa a sin conexión y el historial registra el error; al volver, en línea.
7. Credenciales: con contraseña activada funciona tras cargarla en HomeIA y falla con un mensaje claro sin ella.
8. Reinicio del Edge y del backend: el estado se resincroniza sin intervención.
9. Zigbee: emparejamiento con `permit_join` desde HomeIA, cierre verificado y alerta de unión.
