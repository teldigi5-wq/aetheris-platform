#!/usr/bin/env bash
# B62-G2: standalone synthetic verifier + disposable PostgreSQL probe ONLY.
# Do not run in the presence of any production Docker stack.
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
assert set(d["services"]) == {"postgres"}
assert len(d["networks"]) == 1 and all(v.get("internal") is True for v in d["networks"].values())
assert len(d.get("volumes",{})) == 1 and not any(v.get("external") for v in d["volumes"].values())
svc=d["services"]["postgres"]
assert not svc.get("ports") and not svc.get("privileged") and not svc.get("devices")
assert not any("docker.sock" in str(v) for v in svc.get("volumes",[]))
assert svc["environment"]["POSTGRES_DB"] == "b62g_synthetic"
assert svc["environment"]["POSTGRES_USER"] == "b62g_synthetic"
assert svc["environment"]["POSTGRES_PASSWORD"] == "test-only-b62g-never-use-in-production"
print("B62G2_DISPOSABLE_DB_ISOLATION_CONFIRMED")
'

"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
PSQL=(psql -X -qAt -v ON_ERROR_STOP=1 -U b62g_synthetic -d b62g_synthetic)
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g-revocation-prototype.sql
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g-policy-assertions.sql
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g2-readonly-role.sql
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" -c \
  "SELECT b62g_proof.raise_principal_epoch(42, 12000);" >/dev/null

if "${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" \
    -c "SET ROLE b62g_verifier;" \
    -c "SELECT b62g_proof.raise_principal_epoch(42, 9000);" >/dev/null 2>&1; then
  echo B62G2_READONLY_ROLE_COULD_MUTATE_EPOCH
  exit 1
fi
if "${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" \
    -c "SET ROLE b62g_verifier;" \
    -c "SELECT b62g_proof.block_signer('b62g-identity','key-current','emergency');" >/dev/null 2>&1; then
  echo B62G2_READONLY_ROLE_COULD_BLOCK_SIGNER
  exit 1
fi
echo B62G2_ROLE_CANNOT_WRITE_REVOCATION_STATE

python3 -m unittest discover -s tests -p 'test_b62g2_adapter_unit.py' -v
python3 tests/security/b62g2_live_acceptance.py online

"${COMPOSE[@]}" restart postgres
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
python3 tests/security/b62g2_live_acceptance.py restarted

"${COMPOSE[@]}" stop postgres
python3 tests/security/b62g2_live_acceptance.py outage

echo B62G2_SIGNED_SYNTHETIC_CONSUMER_FAIL_CLOSED_RESTART_AND_OUTAGE_PASS
echo B62G2_NOT_CONNECTED_TO_ANY_PRODUCTION_VERIFIER
