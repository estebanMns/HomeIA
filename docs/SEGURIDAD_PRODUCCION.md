# Lista de verificación antes de producción

Implementado en el código (verificar la configuración en cada despliegue):

- [ ] `HOMEIA_SECRETS_KEY` definido (`openssl rand -base64 32`) y guardado en un gestor de secretos. Sin él, los
      secretos se cifran con una clave derivada de `SECRET_KEY` y rotar esa clave los dejaría ilegibles.
- [ ] MQTT por TLS mutuo o VPN (docs/SEGURIDAD_MQTT.md); el puerto 1883 no es accesible desde Internet.
- [ ] Usuarios MQTT separados (backend, Edge, Zigbee2MQTT, dispositivos) y sin el usuario compartido heredado.
- [ ] Reloj sincronizado por NTP en backend y Edge (ventana de firma ±120 s).
- [ ] `/actuator/prometheus` accesible solo desde la red de monitorización (requiere autenticación por defecto).
      Alertas sugeridas: `homeia_edges_offline > 0` durante 5 min, `homeia_commands_open` creciente, `homeia_mqtt_connected == 0`.
- [ ] Copias de seguridad diarias (`scripts/backup-db.sh` o el servicio `backup` de compose.yaml) y una restauración probada.
- [ ] Retención revisada: `RETENTION_ENERGY_DAYS` (400), `RETENTION_DEVICE_EVENT_DAYS` (180), `RETENTION_DEVICE_COMMAND_DAYS` (30).
- [ ] Imágenes Docker y dependencias fijadas; revisar avisos (`npm audit`, `pip-audit`, dependabot) antes de actualizar.

Controles implementados:

- Firma HMAC con anti-repetición en todos los mensajes backend ↔ Edge; revocación y rotación de secretos.
- Credenciales de dispositivos cifradas en reposo (AES-256-GCM) y en tránsito al Edge; nunca se devuelven por la API.
- Política de capacidades: cada orden se valida antes de enviarse (backend, Edge y frontend).
- SSRF: solo IPv4 privadas literales (backend y Edge), sin redirecciones, respuestas acotadas a 256 KB.
- `permit_join` limitado a 120 s, auditado, con cierre verificado y alertas.
- Límites de frecuencia: 60 órdenes/min por usuario; 10 búsquedas o emparejamientos/min.
- Los logs no incluyen secretos ni credenciales (solo nombres e ids).
- Mensajes MQTT de más de 256 KB se descartan.

Pendiente (requiere personas, hardware o terceros):

- [ ] Prueba de penetración externa del backend, del broker en la nube y de un Edge instalado.
- [ ] Validación física de modelos (docs/MODELOS_CERTIFICADOS.md).
- [ ] Revisión de privacidad de la telemetría (presencia, contacto) según la normativa aplicable (Ley 1581 en Colombia).
