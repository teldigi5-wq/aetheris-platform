#!/usr/bin/env bash
# B62-G standalone *synthetic* PostgreSQL concurrency/restart proof.
# Caller must be an isolated CI runner. Never connect to a user/production DB.
set -euo pipefail
cd "$(dirname "$0")/../.."
COMPOSE=(docker compose -f tests/security/docker-compose.b62g-epoch-proof.yml)
cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans --timeout 10 >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${COMPOSE[@]}" config --format json | python3 -c '
import json,sys
d=json.load(sys.stdin)
assert set(d["services"]) == {"postgres"}, "Only disposable DB service allowed"
assert len(d["networks"]) == 1 and all(v.get("internal") is True for v in d["networks"].values())
assert len(d.get("volumes",{})) == 1
assert not any(v.get("external") for v in d["volumes"].values())
svc=d["services"]["postgres"]
assert not svc.get("ports") and not svc.get("privileged")
assert not svc.get("devices") and not svc.get("pid")
assert not any("docker.sock" in str(v) for v in svc.get("volumes",[]))
assert svc["environment"]["POSTGRES_DB"] == "b62g_synthetic"
assert svc["environment"]["POSTGRES_PASSWORD"] == "test-only-b62g-never-use-in-production"
print("B62G_ISOLATED_UNEXPOSED_TEMP_VOLUME_CONFIRMED")
'

"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
PSQL=(psql -X -v ON_ERROR_STOP=1 -U b62g_synthetic -d b62g_synthetic)
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g-revocation-prototype.sql
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g-policy-assertions.sql

# Exercise actual competing PostgreSQL writers through separate SQL sessions.
pids=()
for cutoff in 9100 9000 9500 9400 11999 10000 11000 12000 9501 8000; do
  "${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" -q -c \
    "SELECT b62g_proof.raise_principal_epoch(42, ${cutoff});" >/dev/null &
  pids+=("$!")
done
for pid in "${pids[@]}"; do
  wait "$pid"
done
latest=$("${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" -tA -c \
  "SELECT reject_issued_at_or_before_epoch FROM b62g_proof.principal_epochs WHERE principal_id=42;")
test "$latest" = "12000"
printf 'B62G_CONCURRENT_MONOTONIC_EPOCH_PASS cutoff=%s sessions=%s\n' "$latest" "${#pids[@]}"

# Prototype fail-closed consumer: any unavailable/invalid DB response is DENY,
# not success. It is not wired to Gateway or any deployed auth service.
proof_decision() {
  local principal="$1" kid="$2" iat="$3" result
  if ! result=$("${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" -tA -c \
      "SELECT b62g_proof.synthetic_post_signature_allowed(${principal}, 'b62g-identity', '${kid}', ${iat});" 2>/dev/null); then
    printf 'DENY'
    return
  fi
  case "$result" in
    t) printf 'ALLOW' ;;
    *) printf 'DENY' ;;
  esac
}
test "$(proof_decision 42 key-current 11999)" = "DENY"
test "$(proof_decision 42 key-current 12000)" = "DENY"
test "$(proof_decision 42 key-current 12001)" = "ALLOW"
test "$(proof_decision 43 key-compromised 12001)" = "DENY"
test "$(proof_decision 43 key-current 12001)" = "ALLOW"
echo B62G_SQL_POLICY_ALLOW_DENY_BOUNDS_PASS

# The named volume is synthetic and survives only this *container restart*;
# trap deletes it on all workflow exits. Never claim production backup proof.
"${COMPOSE[@]}" restart postgres
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
test "$(proof_decision 42 key-current 11999)" = "DENY"
test "$(proof_decision 42 key-current 12001)" = "ALLOW"
test "$(proof_decision 43 key-compromised 12001)" = "DENY"
echo B62G_EPOCHS_SURVIVE_DISPOSABLE_POSTGRES_RESTART

"${COMPOSE[@]}" stop postgres
test "$(proof_decision 42 key-current 12001)" = "DENY"
test "$(proof_decision 43 key-current 12001)" = "DENY"
echo B62G_UNAVAILABLE_STORE_DENIED_BY_SYNTHETIC_CLIENT_ONLY
echo B62G_PROTOTYPE_NOT_INTEGRATED_NOT_PRODUCTION_CERTIFIED
