# HomeIA - Implementación Completa

## 📋 Resumen General

Se ha completado la implementación de:
1. ✅ REST API completa con logs y auditoría
2. ✅ Autenticación JWT con protección de endpoints
3. ✅ MQTT para comunicación con dispositivos

---

## 🔐 Autenticación JWT

### Componentes Implementados

#### 1. **JwtUtil** (`infrastructure/security/JwtUtil.java`)
- Generador de tokens JWT con firma HS256
- Métodos:
  - `generateToken(userId, email)`: Crea token con expiración de 24h
  - `validateToken(token)`: Valida firma y no-expiración
  - `extractUserId(token)`: Extrae userId del token
  - `extractEmail(token)`: Extrae email del token
  - `isTokenExpired(token)`: Verifica si está expirado

#### 2. **AuthController** (`adapter/rest/controller/AuthController.java`)
- **POST /api/auth/login**
  - Body: `{ "email": "string", "password": "string", "ipAddress": "string" }`
  - Response: `{ "token": "jwt", "userId": "string", "email": "string", "expiresIn": 86400 }`
  - Usuario demo: `andrea@homeia.co` / `HomeIA2025`
  - Auditoría: LOGIN_SUCCESS / LOGIN_FAILED

- **POST /api/auth/logout**
  - Header: `Authorization: Bearer <token>`
  - Auditoría: LOGOUT

- **POST /api/auth/validate**
  - Header: `Authorization: Bearer <token>`
  - Response: `{ "valid": boolean, "userId": "string", "email": "string", "expired": boolean }`

#### 3. **JwtAuthenticationFilter** (`infrastructure/security/JwtAuthenticationFilter.java`)
- Filtro que valida JWT en cada request
- Retorna 401 Unauthorized si:
  - Falta el header Authorization
  - Token es inválido o expirado
- Excluye: `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/h2-console/**`
- Establece Spring Security context con userId

#### 4. **SecurityConfig** (`infrastructure/security/SecurityConfig.java`)
- CSRF deshabilitado
- Session stateless (solo JWT)
- Endpoints públicos: `/api/auth/**`, swagger, h2-console
- Resto requiere autenticación
- BCryptPasswordEncoder

### Protección de Endpoints

✅ **Todos los endpoints requieren JWT válido excepto:**
- `/api/auth/**` - Autenticación pública
- `/swagger-ui/**` - Documentación
- `/v3/api-docs/**` - OpenAPI
- `/h2-console/**` - Base de datos

---

## 📡 REST API

### DeviceController (`adapter/rest/controller/DeviceController.java`)
**Base:** `/api/devices`

- **GET /api/devices** - Listar todos los dispositivos
- **GET /api/devices/{id}** - Obtener dispositivo por ID
- **GET /api/devices/room/{roomId}** - Listar dispositivos por habitación
- **POST /api/devices/{id}/toggle** - Toggle estado del dispositivo
- **POST /api/devices/{id}/turn-on** - Encender dispositivo
- **POST /api/devices/{id}/turn-off** - Apagar dispositivo

**Auditoría:** Cada acción registra:
- userId, deviceId, deviceName
- Action: TOGGLE, TURN_ON, TURN_OFF, etc.
- Timestamp en domain_events table

### AlertController (`adapter/rest/controller/AlertController.java`)
**Base:** `/api/alerts`

- **GET /api/alerts** - Listar alertas (con filtros severity, status)
- **GET /api/alerts/{id}** - Obtener detalle de alerta
- **POST /api/alerts/{id}/acknowledge** - Marcar como revisada
- **POST /api/alerts/{id}/retry-whatsapp** - Reintentar notificación WhatsApp

**Auditoría:** Cada acción registra evento ALERT_ACTION

### AutomationRestController (`adapter/rest/controller/AutomationRestController.java`)
**Base:** `/api/automation`

