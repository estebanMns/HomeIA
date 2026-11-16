#!/bin/sh
# Genera /mosquitto/data/passwd con un usuario por componente a partir de variables de entorno.
# Se ejecuta en cada arranque del contenedor: cambiar una contraseña = editar .env y reiniciar.
set -eu
PASSWD=/mosquitto/data/passwd
TMP="$PASSWD.tmp"
: > "$TMP"
add() { # usuario contraseña
  if [ -n "$1" ] && [ -n "$2" ]; then mosquitto_passwd -b "$TMP" "$1" "$2"; fi
}
add "${MQTT_BACKEND_USERNAME:-homeia-backend}" "${MQTT_BACKEND_PASSWORD:-}"
add zigbee2mqtt "${MQTT_ZIGBEE_PASSWORD:-}"
add "${HOMEIA_EDGE_ID:-}" "${MQTT_EDGE_PASSWORD:-}"
# Usuario heredado (compatibilidad con instalaciones anteriores). Dejar vacío en producción.
add "${MQTT_USERNAME:-}" "${MQTT_PASSWORD:-}"
# Dispositivos propios: MQTT_DEVICE_USERS="<uuid>:clave <uuid2>:clave2" (usuario = id del dispositivo en HomeIA)
for pair in ${MQTT_DEVICE_USERS:-}; do add "${pair%%:*}" "${pair#*:}"; done
[ -s "$TMP" ] || { echo "No se definió ningún usuario MQTT" >&2; exit 1; }
mv "$TMP" "$PASSWD"
chown mosquitto:mosquitto "$PASSWD"
chmod 0700 "$PASSWD"

# ACL efectiva: reglas fijas + acceso del Edge de esta casa a Zigbee2MQTT local.
ACL=/mosquitto/data/acl
cp /mosquitto/config/acl "$ACL.tmp"
if [ -n "${HOMEIA_EDGE_ID:-}" ]; then
  printf '\nuser %s\ntopic readwrite zigbee2mqtt/#\ntopic readwrite homeia/edge/%s/#\n' "$HOMEIA_EDGE_ID" "$HOMEIA_EDGE_ID" >> "$ACL.tmp"
fi
# Usuario compartido heredado: mismo acceso que antes de las ACL (solo para migrar; no usar en producción).
if [ -n "${MQTT_USERNAME:-}" ] && [ "${MQTT_USERNAME}" != "${MQTT_BACKEND_USERNAME:-homeia-backend}" ]; then
  echo "AVISO: el usuario MQTT compartido '${MQTT_USERNAME}' tiene acceso amplio; migra a usuarios por componente." >&2
  printf '\nuser %s\ntopic readwrite homeia/#\ntopic readwrite zigbee2mqtt/#\n' "$MQTT_USERNAME" >> "$ACL.tmp"
fi
mv "$ACL.tmp" "$ACL"
chown mosquitto:mosquitto "$ACL"
chmod 0700 "$ACL"
exec mosquitto -c /mosquitto/config/mosquitto.conf
