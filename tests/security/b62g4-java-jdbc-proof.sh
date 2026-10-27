#!/usr/bin/env bash
# B62-G4 exact-source, CI-only real Java JDBC revocation admission proof.
# Never connect to live DBs, publish non-loopback ports, or store real keys.
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
assert set(d["services"])=={"postgres"}
assert len(d["networks"])==1 and all(x.get("internal") is True for x in d["networks"].values())
assert len(d.get("volumes",{}))==1 and not any(x.get("external") for x in d["volumes"].values())
v=d["services"]["postgres"]
assert not v.get("privileged") and not v.get("devices") and not v.get("pid")
assert len(v.get("ports",[]))==1
p=v["ports"][0]
assert p["host_ip"]=="127.0.0.1" and str(p["published"])=="15479" and p["target"]==5432
assert not any("docker.sock" in str(x) for x in v.get("volumes",[]))
assert v["environment"]["POSTGRES_DB"]=="b62g_synthetic"
assert v["environment"]["POSTGRES_USER"]=="b62g_synthetic"
assert v["environment"]["POSTGRES_PASSWORD"]=="test-only-b62g-never-use-in-production"
print("B62G4_EXACT_LOOPBACK_ONLY_EPHEMERAL_DB_TOPOLOGY")
'
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
PSQL=(psql -X -qAt -v ON_ERROR_STOP=1 -U b62g_synthetic -d b62g_synthetic)
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g-revocation-prototype.sql
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g-policy-assertions.sql
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" < tests/security/b62g2-readonly-role.sql
"${COMPOSE[@]}" exec -T postgres "${PSQL[@]}" -c \
  "SELECT b62g_proof.raise_principal_epoch(42,12000);" >/dev/null

JDBC='jdbc:postgresql://127.0.0.1:15479/b62g_synthetic?connectTimeout=2&socketTimeout=3&sslmode=disable'
run_java() {
  local mode="$1"
  ./mvnw -B -ntp -f identity-service/pom.xml \
    -Dtest=B62G4RealJdbcRevocationIT \
    "-Db62g4.jdbcUrl=${JDBC}" "-Db62g4.mode=${mode}" test
}
run_java online
"${COMPOSE[@]}" restart postgres
"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres
run_java restarted
"${COMPOSE[@]}" stop postgres
run_java outage

echo B62G4_REAL_JJWT_RS256_READONLY_JDBC_RESTART_OUTAGE_PASS
echo B62G4_REAL_PRODUCTION_VERIFIERS_UNCHANGED