- **GET /api/automation/status** - Estado actual de automatización
- **PUT /api/automation/settings** - Actualizar configuración
- **POST /api/automation/enable** - Habilitar automatización
- **POST /api/automation/disable** - Deshabilitar automatización
- **GET /api/automation/actions** - Historial de acciones

**Auditoría:** Cada acción registra evento AUTOMATION_ACTION

---

## 🌐 MQTT - Comunicación con Dispositivos

### Configuración (`application.properties`)
```properties
mqtt.enabled=true                          # Habilitar MQTT
mqtt.broker-url=tcp://localhost:1883      # URL del broker (ej: Mosquitto)
mqtt.username=                            # Opcional: usuario
mqtt.password=                            # Opcional: contraseña
mqtt.client-id=homeIA-client             # ID del cliente
```

### Componentes Implementados

#### 1. **MqttConfig** (`infrastructure/mqtt/MqttConfig.java`)
- Configuración automática de MQTT (si `mqtt.enabled=true`)
- Lee propiedades de `application.properties`
- Bean `MqttAdapter` se crea al iniciar la aplicación

#### 2. **MqttAdapter** (`infrastructure/mqtt/MqttAdapter.java`)
- Cliente MQTT usando Eclipse Paho
- Métodos:
  - `connectToBroker()`: Conecta al broker MQTT
  - `publishDeviceCommand(deviceId, command, payload)`: Envía comando
  - `publishStatusRequest(deviceId)`: Solicita status a dispositivo
  - `disconnect()`: Desconecta del broker
  - `isConnected()`: Verifica conexión

- **Reconexión automática:** Si se desconecta, intenta reconectar
- **Topics MQTT:**
  - Publish: `devices/{deviceId}/command` (comandos)
  - Publish: `devices/{deviceId}/request-status` (status request)
  - Subscribe: `devices/+/status`, `devices/+/metrics`, `devices/+/error`

#### 3. **MqttService** (`application/service/MqttService.java`)
- Capa de negocio para MQTT
- Métodos:
  - `toggleDevice(userId, deviceId, deviceName)`
  - `turnOnDevice(userId, deviceId, deviceName)`
  - `turnOffDevice(userId, deviceId, deviceName)`
  - `requestDeviceStatus(userId, deviceId, deviceName)`
  - `isConnected()`: Verifica conexión
  - `disconnect()`: Desconecta

- **Auditoría integrada:** Cada operación registra:
  - TOGGLE_VIA_MQTT / TURN_ON_VIA_MQTT / TURN_OFF_VIA_MQTT
  - STATUS_REQUEST_VIA_MQTT
  - *_ERROR si hay problemas

#### 4. **DeviceCommandController** (`adapter/rest/controller/DeviceCommandController.java`)
**Base:** `/api/devices/mqtt`

- **POST /api/devices/mqtt/{deviceId}/toggle** - Toggle via MQTT
- **POST /api/devices/mqtt/{deviceId}/on** - Encender via MQTT
- **POST /api/devices/mqtt/{deviceId}/off** - Apagar via MQTT
- **GET /api/devices/mqtt/{deviceId}/status** - Solicitar status
- **GET /api/devices/mqtt/mqtt/status** - Verificar conexión MQTT

**Respuestas:**
- 202 Accepted: Comando aceptado (procesamiento asíncronos)
- 500 Internal Server Error: Si hay error en MQTT

### Flujo de Comunicación MQTT

```
Frontend -> API JWT
  ↓
DeviceCommandController (requiere JWT)
  ↓
MqttService (auditoría)
  ↓
MqttAdapter (publica en MQTT)
  ↓
Broker MQTT
  ↓
Dispositivo IoT
```

---

## 📊 Auditoría - AuditService

Todos los eventos se registran en la tabla `domain_events` con:

**Campos:**
- `id`: UUID único
- `eventType`: Tipo de evento (DEVICE_ACTION, ALERT_ACTION, AUTOMATION_ACTION, AUTH_ACTION)
- `deviceId`: ID del dispositivo (si aplica)
- `roomId`: ID de la habitación (si aplica)
- `payload`: JSON con detalles completos
- `occurredAt`: Timestamp del evento
- `createdAt`: Timestamp de registro

