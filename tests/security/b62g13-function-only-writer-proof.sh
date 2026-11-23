#!/usr/bin/env bash
# B62-G13 CI ONLY: true network-authenticated function-only emergency writer.
# No production migration, credentials, operator approval or real JWT.
set -euo pipefail
cd "$(dirname "$0")/../.."
COMPOSE=(
 docker compose
 -f tests/security/docker-compose.b62g-epoch-proof.yml
 -f tests/security/b62g12-restore-overlay.yml
)
cleanup() {
 "${COMPOSE[@]}" down --volumes --remove-orphans --timeout 10 >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${COMPOSE[@]}" config --format json | python3 -c '
import sys,json
d=json.load(sys.stdin)
assert set(d["services"])=={"postgres","auth-probe"}
assert len(d["networks"])==1 and all(x.get("internal") is True for x in d["networks"].values())
assert len(d.get("volumes",{}))==1 and all(not v.get("external") for v in d["volumes"].values())
for name,svc in d["services"].items():
 assert not svc.get("ports") and not svc.get("privileged"),name
 assert not svc.get("devices") and not svc.get("network_mode"),name
 assert not any("docker.sock" in str(v) for v in svc.get("volumes",[])),name
probe=d["services"]["auth-probe"]
assert not probe.get("environment") and not probe.get("volumes")
assert "ALL" in {str(x).upper() for x in probe.get("cap_drop",[])}
assert "no-new-privileges:true" in probe.get("security_opt",[])
assert d["services"]["postgres"]["environment"]["POSTGRES_DB"]=="b62g_synthetic"
assert d["services"]["postgres"]["environment"]["POSTGRES_PASSWORD"]=="test-only-b62g-never-use-in-production"
print("B62G13_NO_HOST_PORTS_PRIVATE_DISPOSABLE_TOPOLOGY_PASS")
'

"${COMPOSE[@]}" up -d --wait --wait-timeout 120 postgres auth-probe
ADMIN=(psql -X -qAt -v ON_ERROR_STOP=1 -U b62g_synthetic -d b62g_synthetic)
for file in b62g-revocation-prototype.sql b62g-policy-assertions.sql \
    b62g2-readonly-role.sql b62g8-login-roles.sql b62g13-function-only-writer.sql; do
  "${COMPOSE[@]}" exec -T postgres "${ADMIN[@]}" < "tests/security/$file"
done

connect() {
  local role="$1" password="$2" query="$3"
  "${COMPOSE[@]}" exec -T -e "PGPASSWORD=$password" -e PGCONNECT_TIMEOUT=2 \
    auth-probe timeout 8s psql -X -qAt -w -v ON_ERROR_STOP=1 \
      -h postgres -p 5432 -U "$role" -d b62g_synthetic -c "$query"
}
writer() {
 connect b62g13_emergency_writer b62g13-writer-ci-synthetic-only "$1"
}
audit() {
 connect b62g13_audit_reviewer b62g13-auditor-ci-synthetic-only "$1"
}
expect_denied() {
  local who="$1" password="$2" sql="$3"
  if connect "$who" "$password" "$sql" >/dev/null 2>&1; then
    echo B62G13_UNAUTHORIZED_SQL_SUCCEEDED
    exit 1
  fi
}
reader_password() {
  case "$1" in
    b62g8_gateway_reader) printf %s b62g8-gateway-synthetic-ci-only ;;
    b62g8_user_reader) printf %s b62g8-user-synthetic-ci-only ;;
    b62g8_audit_reader) printf %s b62g8-audit-synthetic-ci-only ;;
    *) return 1 ;;
  esac
}
reader() {
  local role="$1" uid="$2" kid="$3" iat="$4"
  connect "$role" "$(reader_password "$role")" \
    "SELECT b62g_proof.synthetic_post_signature_allowed($uid,'b62g-identity','$kid',$iat);"
}
assert_eq() {
  [[ "$1" == "$2" ]] || {
    echo "B62G13_UNEXPECTED_POLICY_RESULT label=$3 expected=$2 actual=$1"
    exit 1
  }
}
readers=(b62g8_gateway_reader b62g8_user_reader b62g8_audit_reader)
for role in "${readers[@]}"; do
  assert_eq "$(reader "$role" 42 key-current 11000)" t baseline
  expect_denied "$role" "$(reader_password "$role")" \
    "SELECT b62g_proof.b62g13_raise_epoch(42,12000,'ci-unauthorized-001');"
  expect_denied "$role" "$(reader_password "$role")" \
    "DELETE FROM b62g_proof.b62g13_emergency_audit;"
done
expect_denied b62g13_emergency_writer invalid-test-password \
  "SELECT b62g_proof.b62g13_raise_epoch(42,12000,'ci-wrong-password-001');"
expect_denied b62g13_audit_reviewer invalid-test-password "SELECT 1;"
echo B62G13_NETWORK_PASSWORDS_AND_READER_WRITE_ISOLATION_PASS

