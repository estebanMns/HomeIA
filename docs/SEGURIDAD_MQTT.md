# Seguridad MQTT: usuarios, ACL, TLS y canal privado

## Regla principal

**Nunca publiques el puerto 1883 en Internet** (sin reenvío de puertos en el router). El backend en Railway llega a
los Edge por una de estas dos vías:

### Vía 1: broker en la nube con TLS mutuo (recomendada para varias casas)

Los Edge salen hacia el broker (no se abren puertos en las casas). Cada cliente se identifica con un certificado; el
CN es su usuario MQTT y la ACL le limita a su propio árbol de tópicos.

```bash
./docker/mosquitto/pki.sh ca                          # una sola vez; guarda certs/ca.key fuera del servidor
./docker/mosquitto/pki.sh server broker.midominio.com
./docker/mosquitto/pki.sh client homeia-backend
./docker/mosquitto/pki.sh client <HOMEIA_EDGE_ID>     # uno por Edge
docker compose -f compose.broker-cloud.yaml up -d     # en un VPS con el puerto 8883 abierto
```

Backend:

```
MQTT_ENABLED=true
MQTT_BROKER_URL=ssl://broker.midominio.com:8883
MQTT_CA_CERT=/ruta/ca.crt
MQTT_CLIENT_CERT=/ruta/homeia-backend.crt
MQTT_CLIENT_KEY=/ruta/homeia-backend.key      # PKCS#8 (BEGIN PRIVATE KEY), como lo genera pki.sh
MQTT_CLIENT_ID=homeia-backend
```

Edge (`/etc/homeia-edge.env`): `UPLINK_MQTT_HOST`, `UPLINK_MQTT_TLS=true`, `UPLINK_MQTT_CA_CERT`,
`UPLINK_MQTT_CLIENT_CERT`, `UPLINK_MQTT_CLIENT_KEY`. El broker LAN sigue sirviendo a Zigbee2MQTT y a los ESP32.

Revocar el certificado de un Edge: `./docker/mosquitto/pki.sh revoke <HOMEIA_EDGE_ID>` y añadir `crlfile` al
listener. Además, revoca el Edge en HomeIA (su firma HMAC deja de aceptarse aunque conserve el certificado).

### Vía 2: VPN privada (una sola casa)

WireGuard o Tailscale entre el servidor del backend y el equipo del Edge; `MQTT_BROKER_URL=tcp://<IP-VPN>:1883`.
El tráfico va cifrado por la VPN y el broker solo escucha en la LAN/VPN.

Ejemplo WireGuard (en el equipo del Edge, `/etc/wireguard/wg0.conf`):

```
[Interface]
Address = 10.66.0.2/32
PrivateKey = <clave privada del Edge>
[Peer]
PublicKey = <clave pública del servidor>
Endpoint = vpn.midominio.com:51820
AllowedIPs = 10.66.0.1/32
PersistentKeepalive = 25
```

Railway no ofrece interfaces WireGuard en contenedores; en ese caso usa la vía 1 o aloja el backend en un VPS con VPN.

## Usuarios y ACL (broker LAN)

`docker/mosquitto/init-users.sh` genera en cada arranque un usuario por componente:

| Usuario | Variable | Acceso (docker/mosquitto/acl) |
|---|---|---|
| `homeia-backend` | `MQTT_BACKEND_PASSWORD` | `homeia/devices/#`, `homeia/edge/#` |
| `zigbee2mqtt` | `MQTT_ZIGBEE_PASSWORD` | `zigbee2mqtt/#` |
| `<HOMEIA_EDGE_ID>` | `MQTT_EDGE_PASSWORD` | `homeia/edge/<su id>/#`, `zigbee2mqtt/#` |
| `<id del dispositivo>` | `MQTT_DEVICE_USERS="<id>:clave ..."` | escribe `homeia/devices/<id>/state`, lee `.../command` |

El usuario compartido `MQTT_USERNAME`/`MQTT_PASSWORD` se mantiene solo por compatibilidad; elimínalo en producción.

## Emparejamiento Zigbee

- `permit_join: false` en la configuración de Zigbee2MQTT.
- HomeIA lo abre solo a pedido de un administrador, máximo 120 s, con auditoría y alerta.
- El Edge envía el cierre explícito al vencer y el backend verifica en cada heartbeat que siga cerrado; si no, fuerza
  el cierre y genera una alerta. Un dispositivo unido sin autorización vigente produce una alerta crítica.
