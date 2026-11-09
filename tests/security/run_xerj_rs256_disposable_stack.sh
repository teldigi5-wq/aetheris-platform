#!/usr/bin/env bash
# RSA-only hosted integration: a fresh CI signing key never reaches resource servers.
set -euo pipefail
: "${RUNNER_TEMP:?Requires disposable CI runner}"
: "${GITHUB_SHA:?Exact SHA needed}"
: "${XERJ_COMPOSE_PROJECT:?Disposable Compose project required}"
: "${XERJ_TEST_PG_PASSWORD:?Disposable database secret required}"
: "${XERJ_TEST_RABBIT_PASSWORD:?Disposable RabbitMQ secret required}"
test -z "${AETHERIS_JWT_SECRET:-}" || { echo "XERJ_RS256_MIXED_SIGNING_KEYS_DENIED" >&2; exit 2; }
test "$XERJ_COMPOSE_PROJECT" != "aetheris" || exit 2
privateFile="$RUNNER_TEMP/xerj-ci-rs256-private.pem"
publicFile="$RUNNER_TEMP/xerj-ci-rs256-public.pem"
test -s "$privateFile" && test -s "$publicFile"
privatePem="$(cat "$privateFile")"
publicPem="$(cat "$publicFile")"
work="$(mktemp -d "${RUNNER_TEMP}/xerj-rsa-review-XXXXXXXX")"
chmod 700 "$work"
pids=()
finish() {
  for pid in "${pids[@]}"; do kill "$pid" >/dev/null 2>&1 || true; done
  for pid in "${pids[@]}"; do wait "$pid" >/dev/null 2>&1 || true; done
}
trap finish EXIT
printf '127.0.0.1 user-service identity-service audit-service\n' | sudo tee -a /etc/hosts >/dev/null

export SERVER_ADDRESS=127.0.0.1
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55432/xerj_ci
export SPRING_DATASOURCE_USERNAME=xerj_ci
export SPRING_DATASOURCE_PASSWORD="$XERJ_TEST_PG_PASSWORD"
# This intentionally creates tables ONLY in this disposable test DB.
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
export AETHERIS_JWT_ISSUER=aetheris-identity
export AETHERIS_JWT_AUDIENCE=aetheris-api
export AETHERIS_JWT_ALGORITHM=RS256
export AETHERIS_JWT_RS256_KEY_ID=xerj-ci-20261106-1
export LOGGING_LEVEL_ROOT=WARN

for svc in identity-service user-service audit-service gateway; do
  jarfile="$(find "$svc/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' ! -name '*sources*' ! -name '*javadoc*' | head -n 1)"
  [[ -n "$jarfile" && -s "$jarfile" ]] || { echo "XERJ_RS256_JAR_MISSING" >&2; exit 1; }
  if [[ "$svc" == "identity-service" ]]; then
    # Identity is the ONLY app that receives the signing private key.
    env -u AETHERIS_JWT_SECRET -u AETHERIS_JWT_RSA_PUBLIC_KEY_PEM \
      AETHERIS_JWT_RSA_PRIVATE_KEY_PEM="$privatePem" \
      java -jar "$jarfile" > "$work/$svc.log" 2>&1 &
  else
    # Resource servers receive the pinned public key and NEVER the signing private key.
    env -u AETHERIS_JWT_SECRET -u AETHERIS_JWT_RSA_PRIVATE_KEY_PEM \
      AETHERIS_JWT_RSA_PUBLIC_KEY_PEM="$publicPem" \
      java -jar "$jarfile" > "$work/$svc.log" 2>&1 &
  fi
  pids+=("$!")
done

python3 tests/security/xerj_rs256_http_proof.py \
  --report "$work/report.json" --compose tests/security/xerj-disposable-deps.compose.yml

python3 - "$work/report.json" <<'PY'
import json, os, sys
from pathlib import Path
r=json.loads(Path(sys.argv[1]).read_text())
assert r["status"]=="PASS"
assert r["source_commit"]==os.environ["GITHUB_SHA"]
assert r["evidence_class"]=="HOSTED_DISPOSABLE_RS256_INTEGRATION"
assert r["production_verified"] is False
assert r["tenant_isolation_verified"] is False
assert r["mTLS_verified"] is False
assert len(r["checks"])>=29 and all(x=="PASS" for x in r["checks"].values())
print("XERJ_RS256_REAL_INTEGRATION_PASS checks="+str(len(r["checks"])))
PY
mkdir -p "$RUNNER_TEMP/xerj-rs256-safe-report"
cp "$work/report.json" "$RUNNER_TEMP/xerj-rs256-safe-report/report.json"
