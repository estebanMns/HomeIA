#!/bin/sh
# Copia de seguridad de PostgreSQL (solo el esquema homeia) con retención.
#   PGHOST, PGUSER, PGPASSWORD, PGDATABASE (o DATABASE_URL) definen la conexión.
#   BACKUP_DIR (por defecto ./backups) y BACKUP_KEEP_DAYS (por defecto 14).
# Restaurar: pg_restore --clean --if-exists -d "$DATABASE_URL" backups/homeia-AAAAMMDD-HHMMSS.dump
set -eu
DIR="${BACKUP_DIR:-./backups}"
KEEP="${BACKUP_KEEP_DAYS:-14}"
mkdir -p "$DIR"
FILE="$DIR/homeia-$(date -u +%Y%m%d-%H%M%S).dump"
if [ -n "${DATABASE_URL:-}" ]; then
  pg_dump --format=custom --schema=homeia --no-owner --file="$FILE.tmp" "$DATABASE_URL"
else
  pg_dump --format=custom --schema=homeia --no-owner --file="$FILE.tmp"
fi
mv "$FILE.tmp" "$FILE"
chmod 600 "$FILE"
find "$DIR" -name 'homeia-*.dump' -type f -mtime +"$KEEP" -delete
echo "Copia creada: $FILE"
