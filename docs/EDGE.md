# HomeIA Edge

Agente local (Python) que corre en un equipo Linux de la casa (Raspberry Pi, mini PC o servidor) y hace lo que el
backend en la nube no puede: descubrir equipos por mDNS/SSDP, hablar HTTP/API nativa con Shelly, Tasmota, WLED y
ESPHome, y operar Zigbee2MQTT en el broker local.

## Arquitectura

```
Navegador ── HTTPS ──> Backend (Railway) ──┐
                                           │  MQTT firmado (HMAC)   homeia/edge/{edgeId}/{tipo}
                    Broker uplink ─────────┘  (VPN privada o broker en la nube con mTLS)
                         │
                   HomeIA Edge  ── HTTP LAN ──> Shelly / Tasmota / WLED
                         │      ── API 6053 ──> ESPHome
                   Broker LAN  ── Zigbee2MQTT ── coordinador USB ── dispositivos Zigbee
```

- **Identidad por hogar**: cada Edge se enrola en una casa y recibe `HOMEIA_EDGE_ID` + `HOMEIA_EDGE_SECRET`
  (32 bytes aleatorios). El backend guarda el secreto cifrado con `HOMEIA_SECRETS_KEY`.
- **Autenticación mutua**: todos los mensajes, en ambos sentidos, van en un sobre firmado con HMAC-SHA256
  (`EdgeSigner.java` / `security.py`), con marca de tiempo (±120 s) y nonce único. Un Edge no puede suplantar a otro
  ni al backend aunque compartan broker; un mensaje capturado no se puede repetir.
- **Credenciales de dispositivos**: viajan cifradas con AES-256-GCM con una clave derivada del secreto del Edge;
  solo ese Edge puede leerlas.
- **Rotación**: "Rotar secreto" entrega uno nuevo; el anterior sigue valiendo 15 minutos y el backend responde con
  el que el Edge esté usando hasta que se actualice. **Revocar** invalida el Edge de inmediato.

## Mensajes

| Tipo | Sentido | Contenido |
|---|---|---|
| `hello` | Edge → backend | versión, plataforma; el backend responde con `sync` |
| `heartbeat` | Edge → backend | cada 30 s: versión, uptime, cola local, métricas, estado del coordinador Zigbee y `permit_join` |
| `discovery` | Edge → backend | candidatos encontrados en la LAN e inventario de Zigbee2MQTT |
| `ack` | Edge → backend | resultado de una orden: `OK`, `FAILED`, `EXPIRED` o `DUPLICATE` + estado leído |
| `state` | Edge → backend | estado de un dispositivo (sondeo cada 30 s o reporte Zigbee) |
| `event` | Edge → backend | `ZIGBEE_DEVICE_JOINED`, `ZIGBEE_DEVICE_LEFT`, `DEVICE_UNREACHABLE` |
| `command` | backend → Edge | `requestId`, dispositivo, orden, `expiresAt`, credenciales cifradas |
| `sync` | backend → Edge | inventario completo de dispositivos del Edge (tras `hello` y cuando el backend se reconecta) |
| `scan` / `permit_join` | backend → Edge | buscar en la LAN / abrir o cerrar emparejamiento Zigbee |

**Órdenes idempotentes**: el backend guarda cada orden (`device_commands`), la reenvía con el mismo `requestId`
si no llega confirmación en 8 s (máx. 3 intentos) y la marca `EXPIRED` a los 45 s. El Edge recuerda los
`requestId` procesados (SQLite) y a un reintento le repite la respuesta sin volver a ejecutar la orden.

**Cola local**: si se cae el uplink, `ack`, `state`, `event` y `discovery` se guardan en SQLite (hasta 5000) y se
reenvían firmados al reconectar. El inventario también persiste: tras reiniciar, el Edge sigue sondeando.

## Instalación

### Opción A: servicio systemd (Linux, Raspberry Pi OS, Ubuntu, Debian)

```bash
git clone <repo> && cd HomeIA/edge
sudo ./install.sh                 # instala en /opt/homeia-edge, crea el usuario homeia-edge y el servicio
sudo nano /etc/homeia-edge.env    # HOMEIA_EDGE_ID, HOMEIA_EDGE_SECRET, usuario MQTT
sudo systemctl restart homeia-edge
journalctl -u homeia-edge -f
```

Actualizar: `git pull && sudo ./install.sh` (reemplaza el código y conserva la configuración).
Desinstalar: `sudo ./install.sh --uninstall`.

El servicio corre sin privilegios, con el sistema de archivos en solo lectura salvo `/var/lib/homeia-edge`.
El reloj debe estar sincronizado (`timedatectl set-ntp true`): los mensajes firmados caducan a los 2 minutos.

### Opción B: Docker (compose.edge.yaml)

Levanta Mosquitto (usuarios y ACL por componente), Zigbee2MQTT (versión fijada) y el Edge con red del host.

### Plataformas y multicast

| Plataforma | mDNS/SSDP | Notas |
|---|---|---|
| Linux nativo / Raspberry Pi (systemd) | Sí | Recomendado |
| Docker en Linux con `network_mode: host` | Sí | Lo que usa compose.edge.yaml |
| Docker Desktop (macOS/Windows) | **No** | La red del host no es la LAN real: usa la opción A en un equipo Linux |
| Máquina virtual | Solo en modo puente | Con NAT el multicast no llega |

Si el router aísla clientes Wi-Fi (AP isolation) o la red de invitados, el descubrimiento falla: el Edge debe estar
en la misma VLAN que los dispositivos. Siempre se puede corregir la IP desde Dispositivos → Editar.

## Adaptadores

| Proveedor | Protocolo | Soporte |
|---|---|---|
| Shelly Gen1 | HTTP `/relay`, `/light`, `/status`, auth básica | relé, dimmer, potencia |
| Shelly Gen2/Plus/Pro | RPC `Switch.*` / `Light.*`, auth digest | relé, dimmer, potencia, tensión, energía |
| Tasmota | `/cm?cmnd=` con `user`/`password` | Power, Dimmer, ENERGY (Status 8) |
| WLED | `/json/state` | encendido, brillo |
| ESPHome | API nativa (aioesphomeapi), Noise opcional | primera luz o interruptor; sensor de potencia |
| Zigbee | Zigbee2MQTT `/set`, `/get` | según `exposes`: POWER, BRIGHTNESS, sensores |

Añadir un fabricante: implementar `Adapter` en `edge/homeia_edge/adapters/`, registrarlo en `default_adapters()`,
añadir el proveedor a `DeviceProvider.java` y escribir pruebas con respuestas reales del equipo.

## Pruebas

```bash
cd edge && python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest
```

`tests/test_security.py` comparte un vector de firma con `EdgeSignerTest.java`: si el formato cambia en un lado,
fallan ambas suites.
