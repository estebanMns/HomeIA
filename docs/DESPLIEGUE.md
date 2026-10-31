# Despliegue: Vercel + Railway + AWS RDS

- **Frontend:** Vercel, desde la carpeta `homeIafrontend`.
- **Backend:** Railway, con el `Dockerfile` de la raíz y `railway.json`.
- **Base de datos:** PostgreSQL en AWS RDS. Las tablas se crean solas con Flyway en el esquema `homeia`.

Los dos servicios despliegan automáticamente cada cambio que llega a `main`.

## 1. Base de datos (AWS RDS)

1. **Acceso de red:** Railway no tiene IP fija en el plan básico. En el grupo de seguridad del RDS, abre la entrada TCP 5432 a `0.0.0.0/0`. La conexión exige SSL (`sslmode=require`), así que la contraseña debe ser larga y aleatoria.
2. **URL de conexión:** `DATABASE_URL` tiene el formato `postgresql://usuario:contraseña@host:5432/base`. Si la contraseña tiene símbolos como `@ : / ? # %`, escríbelos codificados (por ejemplo `@` → `%40`).

## 2. Backend en Railway

1. **Crear el proyecto:** New Project → Deploy from GitHub repo → este repositorio, rama `main`. Railway detecta `railway.json`, construye con el `Dockerfile` y revisa `/actuator/health`.
2. **Variables** (Variables → Raw Editor), sin comillas:

   | Variable | Valor |
   |---|---|
   | `DATABASE_URL` | URL del RDS (paso 1) |
   | `SECRET_KEY` | 64 caracteres aleatorios, distinta a la de desarrollo |
   | `ACCESS_TOKEN_EXPIRE_MINUTES` | p. ej. `15` |
   | `REFRESH_TOKEN_EXPIRE_MINUTES` | p. ej. `10080` (7 días) |
   | `CORS_ALLOWED_ORIGINS` | `https://<tu-app>.vercel.app` (después del paso 3) |
   | `FRONTEND_URL` | `https://<tu-app>.vercel.app`, para los enlaces de recuperación de contraseña |
   | `GEMINI_API_KEY` | clave de Google AI Studio |
   | `TUYA_ACCESS_ID`, `TUYA_ACCESS_SECRET`, `TUYA_REGION` | ver [WIFI_TUYA.md](WIFI_TUYA.md) |
   | `MQTT_ENABLED` | `false` (solo Tuya; ver [MQTT.md](MQTT.md) para dispositivos propios) |
   | `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | opcional: correo de recuperación de contraseña |
   | `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ALERT_TEMPLATE` | opcional: ver [WHATSAPP_ALERTS.md](WHATSAPP_ALERTS.md) |

   No hay que definir `PORT`: Railway lo pone solo.
3. **Dominio público:** Settings → Networking → Generate Domain.
4. **Comprobación:** `https://<dominio-railway>/actuator/health` debe responder `{"status":"UP"}`.

## 3. Frontend en Vercel

1. **Crear el proyecto:** Add New Project → importar este repositorio.
2. **Root Directory:** `homeIafrontend`. Vercel detecta Next.js solo.
3. **Variable de entorno:** `NEXT_PUBLIC_API_URL=https://<dominio-railway>/api`.
4. **Deploy.**
5. **Volver a Railway:** pon el dominio de Vercel en `CORS_ALLOWED_ORIGINS` y en `FRONTEND_URL`. Railway redespliega solo.

## 4. Comprobar que todo funciona

- **Login:** `https://<tu-app>.vercel.app` muestra el login y permite ingresar.
- **Conexión:** la barra superior dice **Datos en vivo**.
- **Estado del sistema:** base de datos `UP`, Gemini `CONFIGURED` y MQTT `DISABLED` (esperado con solo Tuya).

## Problemas comunes

| Síntoma | Causa |
|---|---|
| "No se pudo conectar con el backend" en el login | `CORS_ALLOWED_ORIGINS` no tiene el dominio exacto de Vercel, o `NEXT_PUBLIC_API_URL` está mal. Si cambias esta variable, vuelve a desplegar en Vercel, porque se fija al compilar. |
| Railway marca el despliegue como fallido | Revisa los logs: casi siempre es la `DATABASE_URL` o que el RDS no acepta la conexión (paso 1). |
| `SECRET_KEY debe tener al menos 32 bytes` | Falta `SECRET_KEY` o es muy corta. |
