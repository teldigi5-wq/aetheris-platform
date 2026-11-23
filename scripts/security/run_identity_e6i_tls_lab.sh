#!/usr/bin/env bash
# S0-E6I real disposable Identity Tomcat HTTPS mTLS lab.
# Absolutely no live Identity server, certificates or owner database.
set -euo pipefail
[[ "${AETHERIS_IDENTITY_TLS_LAB_SYNTHETIC_ONLY:-}" == "yes" ]] || {
  echo "E6I_DISPOSABLE_TLS_ONLY_REQUIRED" >&2
  exit 1
}
command -v openssl >/dev/null
command -v keytool >/dev/null
umask 077
scratch="$(mktemp -d /tmp/aetheris-e6i-identity-mtls.XXXXXXXX)"
chmod 700 "$scratch"
cleanup() {
  case "$scratch" in
    /tmp/aetheris-e6i-identity-mtls.*) rm -rf -- "$scratch" ;;
    *) echo "E6I_REFUSING_UNEXPECTED_CLEANUP" >&2; exit 1 ;;
  esac
}
trap cleanup EXIT
export AETHERIS_IDENTITY_TLS_LAB_PASSWORD
AETHERIS_IDENTITY_TLS_LAB_PASSWORD="$(openssl rand -hex 24)"
export AETHERIS_IDENTITY_TLS_LAB_MUST_RUN=yes
export AETHERIS_IDENTITY_TLS_LAB_DISPOSABLE_MARKER=ONLY_DISPOSABLE_E6I_CERTIFICATES

openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 1 \
  -keyout "$scratch/ca.key" -out "$scratch/ca.crt" \
  -subj '/CN=Aetheris E6I Synthetic Restricted CA' \
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
  -subj '/CN=E6I Synthetic Gateway Peer' >/dev/null 2>&1
printf 'basicConstraints=critical,CA:FALSE\nsubjectAltName=URI:spiffe://aetheris.test/ns/security/sa/gateway\nextendedKeyUsage=clientAuth\nkeyUsage=digitalSignature\n' > "$scratch/gateway.ext"
openssl x509 -req -sha256 -days 1 -in "$scratch/gateway.csr" \
  -CA "$scratch/ca.crt" -CAkey "$scratch/ca.key" -CAcreateserial \
  -extfile "$scratch/gateway.ext" -out "$scratch/gateway.crt" >/dev/null 2>&1

openssl req -new -newkey rsa:2048 -nodes -sha256 \
  -keyout "$scratch/wrong-client.key" -out "$scratch/wrong-client.csr" \
  -subj '/CN=E6I Synthetic Wrong Peer' >/dev/null 2>&1
printf 'basicConstraints=critical,CA:FALSE\nsubjectAltName=URI:spiffe://aetheris.test/ns/security/sa/attacker\nextendedKeyUsage=clientAuth\nkeyUsage=digitalSignature\n' > "$scratch/wrong-client.ext"
openssl x509 -req -sha256 -days 1 -in "$scratch/wrong-client.csr" \
  -CA "$scratch/ca.crt" -CAkey "$scratch/ca.key" -CAcreateserial \
  -extfile "$scratch/wrong-client.ext" -out "$scratch/wrong-client.crt" >/dev/null 2>&1

openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 1 \
  -keyout "$scratch/untrusted-ca.key" -out "$scratch/untrusted-ca.crt" \
  -subj '/CN=E6I Synthetic Untrusted CA' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign' >/dev/null 2>&1
openssl req -new -newkey rsa:2048 -nodes -sha256 \
  -keyout "$scratch/untrusted-client.key" -out "$scratch/untrusted-client.csr" \
  -subj '/CN=E6I Synthetic Untrusted Gateway' >/dev/null 2>&1
printf 'basicConstraints=critical,CA:FALSE\nsubjectAltName=URI:spiffe://aetheris.test/ns/security/sa/gateway\nextendedKeyUsage=clientAuth\nkeyUsage=digitalSignature\n' > "$scratch/untrusted-client.ext"
openssl x509 -req -sha256 -days 1 -in "$scratch/untrusted-client.csr" \
  -CA "$scratch/untrusted-ca.crt" -CAkey "$scratch/untrusted-ca.key" -CAcreateserial \
  -extfile "$scratch/untrusted-client.ext" \
  -out "$scratch/untrusted-client.crt" >/dev/null 2>&1

openssl verify -CAfile "$scratch/ca.crt" -purpose sslserver "$scratch/backend.crt" >/dev/null
openssl verify -CAfile "$scratch/ca.crt" -purpose sslclient "$scratch/gateway.crt" >/dev/null
openssl verify -CAfile "$scratch/ca.crt" -purpose sslclient "$scratch/wrong-client.crt" >/dev/null
if openssl verify -CAfile "$scratch/ca.crt" -purpose sslclient \
    "$scratch/untrusted-client.crt" >/dev/null 2>&1; then
  echo "E6I_UNTRUSTED_CERT_UNEXPECTEDLY_TRUSTED" >&2
  exit 1
fi

for client in gateway wrong-client untrusted-client; do
  if [[ "$client" == "untrusted-client" ]]; then
    cafile="$scratch/untrusted-ca.crt"
  else
    cafile="$scratch/ca.crt"
  fi
  openssl pkcs12 -export -inkey "$scratch/$client.key" \
    -in "$scratch/$client.crt" -certfile "$cafile" \
    -out "$scratch/$client.p12" \
    -passout env:AETHERIS_IDENTITY_TLS_LAB_PASSWORD >/dev/null 2>&1
done
openssl pkcs12 -export -inkey "$scratch/backend.key" \
  -in "$scratch/backend.crt" -certfile "$scratch/ca.crt" \
  -out "$scratch/backend.p12" \
  -passout env:AETHERIS_IDENTITY_TLS_LAB_PASSWORD >/dev/null 2>&1
keytool -importcert -noprompt -alias e6i-disposable-restricted-ca \
  -file "$scratch/ca.crt" -storetype PKCS12 \
  -keystore "$scratch/ca-trust.p12" \
  -storepass:env AETHERIS_IDENTITY_TLS_LAB_PASSWORD >/dev/null 2>&1
chmod 600 "$scratch/"*.key "$scratch/"*.p12
# Must prove actual execution, not a quietly skipped JUnit class.
./mvnw -B -ntp -f identity-service/pom.xml \
  -Dtest=RealIdentityHttpsMutualTlsWorkloadTest \
  -Daetheris.identity.tls.fixture="$scratch" test
python3 - <<'PY'
from pathlib import Path
from xml.etree import ElementTree as ET
p=Path('identity-service/target/surefire-reports/TEST-io.aetheris.identity.RealIdentityHttpsMutualTlsWorkloadTest.xml')
assert p.exists(),'The real TLS test class was not executed'
x=ET.parse(p).getroot()
assert int(x.attrib.get('tests',0))==4,'Expected four actual TLS socket cases'
for key in ('failures','errors','skipped'):
    assert int(x.attrib.get(key,-1))==0,(key,x.attrib.get(key))
print('AETHERIS_S0_E6I_REAL_TOMCAT_HTTPS_FOUR_TLS_HANDSHAKE_TESTS_PASS')
PY
echo AETHERIS_S0_E6I_SYNTHETIC_CA_ONLY_NO_LIVE_IDENTITY_SERVER_PASS
