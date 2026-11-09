#!/usr/bin/env bash
# Only the disposable, uniquely named PostgreSQL container in hosted CI.
set -euo pipefail
: "${RUNNER_TEMP:?Hosted CI only}"
: "${XERJ_COMPOSE_PROJECT:?Disposable Compose project required}"
[[ "$XERJ_COMPOSE_PROJECT" == xerj-mtls-ci-* ]] || exit 2
compose=(docker compose -p "$XERJ_COMPOSE_PROJECT" -f tests/security/xerj-disposable-deps.compose.yml)
psql() {
  "${compose[@]}" exec -T postgres psql -X -v ON_ERROR_STOP=1 -U xerj_ci -d xerj_ci "$@"
}
# Applying twice must not re-create/reassign existing records.
for i in 1 2; do
  psql -f - < docs/security/migrations/20261106_03_user_transactional_outbox.sql >/dev/null
done
check="$(psql -t -A -c "SELECT count(*) FROM pg_indexes WHERE tablename='user_event_outbox'
     AND indexname='ix_user_event_outbox_ready'" 2>/dev/null)"
test "$check" = "1" || { echo "XERJ_OUTBOX_SCHEMA_INDEX_MISSING" >&2; exit 1; }
echo "XERJ_DISPOSABLE_POSTGRES_OUTBOX_SCHEMA_IDEMPOTENT_PASS"