# Synthetic emergency writer is deliberately NOT granted ANY table DML.
for sql in \
  "UPDATE b62g_proof.principal_epochs SET reject_issued_at_or_before_epoch=1 WHERE principal_id=42;" \
  "INSERT INTO b62g_proof.principal_epochs VALUES (77,1,now());" \
  "DELETE FROM b62g_proof.principal_epochs;" \
  "INSERT INTO b62g_proof.blocked_signer_kids(issuer,kid,reason_code) VALUES ('b62g-identity','forged','compromise');" \
  "UPDATE b62g_proof.blocked_signer_kids SET kid='forged' WHERE kid='key-current';" \
  "DELETE FROM b62g_proof.blocked_signer_kids;" \
  "UPDATE b62g_proof.b62g13_emergency_audit SET actor='other';" \
  "INSERT INTO b62g_proof.b62g13_emergency_audit(request_id,action,principal_id,signed_iat_cutoff,actor) VALUES('ci-forged-audit-001','raise_epoch',42,1,'b62g13_emergency_writer');" \
  "DELETE FROM b62g_proof.b62g13_emergency_audit;" \
  "CREATE TABLE b62g_proof.b62g13_unauthorized_table(id int);" \
  "SELECT * FROM b62g_proof.b62g13_emergency_audit;" \
  "SELECT b62g_proof.synthetic_post_signature_allowed(42,'b62g-identity','key-current',11000);" \
  "SELECT b62g_proof.raise_principal_epoch(42,1);" \
  "SELECT b62g_proof.block_signer('b62g-identity','key-current','compromise');"; do
  expect_denied b62g13_emergency_writer b62g13-writer-ci-synthetic-only "$sql"
done
expect_denied b62g13_audit_reviewer b62g13-auditor-ci-synthetic-only \
  "UPDATE b62g_proof.b62g13_emergency_audit SET actor='other';"
expect_denied b62g13_audit_reviewer b62g13-auditor-ci-synthetic-only \
  "SELECT * FROM b62g_proof.principal_epochs;"
echo B62G13_WRITER_FUNCTION_ONLY_TABLE_AND_AUDIT_DML_DENIED

# Both functions use SECURITY DEFINER with a NOLOGIN, NON-superuser owner;
# request IDs are unique and recorded IN SAME transaction as each update.
assert_eq "$(writer "SELECT b62g_proof.b62g13_raise_epoch(42,12000,'ci-reviewed-cutoff-001');")" 12000 cutoff
assert_eq "$(writer "SELECT b62g_proof.b62g13_raise_epoch(42,11000,'ci-reviewed-cutoff-002');")" 12000 monotonic
writer "SELECT b62g_proof.b62g13_block_signer('b62g-identity','key-current','ci-reviewed-signer-003');" >/dev/null
assert_eq "$(audit "SELECT count(*) FROM b62g_proof.b62g13_emergency_audit;")" 3 initial_events
assert_eq "$(audit "SELECT count(*) FROM b62g_proof.b62g13_emergency_audit WHERE actor='b62g13_emergency_writer';")" 3 actors
assert_eq "$(audit "SELECT count(*) FROM b62g_proof.b62g13_emergency_audit WHERE action='raise_epoch';")" 2 cutoff_events
assert_eq "$(audit "SELECT count(*) FROM b62g_proof.b62g13_emergency_audit WHERE action='block_signer';")" 1 signer_events
for role in "${readers[@]}"; do
  assert_eq "$(reader "$role" 42 key-current 12001)" f signer_revoked
  assert_eq "$(reader "$role" 42 key-safe 11000)" f epoch_revoked
  assert_eq "$(reader "$role" 42 key-safe 12001)" t newer_allowed
  assert_eq "$(reader "$role" 43 key-safe 11000)" t unaffected_allowed
done
echo B62G13_FUNCTION_ONLY_WRITER_AUDIT_AND_REVOCATION_ATOMIC_PASS

# Replaying a request ID, even on the OTHER function, must fail without a
# policy mutation or a new audit event. Invalid actor arguments likewise.
expect_denied b62g13_emergency_writer b62g13-writer-ci-synthetic-only \
  "SELECT b62g_proof.b62g13_raise_epoch(42,99999,'ci-reviewed-cutoff-001');"
expect_denied b62g13_emergency_writer b62g13-writer-ci-synthetic-only \
  "SELECT b62g_proof.b62g13_block_signer('b62g-identity','key-safe','ci-reviewed-cutoff-002');"
expect_denied b62g13_emergency_writer b62g13-writer-ci-synthetic-only \
  "SELECT b62g_proof.b62g13_raise_epoch(42,1,'unapproved');"
expect_denied b62g13_emergency_writer b62g13-writer-ci-synthetic-only \
  "SELECT b62g_proof.b62g13_raise_epoch(42,-1,'ci-negative-epoch-004');"
assert_eq "$(audit "SELECT count(*) FROM b62g_proof.b62g13_emergency_audit;")" 3 audit_replay
for role in "${readers[@]}"; do
  assert_eq "$(reader "$role" 42 key-safe 12001)" t replay_not_applied
  assert_eq "$(reader "$role" 43 key-safe 11000)" t invalid_signer_block_not_applied
done
echo B62G13_REPLAY_AND_INVALID_REQUEST_ATOMIC_ROLLBACK_PASS
echo B62G13_DISPOSABLE_ONLY_NO_OWNER_APPROVAL_NO_PRODUCTION_EMERGENCY_ENDPOINT
