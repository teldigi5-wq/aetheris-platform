#!/usr/bin/env bash
# Disposable hosted CI only. Never runs against existing Aetheris compose projects.
set -euo pipefail
: "${RUNNER_TEMP:?Require ephemeral GitHub Actions runner}"
: "${GITHUB_SHA:?Require exact CI commit}"
: "${AETHERIS_JWT_SECRET:?Require generated synthetic signing key}"
: "${XERJ_TEST_PG_PASSWORD:?Require generated synthetic PostgreSQL password}"
: "${XERJ_TEST_RABBIT_PASSWORD:?Require generated synthetic RabbitMQ password}"
: "${XERJ_COMPOSE_PROJECT:?Require unique disposable Compose project}"
test "$XERJ_COMPOSE_PROJECT" != "aetheris" || exit 2

work="$(mktemp -d "${RUNNER_TEMP}/xerj-live-XXXXXXXX")"
chmod 700 "$work"
pids=()
finish() {
  for pid in "${pids[@]}"; do
    kill "$pid" >/dev/null 2>&1 || true
  done
  for pid in "${pids[@]}"; do
    wait "$pid" >/dev/null 2>&1 || true
  done
  # No application logs or auth responses are uploaded or printed.
}
trap finish EXIT

# The application config deliberately uses production-style service DNS.
# A fresh hosted runner maps those names to its loopback only for this test.
printf '127.0.0.1 user-service identity-service audit-service\n' |
  sudo tee -a /etc/hosts >/dev/null

export SERVER_ADDRESS=127.0.0.1
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55432/xerj_ci
export SPRING_DATASOURCE_USERNAME=xerj_ci
export SPRING_DATASOURCE_PASSWORD="$XERJ_TEST_PG_PASSWORD"
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
export LOGGING_LEVEL_ROOT=WARN

for svc in identity-service user-service audit-service gateway; do
  jar_path="$(find "$svc/target" -maxdepth 1 -type f -name '*.jar' ! -name '*.original' ! -name '*sources*' ! -name '*javadoc*' | head -n 1)"
  if [[ -z "$jar_path" || ! -s "$jar_path" ]]; then
    echo "XERJ_DISPOSABLE_MISSING_JAR service=$svc" >&2
    exit 1
  fi
  java -jar "$jar_path" > "$work/$svc.log" 2>&1 &
  pids+=("$!")
done

# No external network names/ports, credential values or tokens are printed.
python3 tests/security/xerj_disposable_http_proof.py \
  --report "$work/report.json" \
  --compose tests/security/xerj-disposable-deps.compose.yml

# Only machine-readable, non-sensitive, explicit passed assertions leave this folder.
python3 - "$work/report.json" <<'PY'
import json, sys
from pathlib import Path
data = json.loads(Path(sys.argv[1]).read_text())
assert data["status"] == "PASS"
assert data["source_commit"] == __import__("os").environ["GITHUB_SHA"]
assert data["evidence_class"] == "HOSTED_DISPOSABLE_INTEGRATION"
assert data["production_verified"] is False
assert data["tenant_isolation_verified"] is False
assert len(data["checks"]) >= 15 and all(v == "PASS" for v in data["checks"].values())
print("XERJ_DISPOSABLE_INTEGRATION_PASS checks=" + str(len(data["checks"])))
PY
mkdir -p "${RUNNER_TEMP}/xerj-integration-evidence"
cp "$work/report.json" "${RUNNER_TEMP}/xerj-integration-evidence/report.json"
