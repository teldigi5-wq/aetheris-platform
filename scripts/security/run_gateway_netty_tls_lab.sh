#!/usr/bin/env bash
# Isolated real TLS socket transport proof with throwaway cryptographic keys.
# Never installs any certificates or changes live Gateway/backend services.
set -euo pipefail
[[ "${AETHERIS_NETTY_MTLS_LAB_SYNTHETIC_ONLY:-}" == "yes" ]] || {
  echo "SYNTHETIC_NETTY_MTLS_ONLY_REQUIRED" >&2
  exit 1
}
umask 077
scratch="$(mktemp -d /tmp/aetheris-netty-mtls.XXXXXXXX)"
chmod 700 "$scratch"
cleanup() {
  case "$scratch" in
    /tmp/aetheris-netty-mtls.*) rm -rf -- "$scratch" ;;
    *) echo "REFUSING_UNEXPECTED_TLS_CLEANUP" >&2; exit 1 ;;
  esac
}
trap cleanup EXIT
export AETHERIS_TLS_LAB_PASSWORD
AETHERIS_TLS_LAB_PASSWORD="$(openssl rand -hex 24)"
export AETHERIS_TLS_LAB_MUST_RUN=yes

openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 1 \
  -keyout "$scratch/ca.key" -out "$scratch/ca.crt" \
  -subj '/CN=Aetheris Disposable Netty CI CA' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign' >/dev/null 2>&1
openssl req -new -newkey rsa:2048 -nodes -sha256 \
  -keyout "$scratch/backend.key" -out "$scratch/backend.csr" \
  -subj '/CN=localhost' >/dev/null 2>&1
printf 'basicConstraints=critical,CA:FALSE\nsubjectAltName=DNS:localhost\nextendedKeyUsage=serverAuth\nkeyUsage=digitalSignature,keyEncipherment\n' > "$scratch/backend.ext"
openssl x509 -req -sha256 -days 1 -in "$scratch/backend.csr" \
  -CA "$scratch/ca.crt" -CAkey "$scratch/ca.key" -CAcreateserial \
  -extfile "$scratch/backend.ext" -out "$scratch/backend.crt" >/dev/null 2>&1
openssl req -new -newkey rsa:2048 -nodes -sha256 \
  -keyout "$scratch/gateway.key" -out "$scratch/gateway.csr" \
  -subj '/CN=CI-only synthetic gateway client' >/dev/null 2>&1
printf 'basicConstraints=critical,CA:FALSE\nsubjectAltName=URI:spiffe://aetheris.test/ns/security/sa/gateway\nextendedKeyUsage=clientAuth\nkeyUsage=digitalSignature\n' > "$scratch/gateway.ext"
openssl x509 -req -sha256 -days 1 -in "$scratch/gateway.csr" \
  -CA "$scratch/ca.crt" -CAkey "$scratch/ca.key" -CAcreateserial \
  -extfile "$scratch/gateway.ext" -out "$scratch/gateway.crt" >/dev/null 2>&1
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 1 \
  -keyout "$scratch/untrusted-ca.key" -out "$scratch/untrusted-ca.crt" \
  -subj '/CN=Untrusted Disposable Netty CA' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign' >/dev/null 2>&1

openssl verify -CAfile "$scratch/ca.crt" -purpose sslserver "$scratch/backend.crt" >/dev/null
openssl verify -CAfile "$scratch/ca.crt" -purpose sslclient "$scratch/gateway.crt" >/dev/null
openssl pkcs12 -export -inkey "$scratch/gateway.key" \
  -in "$scratch/gateway.crt" -certfile "$scratch/ca.crt" \
  -out "$scratch/gateway.p12" -passout env:AETHERIS_TLS_LAB_PASSWORD >/dev/null 2>&1
chmod 600 "$scratch/"{ca.key,backend.key,gateway.key,untrusted-ca.key,gateway.p12}
./mvnw -B -ntp -f gateway/pom.xml \
  -Dtest=GatewayDisposableNettyMutualTlsSocketTest \
  -Daetheris.tls.lab.fixture="$scratch" test
echo AETHERIS_REAL_DISPOSABLE_NETTY_MTLS_TLS_HANDSHAKE_PASS
echo AETHERIS_TLS_LAB_NOT_A_DEPLOYMENT_OR_BACKEND_IDENTITY_CERTIFICATION