**Métodos de AuditService:**
1. `logDeviceAction()` - Acciones en dispositivos
2. `logAlertAction()` - Acciones en alertas
3. `logAutomationAction()` - Acciones en automatización
4. `logAuthAction()` - Acciones de autenticación

---

## 🔄 Flujo de Seguridad Completo

```
1. Usuario -> POST /api/auth/login
2. Backend -> Valida credenciales
3. Backend -> Genera JWT (JwtUtil)
4. Backend -> Registra LOGIN_SUCCESS (auditoría)
5. Usuario <- Recibe { token, userId, email }

6. Usuario -> GET /api/devices (header: Authorization: Bearer <token>)
7. JwtAuthenticationFilter -> Valida JWT
8. JwtAuthenticationFilter -> Establece Spring Security context
9. DeviceController -> Procesa request
10. DeviceController -> Registra acción (auditoría)
11. Usuario <- Recibe respuesta
```

---

## 🚀 Compilación y Despliegue

### Compilar
```bash
mvn clean compile
```

### Ejecutar
```bash
mvn spring-boot:run
```

### Dependencias Agregadas
- `io.jsonwebtoken:jjwt-api:0.12.3` - JWT
- `io.jsonwebtoken:jjwt-impl:0.12.3` - JWT Implementation
- `io.jsonwebtoken:jjwt-jackson:0.12.3` - JSON processing
- `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` - MQTT Client

---

## 📝 Logs y Debugging

### Niveles de Log
- **INFO**: Eventos importantes (login, logout, comandos MQTT)
- **DEBUG**: Información detallada (token validation, device actions)
- **WARN**: Eventos anómalos (token inválido, reconexión MQTT)
- **ERROR**: Errores (autenticación fallida, fallos MQTT)

### Ubicación en código
- Todos los controllers tienen `@Slf4j` de Lombok
- `log.info()`, `log.debug()`, `log.warn()`, `log.error()`

---

## 🔧 Configuración Recomendada para Producción

```properties
# JWT
jwt.secret=<una-clave-muy-larga-y-aleatoria>
jwt.expiration=3600000  # 1 hora en ms

# MQTT
mqtt.enabled=true
mqtt.broker-url=tcp://broker.ejemplo.com:1883
mqtt.username=usuario_real
mqtt.password=contraseña_real
mqtt.client-id=homeIA-prod

# Database
spring.datasource.url=jdbc:postgresql://prod-db:5432/homeIA
spring.datasource.username=prod_user
spring.datasource.password=<contraseña_fuerte>

# Logging
logging.level.com.home.ia=INFO
```

---

## ✅ Estado de Implementación

| Componente | Estado | Commits |
|-----------|--------|---------|
| REST API | ✅ Completo | 39710db, b611e07 |
| JWT Auth | ✅ Completo | 71c9658, c5efa5e |
| MQTT | ✅ Completo | 738eb4f |
| Auditoría | ✅ Integrada | Todos los commits |
| Logs | ✅ Implementados | Todos los commits |

---

## 🎯 Próximos Pasos (Opcionales)

1. **Base de Datos Real:**
   - Implementar UserRepository para almacenar usuarios reales
   - Implementar DeviceRepository para datos reales
   - Bcrypt para almacenar passwords con hash

2. **WebSocket:**
   - Notificaciones en tiempo real de status MQTT
   - Eventos de dispositivos conectados/desconectados

3. **Metricas:**
   - Prometheus metrics para MQTT
   - Dashboard Grafana

4. **Validaciones Avanzadas:**
   - Refresh tokens
   - Rate limiting
   - CORS configuration

---

## 📚 Documentación OpenAPI

Acceder a: `http://localhost:8080/swagger-ui.html`

Todos los endpoints están documentados automáticamente con Springdoc OpenAPI.

---

**Generado con Claude Haiku 4.5 - 2026-10-08**
