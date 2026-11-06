#!/usr/bin/env bash
set -euo pipefail
: "$RUNNER_TEMP" "$GITHUB_ACTIONS" "$XERJ_COMPOSE_PROJECT"
[[ "$GITHUB_ACTIONS" == true && "$RUNNER_TEMP" == /home/runner/* ]] || exit 2
[[ "$XERJ_COMPOSE_PROJECT" =~ ^xerj-mtls-ci-[0-9]+-[0-9]+$ ]] || exit 2
compose=(docker compose -p "$XERJ_COMPOSE_PROJECT" -f tests/security/xerj-disposable-deps.compose.yml)
psql() {
  local db="$1"; shift
  "${compose[@]}" exec -T postgres psql -X -v ON_ERROR_STOP=1 -U xerj_ci -d "$db" "$@"
}
scalar() {
  local db="$1" q="$2"
  psql "$db" -t -A -c "$q" | tr -d '\r\n'
}
migrate() {
  local db="$1" file
  for file in docs/security/migrations/20261106_01_identity_account_tenant.sql \
    docs/security/migrations/20261106_02_user_record_tenant.sql \
    docs/security/migrations/20261106_03_user_transactional_outbox.sql \
    docs/security/migrations/20261106_04_user_outbox_dual_control_recovery.sql; do
    psql "$db" -f - < "$file" >/dev/null
  done
}
umask 077
pre="$(mktemp "$RUNNER_TEMP/xerj-dr-pre-XXXXXXXX.dump")"
post="$(mktemp "$RUNNER_TEMP/xerj-dr-post-XXXXXXXX.dump")"
trap 'rm -f "$pre" "$post"' EXIT
for db in xerj_dr_source xerj_dr_pre xerj_dr_restored xerj_dr_empty; do
  psql postgres -c "CREATE DATABASE $db" >/dev/null
done
psql xerj_dr_source -f - < tests/security/xerj_outbox_dr_legacy_seed.sql >/dev/null
"${compose[@]}" exec -T postgres pg_dump -U xerj_ci -d xerj_dr_source -Fc --no-owner > "$pre"
test -s "$pre"
migrate xerj_dr_source
migrate xerj_dr_source
[[ "$(scalar xerj_dr_source "SELECT (
 (SELECT count(*) FROM identity_accounts WHERE tenant_id IS NULL)=2 AND
 (SELECT count(*) FROM users WHERE tenant_id IS NULL)=2)::text")" == true ]] || exit 1
echo XERJ_DR_FOUR_MIGRATIONS_IDEMPOTENT_AND_OWNERLESS_PASS
psql xerj_dr_source -f - < tests/security/xerj_outbox_dr_recovery_seed.sql >/dev/null
"${compose[@]}" exec -T postgres pg_dump -U xerj_ci -d xerj_dr_source -Fc --no-owner > "$post"
test -s "$post"
"${compose[@]}" exec -T postgres pg_restore -U xerj_ci -d xerj_dr_restored \
  --no-owner --exit-on-error --single-transaction < "$post" >/dev/null
for db in xerj_dr_pre xerj_dr_empty; do
  "${compose[@]}" exec -T postgres pg_restore -U xerj_ci -d "$db" \
    --no-owner --exit-on-error --single-transaction < "$pre" >/dev/null
done
# Assert exact restored rows, tenant UUID, approval chain and ACL.
q="SELECT (
 (SELECT count(*) FROM identity_accounts WHERE tenant_id IS NULL)=2 AND
 (SELECT count(*) FROM users WHERE tenant_id IS NULL)=2 AND
 (SELECT count(*) FROM user_event_outbox)=2 AND
 (SELECT count(*) FROM user_outbox_replay_approvals)=1 AND
 (SELECT count(*) FROM user_outbox_replay_audit)=1 AND
 EXISTS (SELECT 1 FROM user_event_outbox WHERE
   event_id='11111111-1111-4111-8111-111111111111'
   AND state='parked' AND recovery_generation=1 AND attempts=12) AND
 EXISTS (SELECT 1 FROM user_event_outbox WHERE
   event_id='22222222-2222-4222-8222-222222222222'
   AND tenant_id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa') AND
 EXISTS (SELECT 1 FROM user_outbox_replay_audit a JOIN user_outbox_replay_approvals p
   ON a.approval_id=p.approval_id WHERE p.consumed_at IS NOT NULL) AND
 EXISTS (SELECT 1 FROM pg_indexes WHERE indexname='ix_user_event_outbox_ready') AND
 (SELECT prosecdef FROM pg_proc WHERE
   oid='public.rearm_approved_parked_user_outbox(uuid)'::regprocedure) AND
 NOT EXISTS (SELECT 1 FROM pg_proc f CROSS JOIN LATERAL aclexplode(f.proacl) a
   WHERE f.oid IN (
   'public.rearm_approved_parked_user_outbox(uuid)'::regprocedure,
   'public.approve_parked_user_outbox_replay(uuid,text)'::regprocedure)
   AND a.grantee=0 AND a.privilege_type='EXECUTE')
 )::text"
for db in xerj_dr_source xerj_dr_restored; do
  [[ "$(scalar "$db" "$q")" == true ]] || {
    echo XERJ_DR_RESTORED_SCHEMA_DATA_OR_ACL_HOLD >&2; exit 1;
  }
done
# Compare deterministic, non-public synthetic row digests, never print data.
hash_sql="SELECT md5(
 (SELECT string_agg(id||':'||email||':'||coalesce(tenant_id::text,'N'),'|' ORDER BY id) FROM users)
 ||'#'||(SELECT string_agg(id||':'||email||':'||coalesce(tenant_id::text,'N'),'|' ORDER BY id) FROM identity_accounts)
 ||'#'||(SELECT string_agg(event_id||':'||state||':'||attempts||':'||recovery_generation,'|' ORDER BY event_id) FROM user_event_outbox)
 ||'#'||(SELECT string_agg(approval_id||':'||ticket||':'||approved_by,'|' ORDER BY approval_id) FROM user_outbox_replay_approvals)
 ||'#'||(SELECT string_agg(audit_id||':'||executed_by||':'||new_generation,'|' ORDER BY audit_id) FROM user_outbox_replay_audit))"
source_hash="$(scalar xerj_dr_source "$hash_sql")"
[[ -n "$source_hash" && "$(scalar xerj_dr_restored "$hash_sql")" == "$source_hash" ]] || exit 1
echo XERJ_DR_CUSTOM_BACKUP_RESTORE_DATA_SCHEMA_ACL_PASS
if psql xerj_dr_restored -f - < tests/security/xerj_outbox_empty_only_rollback.sql \
    >/dev/null 2>&1; then
  echo XERJ_DR_UNSAFE_DOWNGRADE_ACCEPTED >&2
  exit 1
fi
[[ "$(scalar xerj_dr_restored "$q")" == true &&
   "$(scalar xerj_dr_restored "$hash_sql")" == "$source_hash" ]] || exit 1
echo XERJ_DR_POPULATED_OUTBOX_DOWNGRADE_REFUSED_WITH_EVIDENCE_PRESERVED_PASS
migrate xerj_dr_empty
psql xerj_dr_empty -f - < tests/security/xerj_outbox_empty_only_rollback.sql >/dev/null
check="SELECT (
 to_regclass('public.user_event_outbox') IS NULL AND
 to_regclass('public.user_outbox_replay_audit') IS NULL AND
 to_regclass('public.user_outbox_replay_approvals') IS NULL AND
 NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE
 table_schema='public' AND table_name IN ('users','identity_accounts')
 AND column_name='tenant_id') AND
 (SELECT count(*) FROM users)=2 AND
 (SELECT count(*) FROM identity_accounts)=2)::text"
[[ "$(scalar xerj_dr_empty "$check")" == true ]] || exit 1
[[ "$(scalar xerj_dr_pre "SELECT (
 to_regclass('public.user_event_outbox') IS NULL AND
 (SELECT count(*) FROM users)=2 AND (SELECT count(*) FROM identity_accounts)=2)::text")" == true ]] || exit 1
echo XERJ_DR_EMPTY_SCHEMA_ROLLBACK_AND_BASELINE_RESTORE_PASS
echo XERJ_DISPOSABLE_OUTBOX_DR_REHEARSAL_PASS
