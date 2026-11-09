#!/usr/bin/env bash
# All keys/certificates are generated inside the fresh GitHub Actions runner.
set -euo pipefail
: "${RUNNER_TEMP:?Hosted disposable runner only}"
: "${XERJ_MTLS_KEYSTORE_PASSWORD:?Random test-only store password required}"
dir="$RUNNER_TEMP/xerj-mtls-fixture"
mkdir -p "$dir"
chmod 700 "$dir"
umask 077
openssl req -x509 -newkey rsa:3072 -nodes -sha256 -days 1 \
  -keyout "$dir/ca.key" -out "$dir/ca.crt" \
  -subj "/CN=XERJ isolated CI trust root" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign" >/dev/null 2>&1
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 1 \
  -keyout "$dir/untrusted-ca.key" -out "$dir/untrusted-ca.crt" \
  -subj "/CN=untrusted synthetic CI root" \
  -addext "basicConstraints=critical,CA:TRUE" >/dev/null 2>&1
issue_leaf() {
  local name="$1" use="$2" san="$3"
  openssl req -newkey rsa:2048 -nodes -sha256 \
    -keyout "$dir/$name.key" -out "$dir/$name.csr" \
    -subj "/CN=$name" >/dev/null 2>&1
  printf '%s\n' "basicConstraints=critical,CA:FALSE" \
    "keyUsage=critical,digitalSignature,keyEncipherment" \
    "extendedKeyUsage=$use" "subjectAltName=$san" > "$dir/$name.ext"
  openssl x509 -req -sha256 -days 1 -in "$dir/$name.csr" \
    -CA "$dir/ca.crt" -CAkey "$dir/ca.key" -CAcreateserial \
    -out "$dir/$name.crt" -extfile "$dir/$name.ext" >/dev/null 2>&1
  openssl pkcs12 -export -inkey "$dir/$name.key" \
    -in "$dir/$name.crt" -certfile "$dir/ca.crt" \
    -out "$dir/$name.p12" -name "$name" \
    -passout "pass:$XERJ_MTLS_KEYSTORE_PASSWORD" >/dev/null 2>&1
}
issue_leaf user-service serverAuth "DNS:user-service"
issue_leaf audit-service serverAuth "DNS:audit-service"
issue_leaf gateway-client clientAuth \
  "URI:spiffe://aetheris.test/ns/security/sa/gateway"
issue_leaf wrong-client clientAuth \
  "URI:spiffe://aetheris.test/ns/security/sa/attacker"
keytool -importcert -noprompt -storetype PKCS12 \
  -keystore "$dir/trusted-clients.p12" \
  -storepass "$XERJ_MTLS_KEYSTORE_PASSWORD" \
  -alias xerj-ci-ca -file "$dir/ca.crt" >/dev/null 2>&1
chmod -R go-rwx "$dir"
for service in user-service audit-service gateway-client wrong-client; do
  openssl verify -CAfile "$dir/ca.crt" "$dir/$service.crt" >/dev/null
done
openssl x509 -in "$dir/gateway-client.crt" -noout -ext subjectAltName |
  grep -Fq "URI:spiffe://aetheris.test/ns/security/sa/gateway"
openssl x509 -in "$dir/user-service.crt" -noout -ext subjectAltName |
  grep -Fq "DNS:user-service"
openssl x509 -in "$dir/audit-service.crt" -noout -ext subjectAltName |
  grep -Fq "DNS:audit-service"
echo "XERJ_SYNTHETIC_MTLS_X509_FIXTURE_OK"
