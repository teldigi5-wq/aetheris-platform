#!/usr/bin/env bash
# B62-G9 disposable two-Postgres counterexample: primary is authoritative.
# "stale-snapshot" is an INDEPENDENT INTENTIONALLY STALE DB, NOT streaming
# replication, a backup, a physical replica, or an approved failover target.
set -euo pipefail
cd "$(dirname "$0")/../.."
COMPOSE=(
  docker compose
  -f tests/security/docker-compose.b62g-epoch-proof.yml
  -f tests/security/b62g9-stale-snapshot-overlay.yml
)
cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans --timeout 10 >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${COMPOSE[@]}" config --format json | python3 -c '
import sys,json
d=json.load(sys.stdin)
assert set(d["services"]) == {"postgres","stale-snapshot"}
assert len(d["networks"])==1
assert all(v.get("internal") is True for v in d["networks"].values())
assert len(d.get("volumes",{}))==2
assert all(not v.get("external") for v in d["volumes"].values())
for name,svc in d["services"].items():
    assert not svc.get("ports") and not svc.get("privileged"),name
    assert not svc.get("devices") and not svc.get("pid"),name
    assert not any("docker.sock" in str(v) for v in svc.get("volumes",[])),name
    assert svc["environment"]["POSTGRES_DB"]=="b62g_synthetic"
    assert svc["environment"]["POSTGRES_USER"]=="b62g_synthetic"
    assert svc["environment"]["POSTGRES_PASSWORD"]=="test-only-b62g-never-use-in-production"
print("B62G9_TWO_DISPOSABLE_DB_NO_HOST_PORTS_ISOLATION_PASS",flush=True)
'
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres stale-snapshot

ADMIN=(psql -X -qAt -v ON_ERROR_STOP=1 -U b62g_synthetic -d b62g_synthetic)
for service in postgres stale-snapshot; do
  "${COMPOSE[@]}" exec -T "$service" "${ADMIN[@]}" < tests/security/b62g-revocation-prototype.sql
  "${COMPOSE[@]}" exec -T "$service" "${ADMIN[@]}" < tests/security/b62g-policy-assertions.sql
  "${COMPOSE[@]}" exec -T "$service" "${ADMIN[@]}" < tests/security/b62g2-readonly-role.sql
  "${COMPOSE[@]}" exec -T "$service" "${ADMIN[@]}" < tests/security/b62g8-login-roles.sql
done

writer_sql() {
  # Test-only password, never live key material. The reader clients use
  # THREE distinct TCP-authenticated logins, not temporary SET ROLE.
  "${COMPOSE[@]}" exec -T -e 'PGPASSWORD=b62g8-writer-synthetic-ci-only' \
    postgres timeout 10s psql -X -qAt -v ON_ERROR_STOP=1 \
      -h 127.0.0.1 -p 5432 -U b62g8_emergency_writer -d b62g_synthetic \
      -c "$1"
}
client() {
  python3 tests/security/b62g9-primary-consistency-client.py --stage "$1"
}

# Both copies initially permit the same verified synthetic JWT tuple.
client baseline

# All subsequent reads are begun only AFTER this writer commit succeeds.
# The independent stale DB intentionally receives no corresponding UPDATE.
writer_sql "SELECT b62g_proof.raise_principal_epoch(42,12000);" >/dev/null
client postcommit

# Prove real competing TCP authenticated writer processes on primary only.
pids=()
for cutoff in 13000 12500 13999 14000 13500 10000 12001 8000; do
  writer_sql "SELECT b62g_proof.raise_principal_epoch(42,${cutoff});" >/dev/null &
  pids+=("$!")
done
for pid in "${pids[@]}"; do
  wait "$pid"
done
latest=$("${COMPOSE[@]}" exec -T postgres "${ADMIN[@]}" -c \
  "SELECT reject_issued_at_or_before_epoch FROM b62g_proof.principal_epochs WHERE principal_id=42;")
test "$latest" = "14000"
echo B62G9_AUTHENTICATED_PARALLEL_WRITES_FINAL_MONOTONIC_EPOCH_14000

# A signer block is committed on the real primary; the stale independent
# snapshot continues to ALLOW and must NEVER authorize any request.
writer_sql "SELECT b62g_proof.block_signer('b62g-identity','key-current','compromise');" >/dev/null
client blocked

"${COMPOSE[@]}" restart postgres
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
client restarted

"${COMPOSE[@]}" stop postgres
client outage
echo B62G9_STALE_READ_DENY_NO_FALLBACK_SYNTHETIC_CONSISTENCY_PASS
echo B62G9_NO_DURABLE_REPLICATION_NO_PRODUCTION_INTEGRATION
