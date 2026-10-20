# Dispositivos por WiFi con Tuya / Smart Life

HomeIA controla enchufes, interruptores y bombillos **Tuya / Smart Life** a través de la **nube de Tuya**
(Tuya IoT Platform), igual que la app Smart Life. Los dispositivos siguen conectados a tu WiFi de siempre:
no hay que flashearlos ni instalar un broker.

```
Dispositivo ──WiFi──► Nube de Tuya ◄──HTTPS firmado── Backend de HomeIA ◄── App web
```

## 1. Crear el proyecto en Tuya (una sola vez)

1. Crea una cuenta en **https://platform.tuya.com** (puede ser la misma de la app Smart Life).
2. Ve a **Cloud → Development → Create Cloud Project**:
   - *Development Method*: **Smart Home**.
   - *Data Center*: el de tu cuenta de la app. Para Colombia es **Western America Data Center**.
     Si eliges otro, el proyecto no verá tus dispositivos.
3. Cuando el asistente lo pida, autoriza el servicio **IoT Core**.
4. En el proyecto: **Devices → Link App Account → Add App Account**. Abre la app **Smart Life**,
   toca el ícono de escanear (pestaña *Yo*) y escanea el código QR. Confirma en el teléfono.
   En la pestaña *All Devices* del proyecto deberían aparecer tus dispositivos.
5. En la pestaña **Overview** copia el **Access ID/Client ID** y el **Access Secret/Client Secret**.

> El plan gratuito de IoT Core tiene un límite de llamadas a la API y puede requerir renovación desde la
> consola. Revisa tu cuota y su vigencia en la sección *Cloud Services* de la plataforma.

## 2. Configurar el backend

En el `.env` de la raíz del proyecto:

```properties
TUYA_ACCESS_ID=tu_access_id
TUYA_ACCESS_SECRET=tu_access_secret
# Centro de datos: us (Western America), us-e (Eastern America), eu, eu-w, cn, in
TUYA_REGION=us
# Cada cuántos segundos se lee el estado. Cada lectura gasta llamadas de la cuota.
TUYA_SYNC_SECONDS=60
```

Reinicia el backend.

## 3. Agregar los dispositivos

En la app: **Conexión WiFi**. Verás los dispositivos de tu cuenta Smart Life. Para cada uno elige si es
**luz** o **tomacorriente**, su **habitación** y si es **crítico** (nunca se apaga solo), y pulsa **Agregar**.
A partir de ahí aparece en **Dispositivos** y la automatización lo controla como a cualquier otro.

## Qué lee y controla HomeIA

| Tuya (código) | HomeIA |
|---|---|
| `switch_1`, `switch`, `switch_led` | Encendido / apagado |
| `cur_power` (con su escala, normalmente décimas de vatio) | Consumo actual en W, para consumo, fugas y alertas |
| `bright_value_v2` (10–1000) o `bright_value` (25–255) | Brillo en % |

- El estado se lee cada `TUYA_SYNC_SECONDS`. Un dispositivo que Tuya reporta sin conexión queda *Sin conexión*.
- Al encender o apagar desde HomeIA, Tuya confirma la orden y el estado se actualiza al instante.
- Solo los enchufes con **medidor de energía** reportan `cur_power`; sin él, HomeIA controla el dispositivo
  pero no puede calcular su consumo.

## Problemas comunes

| Mensaje | Causa |
|---|---|
| `Tuya rechazó la firma (1004)` | `TUYA_ACCESS_ID` o `TUYA_ACCESS_SECRET` mal copiados (espacios, comillas). |
| `Tuya negó el permiso (1106)` | La cuenta Smart Life no está vinculada, el centro de datos no coincide o falta autorizar IoT Core. |
| La lista de dispositivos sale vacía | Cuenta no vinculada o `TUYA_REGION` distinto al centro de datos del proyecto. |
| `No se pudo conectar con la nube de Tuya` | Sin internet en el equipo del backend, o la URL de la región es incorrecta. |

## Dispositivos propios por MQTT

Para dispositivos que programes tú (por ejemplo un ESP32), HomeIA sigue aceptando MQTT:
el dispositivo publica su estado en `homeia/devices/{id}/state` y recibe órdenes en `homeia/devices/{id}/command`.
Requiere un broker MQTT y `MQTT_ENABLED=true`.
