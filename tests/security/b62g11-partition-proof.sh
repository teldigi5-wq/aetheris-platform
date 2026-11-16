#!/usr/bin/env bash
# B62-G11 is SOURCED by G10 only when CI explicitly opts into faults.
# Requires live physical streaming and the existing synthetic private Docker
# network. Docker host controls are NOT mounted inside any application.
set -euo pipefail
: "${B62G11_TEST_ONLY_FAULTS:?CI opt-in required}"
[[ "$B62G11_TEST_ONLY_FAULTS" == "1" ]] || exit 1

NET=$("${COMPOSE[@]}" config --format json | python3 -c '
import json,sys
config=json.load(sys.stdin)
net=config["networks"]["isolated"]
assert net.get("internal") is True
print(net["name"])
')
[[ "$NET" == "aetheris-b62g-epoch-proof_isolated" ]] || {
  echo B62G11_UNEXPECTED_DOCKER_NETWORK
  exit 1
}
replica_container=$("${COMPOSE[@]}" ps -q replica)
probe_container=$("${COMPOSE[@]}" ps -q auth-probe)
[[ -n "$replica_container" && -n "$probe_container" ]] || exit 1
assert_network() {
  local container="$1" expected="$2"
  docker inspect --format '{{json .NetworkSettings.Networks}}' "$container" |
    python3 -c '
import json,sys
nets=json.load(sys.stdin)
name,expected=sys.argv[1:]
connected=name in nets
assert connected == (expected == "connected"), (connected,expected)
' "$NET" "$expected"
}
assert_network "$replica_container" connected
assert_network "$probe_container" connected
g11() { python3 tests/security/b62g11-partition-client.py --stage "$1"; }

# 42 / IAT 12001 is ACCEPTABLE before this new update (cutoff is 12000).
# The standby's WAL replay is stopped while it is a real streaming follower.
replica_sql "SELECT pg_wal_replay_pause();" >/dev/null
wait_paused
assert_network "$replica_container" connected
# Genuine severing of the standby's sole private network link. PostgreSQL
# stays running and cannot receive the newly committed WAL during this cut.
docker network disconnect "$NET" "$replica_container"
assert_network "$replica_container" disconnected
writer "SELECT b62g_proof.raise_principal_epoch(42,13000);" >/dev/null
g11 replica-isolated
echo B62G11_PHYSICAL_REPLICA_NETWORK_DISCONNECTED_DURING_COMMITTED_REVOKE

# Rejoin alone MUST NOT be treated as WAL replay freshness. Replay remains
# paused until explicitly resumed and the target WAL LSN is confirmed.
docker network connect --alias replica "$NET" "$replica_container"
assert_network "$replica_container" connected
g11 replica-rejoined-paused
replica_sql "SELECT pg_wal_replay_resume();" >/dev/null
wal="$(primary_sql 'SELECT pg_current_wal_lsn();')"
wait_replay_lsn "$wal"
g11 replica-caught-up

# Disconnect the authenticated client from *both* healthy DB nodes. Even
# when the standby could still answer ALLOW, no primary response == DENY.
docker network disconnect "$NET" "$probe_container"
assert_network "$probe_container" disconnected
g11 client-isolated
docker network connect --alias auth-probe "$NET" "$probe_container"
assert_network "$probe_container" connected
g11 client-recovered
echo B62G11_REAL_NETWORK_PARTITION_AND_RECOVERY_FAIL_CLOSED_PASS
