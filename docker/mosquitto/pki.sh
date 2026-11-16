#!/usr/bin/env bash
# PKI mínima para MQTT con TLS mutuo. Guarda la CA fuera del servidor una vez emitidos los certificados.
#   ./pki.sh ca                       crea la CA (certs/ca.crt, certs/ca.key)
#   ./pki.sh server broker.midominio.com   certificado del broker
#   ./pki.sh client homeia-backend    certificado del backend
#   ./pki.sh client <HOMEIA_EDGE_ID>  certificado de un Edge (el CN debe ser su id)
#   ./pki.sh revoke <nombre>          añade el certificado a la CRL (configura crlfile en mosquitto)
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p certs && chmod 700 certs
DAYS_CA=3650; DAYS=825
case "${1:-}" in
  ca)
    [[ -f certs/ca.key ]] && { echo "La CA ya existe" >&2; exit 1; }
    openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out certs/ca.key
    openssl req -x509 -new -key certs/ca.key -sha256 -days $DAYS_CA -subj "/CN=HomeIA MQTT CA" -out certs/ca.crt
    touch certs/index.txt; echo 1000 > certs/crlnumber ;;
  server)
    host="${2:?nombre DNS del broker}"
    openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out certs/server.key
    openssl req -new -key certs/server.key -subj "/CN=$host" -out certs/server.csr
    printf "subjectAltName=DNS:%s\nextendedKeyUsage=serverAuth\n" "$host" > certs/server.ext
    openssl x509 -req -in certs/server.csr -CA certs/ca.crt -CAkey certs/ca.key -CAcreateserial \
      -days $DAYS -sha256 -extfile certs/server.ext -out certs/server.crt ;;
  client)
    name="${2:?CN del cliente}"
    [[ "$name" =~ ^[A-Za-z0-9-]+$ ]] || { echo "CN inválido" >&2; exit 1; }
    # PKCS#8 (BEGIN PRIVATE KEY): es el formato que lee el backend Java.
    openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "certs/$name.key"
    openssl req -new -key "certs/$name.key" -subj "/CN=$name" -out "certs/$name.csr"
    printf "extendedKeyUsage=clientAuth\n" > "certs/$name.ext"
    openssl x509 -req -in "certs/$name.csr" -CA certs/ca.crt -CAkey certs/ca.key -CAcreateserial \
      -days $DAYS -sha256 -extfile "certs/$name.ext" -out "certs/$name.crt"
    chmod 600 "certs/$name.key"
    echo "Emitido certs/$name.crt (clave certs/$name.key)" ;;
  revoke)
    name="${2:?nombre}"
    cat > certs/ca.cnf <<CNF
[ ca ]
default_ca = homeia
[ homeia ]
database = certs/index.txt
crlnumber = certs/crlnumber
default_md = sha256
default_crl_days = 30
CNF
    openssl ca -config certs/ca.cnf -keyfile certs/ca.key -cert certs/ca.crt -revoke "certs/$name.crt"
    openssl ca -config certs/ca.cnf -keyfile certs/ca.key -cert certs/ca.crt -gencrl -out certs/ca.crl
    echo "Añade 'crlfile /mosquitto/certs/ca.crl' al listener y reinicia Mosquitto." ;;
  *) sed -n '2,8p' "$0"; exit 1 ;;
esac
