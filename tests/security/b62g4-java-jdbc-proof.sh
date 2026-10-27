#!/usr/bin/env bash
# B62-G4: actual JJWT 3072-bit RSA + JDBC on the same internal CI network.
# Zero exposed database ports; zero real keys, accounts or external URLs.
set -euo pipefail
cd "$(dirname "$0")/../.."
COMPOSE=(
 docker compose
 -f tests/security/docker-compose.b62g-epoch-proof.yml
 -f tests/security/docker-compose.b62g4-local-jdbc-overlay.yml
)
cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans --timeout 10 >/dev/null 2>&1 || true
}
trap cleanup EXIT
"${COMPOSE[@]}" config --format json | python3 -c '
import json,sys
d=json.load(sys.stdin)
assert set(d["services"])=={"postgres","java-proof"}
assert len(d["networks"])==1 and all(x.get("internal") is True for x in d["networks"].values())
assert len(d.get("volumes",{}))==1 and not any(x.get("external") for x in d["volumes"].values())
for name,v in d["services"].items():
    assert not v.get("ports") and not v.get("privileged") and not v.get("devices") and not v.get("pid"),name
    assert not any("docker.sock" in str(x) for x in v.get("volumes",[])),name
pg=d["services"]["postgres"]
assert pg["environment"]["POSTGRES_DB"]=="b62g_synthetic"
assert pg["environment"]["POSTGRES_USER"]=="b62g_synthetic"
assert pg["environment"]["POSTGRES_PASSWORD"]=="test-only-b62g-never-use-in-production"
java=d["services"]["java-proof"]
assert java.get("build")
assert str(java["build"].get("dockerfile","")).endswith("/tests/security/b62g4-Dockerfile")
assert "ALL" in {str(x).upper() for x in java.get("cap_drop",[])}, java.get("cap_drop")
assert "no-new-privileges:true" in java.get("security_opt",[])
assert java.get("entrypoint")==["sh","-ec"]
command=java.get("command")
assert isinstance(command,list) and len(command)==1
assert "jdbc:postgresql://postgres:5432/b62g_synthetic" in command[0]
assert "B62G4RealJdbcRevocationIT" in command[0]
assert command[0].strip().endswith(" test"), "Maven lifecycle goal is mandatory"
print("B62G4_ZERO_HOST_PORT_INTERNAL_JAVA_DB_TOPOLOGY")
'
# Image build uses a test-only Dockerfile matching the existing pinned
# Corretto base/Maven 3.9.11 SHA512 and prefetches JUnit before network isolation.
"${COMPOSE[@]}" build java-proof
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
PSQL=(psql -X -qAt -v ON_ERROR_STOP=1 -U b62g_synthetic -d b62g_synthetic)
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g-revocation-prototype.sql
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g-policy-assertions.sql
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g2-readonly-role.sql
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" -c \
  "SELECT b62g_proof.raise_principal_epoch(42,12000);" >/dev/null
run_java() {
  local mode="$1"
  "${COMPOSE[@]}" run --rm --no-deps -e "B62G4_MODE=${mode}" java-proof
}
run_java online
"${COMPOSE[@]}" restart postgres
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
run_java restarted
"${COMPOSE[@]}" stop postgres
run_java outage
echo B62G4_REAL_JJWT_RS256_READONLY_JDBC_RESTART_OUTAGE_PASS
echo B62G4_REAL_PRODUCTION_VERIFIERS_UNCHANGED
