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

---

## 🔔 WebSocket - Notificaciones en Tiempo Real

### Configuración

#### **WebSocketConfig** (`infrastructure/websocket/WebSocketConfig.java`)
- Configuración de STOMP (Simple Text Oriented Messaging Protocol)
- Habilita Simple Message Broker
- 3 endpoints STOMP:
  - `/ws/notifications` - Notificaciones generales
  - `/ws/devices` - Eventos de dispositivos
  - `/ws/mqtt` - Eventos MQTT

#### **WebSocketService** (`application/service/WebSocketService.java`)
Métodos para enviar notificaciones:
- `notifyDeviceStatusChanged()` - Cambios de estado de dispositivos
- `notifyMqttMessage()` - Mensajes MQTT recibidos
- `notifyDeviceCommand()` - Comandos enviados a dispositivos
- `notifyAlert()` - Alertas generadas
- `notifyAutomationEvent()` - Eventos de automatización
- `notifyConnectionStatus()` - Estado de conexiones
- `broadcastToAll()` - Broadcast a todos los clientes

#### **NotificationController** (`adapter/rest/controller/NotificationController.java`)
- **STOMP Message Mappings:**
  - `/app/notification/subscribe` → `/topic/notifications`
  - `/app/device/subscribe` → `/topic/devices`
  - `/app/mqtt/subscribe` → `/topic/mqtt/status`
  - `/app/ping` → `/topic/pong`

- **REST Endpoints:**
  - `GET /api/ws/status` - Verificar conexión WebSocket
  - `GET /api/ws/test-notification` - Enviar notificación de prueba
  - `GET /api/ws/test-alert` - Enviar alerta de prueba

### Tópicos WebSocket

```
/topic/devices/{deviceId}     - Eventos específicos de dispositivo
/topic/devices                - Todos los eventos de dispositivos
/topic/alerts                 - Alertas
/queue/alerts                 - Alertas privadas
/topic/mqtt/messages          - Mensajes MQTT
/topic/commands               - Comandos de dispositivos
/topic/automation             - Eventos de automatización
/topic/connection-status      - Estado de conexiones
/topic/notifications          - Notificaciones generales
/topic/pong                   - Respuesta a ping
```

### Cliente WebSocket (JavaScript Ejemplo)

```javascript
// Conectar
const stompClient = new StompJs.Client({
    brokerURL: 'ws://localhost:8080/ws/devices',
    headers: {
        'Authorization': 'Bearer ' + jwtToken
    }
});

// Suscribirse
stompClient.onConnect = function() {
    stompClient.subscribe('/topic/devices/device-001', function(message) {
        console.log('Device update:', JSON.parse(message.body));
    });
};

stompClient.activate();
```

---

## 📊 Prometheus Metrics

### Configuración

**application.properties:**
```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
management.metrics.enable.all=true
```

### Métricas Disponibles

#### Counters (Contadores)

| Métrica | Descripción |
|---------|-------------|
| `auth.login.success` | Logins exitosos |
| `auth.login.failure` | Intentos de login fallidos |
| `device.commands.total` | Total de comandos a dispositivos |
| `mqtt.publish.total` | Mensajes publicados en MQTT |
| `mqtt.subscribe.total` | Suscripciones a MQTT |
| `alerts.total` | Total de alertas generadas |
| `automation.actions.total` | Acciones de automatización ejecutadas |
| `websocket.messages.total` | Mensajes enviados via WebSocket |

#### Gauges (Medidores - Valores Actuales)

| Métrica | Descripción |
|---------|-------------|
| `devices.active` | Dispositivos activos en este momento |
| `mqtt.connections.active` | Conexiones MQTT activas |
| `websocket.connections.active` | Conexiones WebSocket activas |

#### Timers (Duraciones en ms)

| Métrica | Descripción |
|---------|-------------|
| `device.command.duration` | Duración de comandos a dispositivos |
| `mqtt.command.duration` | Duración de comandos MQTT |
| `auth.duration` | Duración del proceso de autenticación |

### Endpoints de Métricas

**Personalizados:**
- `GET /api/metrics/summary` - Resumen de métricas activas
- `GET /api/metrics/devices` - Métricas de dispositivos
- `GET /api/metrics/mqtt` - Métricas de MQTT
- `GET /api/metrics/websocket` - Métricas de WebSocket
- `GET /api/metrics/health` - Health check
- `GET /api/metrics/prometheus-info` - Información de Prometheus

**Estándar de Prometheus:**
- `GET /actuator/prometheus` - Scrape para Prometheus (formato Prometheus)
- `GET /actuator/health` - Health endpoint

### Configuración de Prometheus

**prometheus.yml:**
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'homeIA'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
```

### Grafana Queries (Ejemplos)

```promql
# Total de logins exitosos
rate(auth.login.success[5m])

# Dispositivos activos actualmente
devices.active

# Promedio de duración de comandos MQTT (últimas 5 min)
rate(mqtt.command.duration_sum[5m]) / rate(mqtt.command.duration_count[5m])

# Conexiones WebSocket activas
websocket.connections.active

# Intentos de login fallidos por minuto
rate(auth.login.failure[1m])
```

---

## 🌐 Integración Completa

### Flujo de Eventos Completo

```
1. Usuario -> WebSocket /ws/devices
   ↓
2. NotificationController recibe suscripción
   ↓
3. Usuario controla dispositivo via REST
   ↓
4. DeviceCommandController procesa comando
   ↓
5. MqttService publica en MQTT + Auditoría
   ↓
6. WebSocketService envía notificación
   ↓
7. Cliente WebSocket recibe actualización en tiempo real
   ↓
8. MetricsService registra métricas
   ↓
9. Prometheus scrape recibe datos para dashboard
```

---

## 📈 Dashboards Recomendados (Grafana)

### Dashboard 1: Overview
- Dispositivos activos (Gauge)
- Conexiones MQTT (Gauge)
- Conexiones WebSocket (Gauge)
- Logins/min (Graph)
- Alertas/min (Graph)

### Dashboard 2: Performance
- Device command duration p95 (Graph)
- MQTT command latency (Graph)
- Auth duration p99 (Graph)
- Commands per second (Rate)

### Dashboard 3: Activity
- Logins éxito vs fracaso (Stacked Bar)
- Comandos por tipo (Pie)
- Automatizaciones ejecutadas (Counter)
- WebSocket messages/sec (Rate)

---

## ✅ Resumen Final de Implementación

| Componente | Status | Endpoints | Métricas |
|-----------|--------|-----------|----------|
| REST API | ✅ | 20+ endpoints | device.commands.total |
| JWT Auth | ✅ | /api/auth/** | auth.login.* |
| MQTT | ✅ | /api/devices/mqtt/** | mqtt.* |
| WebSocket | ✅ | /ws/* | websocket.* |
| Prometheus | ✅ | /actuator/prometheus | 14 métricas custom |
| Auditoría | ✅ | domain_events table | Todos los eventos |
| Logs | ✅ | SLF4J | Level DEBUG/INFO/WARN |

---

## 🚀 Próximos Pasos (Opcionales)

1. **Rate Limiting:** Proteger endpoints contra abuso
2. **Refresh Tokens:** Mejorar seguridad de JWT
3. **Database Migrations:** Poblar usuarios reales
4. **Email Notifications:** Alertas por correo
5. **Push Notifications:** Notificaciones mobile
6. **API Gateway:** Kong o Nginx para balance
7. **Container Orchestration:** Kubernetes deployment
8. **CD/CI Pipeline:** GitHub Actions o Jenkins

---

**Última Actualización:** 2026-10-08
**Generado con:** Claude Haiku 4.5
