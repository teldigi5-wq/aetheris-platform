#!/usr/bin/env bash
# Review-only role bootstrap inside the unique hosted disposable CI database.
# Never contact another cluster or migrate production automatically.
set -euo pipefail
: "${RUNNER_TEMP:?Hosted review CI only}"
: "${XERJ_COMPOSE_PROJECT:?Unique Compose project required}"
: "${XERJ_OUTBOX_APPROVER_PASSWORD:?Ephemeral approver password required}"
: "${XERJ_OUTBOX_OPERATOR_PASSWORD:?Ephemeral operator password required}"
[[ "$XERJ_COMPOSE_PROJECT" =~ ^xerj-mtls-ci-[0-9]+-[0-9]+$ ]] || exit 2
[[ "$XERJ_OUTBOX_APPROVER_PASSWORD" != "$XERJ_OUTBOX_OPERATOR_PASSWORD" ]] || exit 2

compose=(docker compose -p "$XERJ_COMPOSE_PROJECT" \
  -f tests/security/xerj-disposable-deps.compose.yml)
psql() {
  "${compose[@]}" exec -T postgres psql -X -v ON_ERROR_STOP=1 -U xerj_ci -d xerj_ci "$@"
}
# Same manually reviewed migration is idempotent, and is applied TWICE only
# against freshly provisioned disposable synthetic Postgres.
for i in 1 2; do
  psql -f - < docs/security/migrations/20261106_04_user_outbox_dual_control_recovery.sql >/dev/null
done

# Credentials enter only through an ephemeral Docker exec environment; never
# interpolate password strings into logged scripts or commit real credentials.
"${compose[@]}" exec -T \
  -e "XERJ_OUTBOX_APPROVER_PASSWORD=$XERJ_OUTBOX_APPROVER_PASSWORD" \
  -e "XERJ_OUTBOX_OPERATOR_PASSWORD=$XERJ_OUTBOX_OPERATOR_PASSWORD" \
  postgres psql -X -v ON_ERROR_STOP=1 -U xerj_ci -d xerj_ci <<'PSQL' >/dev/null
\getenv approver_pwd XERJ_OUTBOX_APPROVER_PASSWORD
\getenv operator_pwd XERJ_OUTBOX_OPERATOR_PASSWORD
CREATE ROLE xerj_outbox_approver LOGIN INHERIT PASSWORD :'approver_pwd';
CREATE ROLE xerj_outbox_operator LOGIN INHERIT PASSWORD :'operator_pwd';
GRANT aetheris_outbox_approver TO xerj_outbox_approver;
GRANT aetheris_outbox_operator TO xerj_outbox_operator;
PSQL
# PostgreSQL catalog verifies distinct memberships and revocation of PUBLIC
# function EXECUTE, not just superficial application-level authorization.
valid="$(psql -t -A -c "
SELECT (
  pg_has_role('xerj_outbox_approver','aetheris_outbox_approver','MEMBER')
  AND NOT pg_has_role('xerj_outbox_approver','aetheris_outbox_operator','MEMBER')
  AND pg_has_role('xerj_outbox_operator','aetheris_outbox_operator','MEMBER')
  AND NOT pg_has_role('xerj_outbox_operator','aetheris_outbox_approver','MEMBER')
  AND NOT EXISTS (
    SELECT 1 FROM pg_catalog.pg_proc f
    CROSS JOIN LATERAL pg_catalog.aclexplode(f.proacl) acl
    WHERE f.oid IN (
      'public.rearm_approved_parked_user_outbox(uuid)'::regprocedure,
      'public.approve_parked_user_outbox_replay(uuid,text)'::regprocedure)
      AND acl.grantee=0 AND acl.privilege_type='EXECUTE'
  )
)::text
" 2>/dev/null)"
[[ "$valid" == "true" ]] || { echo "XERJ_OUTBOX_RECOVERY_ROLE_POLICY_HOLD" >&2; exit 1; }
echo "XERJ_OUTBOX_RECOVERY_INDEPENDENT_DB_ROLES_AND_FUNCTION_ACL_PASS"
