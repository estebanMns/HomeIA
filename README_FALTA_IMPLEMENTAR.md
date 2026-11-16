# HomeIA — Falta implementar

Esta lista corresponde al trabajo que queda después de las **fases 1, 2 y 3** de la ampliación multimarca. El código de esta entrega incorpora el núcleo de proveedores, Zigbee2MQTT y drivers Wi-Fi básicos Shelly/Tasmota. No se debe interpretar como certificación de compatibilidad universal ni como despliegue listo para producción.

## Fase 4 — HomeIA Edge seguro y operación remota

- [ ] Implementar enrolamiento del Edge por hogar con identidad única, rotación/revocación de credenciales y autenticación mutua.
- [ ] Implementar canal privado Edge ↔ backend (VPN o túnel mTLS) para que un backend en Railway pueda enviar órdenes sin exponer MQTT a Internet.
- [ ] Añadir heartbeat, versión, estado de salud, métricas, reconexión y cola local de órdenes/eventos.
- [ ] Añadir confirmación de ejecución, correlación por `requestId`, expiración, deduplicación y reintentos idempotentes.
- [ ] Implementar sincronización de inventario y estado cuando Edge o backend se reinicien.
- [ ] Añadir instalador/actualizador del agente para Linux, Raspberry Pi y mini PC, con configuración segura.
- [ ] Validar el descubrimiento multicast en cada plataforma; Docker Desktop en macOS/Windows puede requerir ejecutar Edge directamente en el host o usar un equipo Linux de la LAN.

## Fase 5 — Dashboard e integración guiada

- [ ] Crear asistente de configuración para Tuya, MQTT, Zigbee2MQTT, Shelly y Tasmota, con diagnóstico paso a paso.
- [ ] Mostrar estado de Edge, coordinador, broker y cada integración por separado (no inferir que Zigbee está operativo solo porque MQTT conectó).
- [ ] Mostrar capacidades y controles específicos por dispositivo; ocultar acciones no soportadas por el modelo.
- [ ] Permitir editar nombre, habitación, tipo, fabricante/modelo y configuración de integración desde la interfaz.
- [ ] Añadir historial de descubrimiento, importación, desconexión y errores por dispositivo.
- [ ] Incorporar integración nativa ESPHome API; por ahora se detectan anuncios, pero no se incorpora ni controla mediante ese protocolo.
- [ ] Añadir soporte para más fabricantes/protocolos Wi-Fi mediante adaptadores independientes y pruebas de compatibilidad.

## Fase 6 — Pruebas, seguridad y producción

- [ ] Ejecutar `./mvnw test`, `npx tsc --noEmit`, lint y build en un entorno con Maven/Node y resolver cualquier fallo específico del entorno.
- [ ] Crear pruebas de integración MQTT/Flyway y pruebas automatizadas de los drivers Shelly, Tasmota y Zigbee2MQTT.
- [ ] Validar físicamente modelos concretos de bombillos, relés, enchufes, sensores y coordinadores Zigbee; registrar los modelos certificados.
- [ ] Implementar usuarios MQTT separados para backend, Edge, Zigbee2MQTT y dispositivos, con ACL de mínimo privilegio por tópico.
- [ ] Habilitar TLS para MQTT o exigir VPN privada; no publicar el puerto 1883 en Internet.
- [ ] Gestionar secretos por dispositivo (incluyendo HTTP Digest/contraseña Shelly y autenticación Tasmota) mediante almacenamiento cifrado y nunca en metadatos JSON.
- [ ] Restringir y auditar el modo `permit_join`; añadir alertas de emparejamiento y cierre automático comprobado.
- [ ] Añadir política de capacidades por dispositivo y validar cada comando antes de enviarlo (por ejemplo, brillo solo en luces regulables).
- [ ] Añadir lectura/confirmación de estado para Shelly y Tasmota, y normalización de sensores Zigbee (temperatura, humedad, contacto, batería, presencia, energía).
- [ ] Revisar límites de red/SSRF, autenticación, rate limiting, logs sin datos sensibles y pruebas de penetración antes de producción.
- [ ] Fijar versiones de imágenes Docker y dependencias, añadir backups/retención de datos y monitorización operativa.

## Configuración externa necesaria para probar las fases implementadas

1. Un equipo Linux/Raspberry Pi/mini PC conectado a la misma LAN que los dispositivos Wi-Fi.
2. Docker y Docker Compose instalados en ese equipo.
3. Para Zigbee: coordinador USB compatible, puerto serie correcto y modelo Zigbee soportado por Zigbee2MQTT.
4. Variables locales `MQTT_USERNAME`, `MQTT_PASSWORD` y `ZIGBEE_SERIAL_DEVICE`; copiar `data_zigbee_configuration.yaml` a `data/zigbee2mqtt/configuration.yaml` y reemplazar los valores de ejemplo.
5. Backend conectado al broker local mediante `MQTT_ENABLED=true`, `MQTT_BROKER_URL` y credenciales coincidentes.
6. Si el backend corre en Railway, establecer primero VPN/túnel privado con la LAN. No abrir el broker MQTT a Internet.

## Estado de validación de esta entrega

- El código fuente se modificó y se revisaron estáticamente los archivos principales.
- Se debe ejecutar `python3 -m py_compile edge/homeia_edge/__main__.py` para validar sintaxis del agente.
- No se declara compilación Maven, build de Next.js ni pruebas con hardware: el entorno de trabajo usado para preparar el paquete no dispone de Maven instalado ni del archivo `.mvn/wrapper/maven-wrapper.properties` que necesita el wrapper del proyecto.
