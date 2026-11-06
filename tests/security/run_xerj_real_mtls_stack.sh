#!/usr/bin/env bash
set -euo pipefail
: "${RUNNER_TEMP:?Ephemeral hosted runner only}"
: "${GITHUB_SHA:?Exact SHA needed}"
: "${XERJ_COMPOSE_PROJECT:?Unique test Compose name needed}"
: "${XERJ_TEST_PG_PASSWORD:?Synthetic database password required}"
: "${XERJ_TEST_RABBIT_PASSWORD:?Synthetic broker password required}"
: "${XERJ_MTLS_KEYSTORE_PASSWORD:?Synthetic keystore password required}"
[[ "$XERJ_COMPOSE_PROJECT" == xerj-mtls-ci-* ]] || exit 2
test -z "${AETHERIS_JWT_SECRET:-}" || exit 2
dir="$RUNNER_TEMP/xerj-mtls-fixture"
test -s "$dir/gateway-client.p12" && test -s "$dir/ca.crt"
test -s "$RUNNER_TEMP/xerj-ci-rs256-private.pem" &&
  test -s "$RUNNER_TEMP/xerj-ci-rs256-public.pem"
privatePem="$(cat "$RUNNER_TEMP/xerj-ci-rs256-private.pem")"
publicPem="$(cat "$RUNNER_TEMP/xerj-ci-rs256-public.pem")"
work="$(mktemp -d "$RUNNER_TEMP/xerj-real-mtls-XXXXXXXX")"
chmod 700 "$work"
pids=()
finish() {
  for pid in "${pids[@]}"; do kill "$pid" >/dev/null 2>&1 || true; done
  for pid in "${pids[@]}"; do wait "$pid" >/dev/null 2>&1 || true; done
}
trap finish EXIT
printf '127.0.0.1 user-service identity-service audit-service\n' |
  sudo tee -a /etc/hosts >/dev/null

export SERVER_ADDRESS=127.0.0.1
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55432/xerj_ci
export SPRING_DATASOURCE_USERNAME=xerj_ci
export SPRING_DATASOURCE_PASSWORD="$XERJ_TEST_PG_PASSWORD"
# ONLY this uniquely named empty synthetic hosted PostgreSQL.
export SPRING_JPA_HIBERNATE_DDL_AUTO=update
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=56379
export SPRING_RABBITMQ_HOST=127.0.0.1
export SPRING_RABBITMQ_PORT=55672
export SPRING_RABBITMQ_USERNAME=xerj_ci
export SPRING_RABBITMQ_PASSWORD="$XERJ_TEST_RABBIT_PASSWORD"
export MANAGEMENT_TRACING_ENABLED=false
export MANAGEMENT_HEALTH_DB_ENABLED=true
export MANAGEMENT_HEALTH_REDIS_ENABLED=true
export MANAGEMENT_HEALTH_RABBIT_ENABLED=true
export AETHERIS_JWT_ISSUER=xerj-mtls-review-identity
export AETHERIS_JWT_AUDIENCE=xerj-mtls-review-api
export AETHERIS_JWT_ALGORITHM=RS256
export AETHERIS_JWT_RS256_KEY_ID=xerj-ci-20261106-mtls1
export LOGGING_LEVEL_ROOT=WARN
export NO_PROXY=localhost,127.0.0.1,user-service,audit-service,identity-service
export no_proxy="$NO_PROXY"

