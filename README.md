# HomeIA - Sistema Inteligente de Control del Hogar

Sistema completo de automatización y control del hogar con IoT, basado en arquitectura de microservicios con Spring Boot y Next.js.

## 📁 Estructura del Proyecto

```
HomeIA/
├── src/                          # Backend (Spring Boot)
│   ├── main/java/com/home/ia/   # Código Java
│   └── main/resources/          # Configuración
├── homeIafrontend/              # Frontend (Next.js + TypeScript + Tailwind)
│   ├── src/
│   │   ├── app/                 # Páginas y rutas
│   │   ├── components/          # Componentes React
│   │   ├── contexts/            # Context API (Autenticación)
│   │   ├── services/            # Servicios API
│   │   ├── types/               # Tipos TypeScript
│   │   └── utils/               # Utilidades
│   └── package.json
├── pom.xml                       # Dependencias Backend
├── start-dev.sh                  # Script para iniciar todo
└── README.md                     # Este archivo
```

## 🚀 Inicio Rápido

### Opción 1: Script Automatizado (Recomendado)

```bash
cd /Users/estebanmeneses/Desktop/HomeIA
./start-dev.sh
```

### Opción 2: Manual

**Terminal 1 - Backend:**
```bash
cd /Users/estebanmeneses/Desktop/HomeIA
mvn spring-boot:run
```

**Terminal 2 - Frontend:**
```bash
cd /Users/estebanmeneses/Desktop/HomeIA/homeIafrontend
npm run dev
```

## 🌐 Acceso

| Componente | URL | Descripción |
|-----------|-----|-------------|
| Frontend | http://localhost:3000 | Interfaz de usuario |
| Backend | http://localhost:8080/api | API REST |
| Swagger UI | http://localhost:8080/swagger-ui.html | Documentación API |
| Prometheus | http://localhost:8080/actuator/prometheus | Métricas |

## 🔐 Credenciales Demo

```
Email:    andrea@homeia.co
Password: HomeIA2025
```

## 🛠️ Tecnologías

### Backend
- **Spring Boot 4.1.1** con Java 21
- **JWT** para autenticación (HS256)
- **Refresh Tokens** (7 días, máx 5 por usuario)
- **MQTT** (Eclipse Paho) para IoT
- **WebSocket/STOMP** para notificaciones en tiempo real
- **Prometheus/Micrometer** para métricas
- **H2 Database** (desarrollo) / PostgreSQL (producción)
- **Flyway** para migraciones

### Frontend
- **Next.js 15** (App Router)
- **TypeScript** para tipado estático
- **Tailwind CSS** para estilos
- **React Context** para estado global
- **Responsive Design**

## 📋 Endpoints Principales

### Autenticación
- `POST /api/auth/login` - Login
- `POST /api/auth/refresh` - Refrescar token
- `POST /api/auth/logout` - Logout
- `GET /api/auth/validate` - Validar token

### Dispositivos
- `GET /api/devices` - Listar dispositivos
- `GET /api/devices/{id}` - Obtener dispositivo
- `POST /api/devices/{id}/toggle` - Toggle dispositivo
- `POST /api/devices/{id}/turn-on` - Encender
- `POST /api/devices/{id}/turn-off` - Apagar

### Alertas
- `GET /api/alerts` - Listar alertas
- `POST /api/alerts/{id}/acknowledge` - Confirmar alerta
- `POST /api/alerts/{id}/retry` - Reintentar

### Métricas
- `GET /api/metrics/summary` - Resumen de métricas
- `GET /api/metrics/health` - Estado del sistema
- `GET /api/metrics/prometheus-info` - Info Prometheus

## 🔄 Autenticación con JWT

1. Login retorna `accessToken` (15 min) y `refreshToken` (7 días)
2. Frontend almacena tokens en localStorage
3. Cada request incluye `Authorization: Bearer <token>`
4. Cuando expira, se usa refreshToken automáticamente
5. Logout revoca todos los refreshTokens

## 🌍 Características Principales

- ✅ Autenticación segura con JWT
- ✅ Refresh Tokens con rotación automática
- ✅ Control de dispositivos IoT vía MQTT
- ✅ Notificaciones en tiempo real (WebSocket)
- ✅ Audit logging completo
- ✅ Métricas de Prometheus
- ✅ Documentación API Swagger
- ✅ Interfaz responsive
- ✅ Soporte multi-dispositivo

## 📊 Auditoría

Todas las operaciones se registran en la tabla `domain_events` con:
- Timestamp
- Usuario
- Tipo de evento
- Detalles JSON
- IP y User-Agent

## 🔧 Configuración

### Backend (application.properties)
```properties
# BD
spring.datasource.url=jdbc:h2:mem:homeIA (desarrollo)
spring.jpa.hibernate.ddl-auto=create-drop

# MQTT (opcional)
mqtt.enabled=false
mqtt.broker-url=tcp://localhost:1883

# WebSocket
spring.websocket.enabled=true
```

### Frontend (.env.local)
```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
```

## 📝 Variables de Entorno

### Backend
- `GEMINI_API_KEY` (opcional) - Para funcionalidad IA

### Frontend
- `NEXT_PUBLIC_API_URL` - URL del backend
- `NEXT_PUBLIC_WS_URL` - URL WebSocket

## 🧪 Testing

Backend:
```bash
mvn test
```

Frontend:
```bash
cd homeIafrontend
npm test
```

## 📦 Build para Producción

Backend:
```bash
mvn clean package
java -jar target/ia-0.0.1-SNAPSHOT.jar
```

Frontend:
```bash
cd homeIafrontend
npm run build
npm start
```

## 🐛 Troubleshooting

### Backend no inicia
- Verificar que puerto 8080 está disponible
- Ver logs: `tail -f /tmp/homeIA-backend.log`
- Compilar: `mvn clean compile`

### Frontend no conecta con Backend
- Verificar `NEXT_PUBLIC_API_URL` en `.env.local`
- Backend debe estar ejecutándose
- Ver logs: `tail -f /tmp/homeIA-frontend.log`

### Tokens expirados
- Frontend maneja refresh automático
- Si falla, dirigue a login
- Credenciales demo siempre disponibles

## 🚀 Deployment

### Docker (próximo)
Se planea crear Dockerfile para backend y frontend.

### Kubernetes (próximo)
Se planea crear manifiestos k8s.

## 📚 Documentación Adicional

- [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) - Resumen técnico
- [REFRESH_TOKENS_GUIDE.md](./REFRESH_TOKENS_GUIDE.md) - Guía de tokens
- [HELP.md](./HELP.md) - Ayuda adicional

## 👨‍💻 Desarrollo

### Clonar proyecto
```bash
git clone https://github.com/estebanMns/HomeIA.git
cd HomeIA
```

### Instalar dependencias
Backend: se descarga automáticamente con Maven
```bash
cd homeIafrontend
npm install
```

### Ejecutar en desarrollo
```bash
./start-dev.sh
```

## 📄 Licencia

Proyecto educativo - HomeIA 2026

## 📞 Soporte

Para problemas o sugerencias, crear issue en GitHub.

---

**Última actualización:** 2026-10-08  
**Versión:** 0.0.1-SNAPSHOT  
**Estado:** ✅ Producción-Ready