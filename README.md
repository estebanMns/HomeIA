# HomeIA

Control inteligente de energía del hogar. Detecta habitaciones vacías, apaga luces y tomacorrientes no críticos, alerta consumos excesivos y posibles fugas, y calcula la factura con la tarifa real del recibo. La automatización usa Gemini con reglas de respaldo y guardarraíles en código.

- **Backend:** Spring Boot 4.1 (Java 21), PostgreSQL con Flyway (esquema propio `homeia`) y JWT con refresh tokens.
- **Frontend:** Next.js 16 (App Router) y TypeScript, en `homeIafrontend/`.
- **Dispositivos:** Tuya / Smart Life por la nube ([docs/WIFI_TUYA.md](docs/WIFI_TUYA.md)), o propios por MQTT ([docs/MQTT.md](docs/MQTT.md)).
- **Notificaciones:** WhatsApp Cloud API ([docs/WHATSAPP_ALERTS.md](docs/WHATSAPP_ALERTS.md)) y correo SMTP para recuperar la contraseña.

## Estructura

```
src/main/java/com/homeia/
├── config/        Propiedades, seguridad, conversión de DATABASE_URL
├── domain/        Enums del dominio
├── integration/   Tuya, Gemini, MQTT y WhatsApp
├── persistence/   Entidades y repositorios JPA
├── scheduler/     Ciclos de automatización, sincronización y mantenimiento
├── security/      JWT
├── service/       Lógica de negocio y políticas (contraseñas, límites, alertas)
└── web/           Controladores REST (/api/...)
src/main/resources/db/homeia/migration/   Migraciones Flyway
homeIafrontend/src/                       app/ (páginas), components/, services/api.ts
```

## Ejecutar en local

1. **Backend.** Crea un `.env` en la raíz con al menos `DATABASE_URL`, `SECRET_KEY` (32 bytes o más) y `CORS_ALLOWED_ORIGINS=http://localhost:3000`. Las demás variables están en [docs/DESPLIEGUE.md](docs/DESPLIEGUE.md). Luego:

   ```
   ./mvnw spring-boot:run
   ```

2. **Frontend.** Crea `homeIafrontend/.env.local` con `NEXT_PUBLIC_API_URL=http://localhost:8080/api`. Luego:

   ```
   cd homeIafrontend
   npm install
   npm run dev
   ```

3. **Primera vez.** Abre http://localhost:3000. Si la base está vacía, `/setup` crea la casa y la cuenta de administrador.

## Verificar

```
./mvnw test                                  # backend
cd homeIafrontend && npx tsc --noEmit && npm run lint && npm run build
```

## Rendimiento

Web Workers, patrones de rendering, event loop y métricas (Web Vitals, latencia de la API, long tasks), con resultados medidos: [docs/RENDIMIENTO.md](docs/RENDIMIENTO.md). Se ven en vivo en **Administración → Rendimiento**.

## Desplegar

Frontend en Vercel y backend en Railway: [docs/DESPLIEGUE.md](docs/DESPLIEGUE.md).

## Seguridad

- **Contraseñas:** de 12 a 64 caracteres, con mayúscula, minúscula, número y símbolo. Se rechazan las secuencias, las repeticiones, las contraseñas comunes y las que contienen el nombre o el correo (`service/PasswordPolicy.java`).
- **Límites por IP:** registro, login y recuperación de contraseña. Además, el login se bloquea tras 5 fallos seguidos en la misma cuenta.
- **Permisos:** las acciones de administrador exigen el rol `ADMIN`, y cada usuario solo ve los datos de su casa.