for svc in identity-service user-service audit-service gateway; do
  jarfile="$(find "$svc/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' ! -name '*sources*' ! -name '*javadoc*' | head -n 1)"
  [[ -n "$jarfile" && -s "$jarfile" ]] || { echo "XERJ_REAL_MTLS_JAR_MISSING" >&2; exit 1; }
  if [[ "$svc" == "identity-service" ]]; then
    env -u AETHERIS_JWT_SECRET -u AETHERIS_JWT_RSA_PUBLIC_KEY_PEM \
      AETHERIS_JWT_RSA_PRIVATE_KEY_PEM="$privatePem" \
      java -jar "$jarfile" > "$work/$svc.log" 2>&1 &
  elif [[ "$svc" == "gateway" ]]; then
    env -u AETHERIS_JWT_SECRET -u AETHERIS_JWT_RSA_PRIVATE_KEY_PEM \
      AETHERIS_JWT_RSA_PUBLIC_KEY_PEM="$publicPem" \
      SPRING_PROFILES_ACTIVE=xerj-mtls-review \
      AETHERIS_USER_BACKEND_URI=https://user-service:8081 \
      AETHERIS_AUDIT_BACKEND_URI=https://audit-service:8083 \
      XERJ_MTLS_CLIENT_KEYSTORE="file:$dir/gateway-client.p12" \
      XERJ_MTLS_SERVER_CA_CERT="file:$dir/ca.crt" \
      java -jar "$jarfile" > "$work/$svc.log" 2>&1 &
  else
    env -u AETHERIS_JWT_SECRET -u AETHERIS_JWT_RSA_PRIVATE_KEY_PEM \
      AETHERIS_JWT_RSA_PUBLIC_KEY_PEM="$publicPem" \
      SERVER_SSL_ENABLED=true SERVER_SSL_CLIENT_AUTH=need \
      SERVER_SSL_KEY_STORE="file:$dir/$svc.p12" \
      SERVER_SSL_KEY_STORE_TYPE=PKCS12 \
      SERVER_SSL_KEY_STORE_PASSWORD="$XERJ_MTLS_KEYSTORE_PASSWORD" \
      SERVER_SSL_TRUST_STORE="file:$dir/trusted-clients.p12" \
      SERVER_SSL_TRUST_STORE_TYPE=PKCS12 \
      SERVER_SSL_TRUST_STORE_PASSWORD="$XERJ_MTLS_KEYSTORE_PASSWORD" \
      AETHERIS_BACKEND_GATEWAY_MTLS_REQUIRED=true \
      AETHERIS_BACKEND_GATEWAY_SPIFFE_ID=spiffe://aetheris.test/ns/security/sa/gateway \
      java -jar "$jarfile" > "$work/$svc.log" 2>&1 &
  fi
  pids+=("$!")
done

proof="${XERJ_BOUNDARY_PROOF_PROGRAM:-tests/security/xerj_real_mtls_http_proof.py}"
case "$proof" in
  tests/security/xerj_real_mtls_http_proof.py|tests/security/xerj_tenant_http_proof.py) ;;
  *) echo "XERJ_UNREVIEWED_SECURITY_PROOF_REFUSED" >&2; exit 2 ;;
esac
python3 "$proof" \
  --report "$work/report.json" --compose tests/security/xerj-disposable-deps.compose.yml

python3 - "$work/report.json" <<'PY'
import json,os,sys
from pathlib import Path
r=json.loads(Path(sys.argv[1]).read_text())
assert r["source_commit"] == os.environ["GITHUB_SHA"]
assert r["status"]=="PASS"
assert r["evidence_class"]=="HOSTED_DISPOSABLE_REAL_GATEWAY_TO_TOMCAT_MTLS"
assert r["mtls_scope"]=="HOSTED_EPHEMERAL_ACTUAL_TLS_SOCKETS_ONLY"
assert r["mTLS_verified"] is True and r["production_mTLS_verified"] is False
assert r["production_verified"] is False and r["tenant_isolation_verified"] is False
assert len(r["checks"])>=47 and all(v=="PASS" for v in r["checks"].values())
print("XERJ_REAL_MTLS_SANITIZED_REPORT_PASS checks="+str(len(r["checks"])))
PY
mkdir -p "$RUNNER_TEMP/xerj-real-mtls-safe-report"
cp "$work/report.json" "$RUNNER_TEMP/xerj-real-mtls-safe-report/report.json"
