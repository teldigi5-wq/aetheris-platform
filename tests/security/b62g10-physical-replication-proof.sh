#!/usr/bin/env bash
# B62-G10 real PostgreSQL physical WAL streaming, disposable runner only.
# No production DB, no promotion, no durable replicas, NO host-published ports.
set -euo pipefail
cd "$(dirname "$0")/../.."
COMPOSE=(docker compose
  -f tests/security/docker-compose.b62g-epoch-proof.yml
  -f tests/security/b62g10-physical-replica-overlay.yml)
cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans --timeout 10 >/dev/null 2>&1 || true
}
trap cleanup EXIT
"${COMPOSE[@]}" config --format json | python3 -c '
import json,sys
d=json.load(sys.stdin)
assert set(d["services"]) == {"postgres","replica","auth-probe"}
assert len(d["networks"])==1 and all(x.get("internal") is True for x in d["networks"].values())
assert len(d["volumes"])==2 and all(not x.get("external") for x in d["volumes"].values())
for name,service in d["services"].items():
    assert not service.get("ports"),name
    assert not service.get("privileged") and not service.get("devices"),name
    assert not service.get("pid") and not service.get("network_mode"),name
    assert not any("docker.sock" in str(x) for x in service.get("volumes",[])),name
p=d["services"]["postgres"]
assert p["environment"]["POSTGRES_DB"]=="b62g_synthetic"
assert p["environment"]["POSTGRES_PASSWORD"]=="test-only-b62g-never-use-in-production"
replica=d["services"]["replica"]
assert replica["image"].startswith("postgres:16-")
assert replica["entrypoint"]==["sh","/tests/replica-bootstrap.sh"]
assert any("b62g10-replica-bootstrap.sh" in str(v) for v in replica.get("volumes",[]))
probe=d["services"]["auth-probe"]
assert not probe.get("environment") and not probe.get("volumes")
assert "ALL" in {str(v).upper() for v in probe.get("cap_drop",[])}
assert "no-new-privileges:true" in probe.get("security_opt",[])
print("B62G10_REAL_REPLICATION_PRIVATE_DOCKER_TOPOLOGY",flush=True)
'
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres auth-probe
ADMIN=(psql -X -qAt -v ON_ERROR_STOP=1 -U b62g_synthetic -d b62g_synthetic)
primary_sql() { "${COMPOSE[@]}" exec -T postgres "${ADMIN[@]}" -c "$1"; }
replica_sql() { "${COMPOSE[@]}" exec -T replica "${ADMIN[@]}" -c "$1"; }
for source in b62g-revocation-prototype.sql b62g-policy-assertions.sql \
              b62g2-readonly-role.sql b62g8-login-roles.sql; do
  "${COMPOSE[@]}" exec -T postgres "${ADMIN[@]}" < "tests/security/$source"
done

primary_sql "CREATE ROLE b62g10_replication WITH LOGIN REPLICATION PASSWORD 'b62g10-replica-test-only-password';" >/dev/null
# THIS HBA rule is inside disposable isolated Docker networking, no host ports.
# It is deliberately not a production CIDR or reusable configuration.
"${COMPOSE[@]}" exec -T postgres sh -ec \
  'printf "%s\n" "host replication b62g10_replication all scram-sha-256" >> "$PGDATA/pg_hba.conf"'
primary_sql "SELECT pg_reload_conf();" >/dev/null
if ! "${COMPOSE[@]}" up -d --wait --wait-timeout 180 replica; then
  echo B62G10_STANDBY_BOOTSTRAP_FAILED_DIAGNOSTIC
  "${COMPOSE[@]}" logs --no-color --tail=80 replica || true
  exit 1
fi

test "$(primary_sql 'SELECT pg_is_in_recovery();')" = "f"
test "$(replica_sql 'SELECT pg_is_in_recovery();')" = "t"
streaming="none"
for n in $(seq 1 60); do
  streaming="$(primary_sql "SELECT COALESCE((SELECT state FROM pg_stat_replication WHERE application_name='b62g10-physical-standby' LIMIT 1),'none');")"
  [[ "$streaming" == streaming ]] && break
  sleep 0.25
done
[[ "$streaming" == streaming ]] || { echo B62G10_REAL_WAL_STREAMING_NEVER_STARTED; exit 1; }
echo B62G10_REAL_PHYSICAL_BASEBACKUP_AND_WAL_STREAMING_PASS

client() { python3 tests/security/b62g10-physical-replication-client.py --stage "$1"; }
writer() {
  "${COMPOSE[@]}" exec -T -e 'PGPASSWORD=b62g8-writer-synthetic-ci-only' \
    -e 'PGCONNECT_TIMEOUT=2' auth-probe \
    timeout 7s psql -X -qAt -w -v ON_ERROR_STOP=1 \
    -h postgres -U b62g8_emergency_writer -d b62g_synthetic -c "$1"
}
wait_replay_lsn() {
  local marker="$1" state="f"
  for n in $(seq 1 70); do
    state="$(replica_sql "SELECT COALESCE(pg_last_wal_replay_lsn() >= '$marker'::pg_lsn,false);")"
    [[ "$state" == t ]] && break
    sleep 0.2
  done
  [[ "$state" == t ]] || { echo B62G10_REPLICA_NOT_CAUGHT_UP; exit 1; }
}
wait_paused() {
  local state=""
  for n in $(seq 1 50); do
    state="$(replica_sql "SELECT pg_get_wal_replay_pause_state();")"
    [[ "$state" == paused ]] && break
    sleep 0.2
  done
  [[ "$state" == paused ]] || { echo B62G10_REPLICA_FAILED_TO_PAUSE_REPLAY; exit 1; }
}
client baseline

# Force real streaming replica lag: WAL transport is real; replay is paused.
replica_sql "SELECT pg_wal_replay_pause();" >/dev/null
writer "SELECT b62g_proof.raise_principal_epoch(42,12000);" >/dev/null
wait_paused
client paused-epoch
replica_sql "SELECT pg_wal_replay_resume();" >/dev/null
wal="$(primary_sql 'SELECT pg_current_wal_lsn();')"
wait_replay_lsn "$wal"
client replayed-epoch

# B62-G11 opts into real Docker network disconnect/rejoin faults at this
# point only. Normal B62-G10 tests retain their existing exact behavior.
if [[ "${B62G11_TEST_ONLY_FAULTS:-0}" == "1" ]]; then
  source tests/security/b62g11-partition-proof.sh
fi

replica_sql "SELECT pg_wal_replay_pause();" >/dev/null
writer "SELECT b62g_proof.block_signer('b62g-identity','key-current','compromise');" >/dev/null
wait_paused
client paused-kid
replica_sql "SELECT pg_wal_replay_resume();" >/dev/null
wal="$(primary_sql 'SELECT pg_current_wal_lsn();')"
wait_replay_lsn "$wal"
client replayed-kid

# NO automatic promotion. Standby stays readable but is NEVER authoritative.
"${COMPOSE[@]}" stop postgres
test "$(replica_sql 'SELECT pg_is_in_recovery();')" = "t"
client outage
echo B62G10_REAL_STANDBY_PRIMARY_OUTAGE_DENY_NO_PROMOTION_PASS
echo B62G10_REAL_PHYSICAL_REPLICATION_TEST_ONLY_NO_PRODUCTION_DEPLOYMENT
