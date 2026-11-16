#!/usr/bin/env bash
# Instala o actualiza HomeIA Edge como servicio systemd en Linux (Debian/Ubuntu/Raspberry Pi OS, x86_64 o ARM).
#   sudo ./install.sh            instala o actualiza desde esta carpeta
#   sudo ./install.sh --uninstall
set -euo pipefail

PREFIX=/opt/homeia-edge
ENV_FILE=/etc/homeia-edge.env
SRC="$(cd "$(dirname "$0")" && pwd)"

if [[ $EUID -ne 0 ]]; then echo "Ejecuta con sudo" >&2; exit 1; fi

if [[ "${1:-}" == "--uninstall" ]]; then
  systemctl disable --now homeia-edge 2>/dev/null || true
  rm -f /etc/systemd/system/homeia-edge.service
  rm -rf "$PREFIX"
  systemctl daemon-reload
  echo "Desinstalado. Se conservan $ENV_FILE y /var/lib/homeia-edge (bórralos a mano si ya no los necesitas)."
  exit 0
fi

command -v python3 >/dev/null || { echo "Instalando Python 3"; apt-get update && apt-get install -y python3 python3-venv; }
python3 - <<'PY' || { echo "Se necesita Python 3.10 o superior" >&2; exit 1; }
import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)
PY
python3 -m venv --help >/dev/null 2>&1 || apt-get install -y python3-venv

id homeia-edge >/dev/null 2>&1 || useradd --system --home /var/lib/homeia-edge --shell /usr/sbin/nologin homeia-edge
install -d -m 0755 "$PREFIX"
install -d -m 0700 -o homeia-edge -g homeia-edge /var/lib/homeia-edge

# Copia del código (actualización atómica: primero a una carpeta nueva y luego se reemplaza).
rm -rf "$PREFIX/homeia_edge.new"
cp -r "$SRC/homeia_edge" "$PREFIX/homeia_edge.new"
find "$PREFIX/homeia_edge.new" -name '__pycache__' -prune -exec rm -rf {} +
rm -rf "$PREFIX/homeia_edge" && mv "$PREFIX/homeia_edge.new" "$PREFIX/homeia_edge"
cp "$SRC/requirements.txt" "$PREFIX/requirements.txt"

[[ -d "$PREFIX/venv" ]] || python3 -m venv "$PREFIX/venv"
"$PREFIX/venv/bin/pip" install --upgrade --quiet pip
"$PREFIX/venv/bin/pip" install --quiet -r "$PREFIX/requirements.txt"

if [[ ! -f "$ENV_FILE" ]]; then
  install -m 0600 -o root -g homeia-edge "$SRC/homeia-edge.env.example" "$ENV_FILE"
  chmod 0640 "$ENV_FILE"
  echo ">> Edita $ENV_FILE con HOMEIA_EDGE_ID, HOMEIA_EDGE_SECRET y el usuario MQTT, luego: systemctl restart homeia-edge"
fi
chmod 0640 "$ENV_FILE"; chown root:homeia-edge "$ENV_FILE"

install -m 0644 "$SRC/homeia-edge.service" /etc/systemd/system/homeia-edge.service
systemctl daemon-reload
systemctl enable homeia-edge >/dev/null

if grep -q '^HOMEIA_EDGE_ID=.\+' "$ENV_FILE" && grep -q '^HOMEIA_EDGE_SECRET=.\+' "$ENV_FILE"; then
  systemctl restart homeia-edge
  sleep 2
  systemctl --no-pager --lines=5 status homeia-edge || true
else
  echo ">> Falta configurar $ENV_FILE; el servicio arrancará cuando lo reinicies."
fi

# El reloj debe estar sincronizado: los mensajes firmados caducan si difiere más de 2 minutos.
timedatectl show -p NTPSynchronized --value 2>/dev/null | grep -q yes || echo "AVISO: el reloj no está sincronizado por NTP (timedatectl set-ntp true)."
echo "HomeIA Edge $("$PREFIX/venv/bin/python" -c 'import sys; sys.path.insert(0, "'"$PREFIX"'"); import homeia_edge; print(homeia_edge.__version__)') instalado."
