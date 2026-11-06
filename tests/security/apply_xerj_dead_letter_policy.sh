#!/usr/bin/env bash
# Only on a fresh, uniquely namespaced disposable RabbitMQ in hosted CI.
# Live broker queues are NEVER purged, removed, or redeclared by this script.
set -euo pipefail
: "${RUNNER_TEMP:?Hosted runner only}"
: "${XERJ_COMPOSE_PROJECT:?Unique disposable test Compose project required}"
[[ "$XERJ_COMPOSE_PROJECT" == xerj-mtls-ci-* ]] || exit 2
rabbit=(docker compose -p "$XERJ_COMPOSE_PROJECT" \
  -f tests/security/xerj-disposable-deps.compose.yml exec -T rabbitmq rabbitmqctl)
# A policy changes dead-letter routing without mutating the durable queue's
# declaration arguments or requiring a destructive RabbitMQ redeclare.
"${rabbit[@]}" set_policy -p xerj_review xerj-ci-audit-poison \
  '^aetheris\.audit\.user-events$' \
  '{"dead-letter-exchange":"aetheris.audit.dlx","dead-letter-routing-key":"quarantine"}' \
  --apply-to queues --priority 9 >/dev/null
policies="$("${rabbit[@]}" list_policies -p xerj_review --no-table-headers)"
[[ "$policies" == *"xerj-ci-audit-poison"* &&
   "$policies" == *"aetheris.audit.dlx"* &&
   "$policies" == *"quarantine"* ]] || {
  echo "XERJ_DLX_POLICY_NOT_APPLIED" >&2; exit 1;
}
echo "XERJ_NONDESTRUCTIVE_AUDIT_DLX_POLICY_ATTACHED_PASS"
