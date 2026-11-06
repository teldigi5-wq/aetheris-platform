#!/usr/bin/env bash
# This script MUST run only after launching the unique disposable CI broker.
set -euo pipefail
: "${RUNNER_TEMP:?Hosted isolated runner required}"
: "${XERJ_COMPOSE_PROJECT:?Unique test Compose project required}"
: "${XERJ_TEST_RABBIT_PASSWORD:?Ephemeral admin login required}"
: "${XERJ_BROKER_PUBLISH_PASSWORD:?Ephemeral publisher required}"
: "${XERJ_BROKER_CONSUME_PASSWORD:?Ephemeral consumer required}"
[[ "$XERJ_COMPOSE_PROJECT" == xerj-mtls-ci-* ]] || exit 2
[[ "$XERJ_TEST_RABBIT_PASSWORD" != "$XERJ_BROKER_PUBLISH_PASSWORD" &&
   "$XERJ_TEST_RABBIT_PASSWORD" != "$XERJ_BROKER_CONSUME_PASSWORD" &&
   "$XERJ_BROKER_PUBLISH_PASSWORD" != "$XERJ_BROKER_CONSUME_PASSWORD" ]] || exit 2
rabbit=(docker compose -p "$XERJ_COMPOSE_PROJECT" -f tests/security/xerj-disposable-deps.compose.yml exec -T rabbitmq rabbitmqctl)
"${rabbit[@]}" add_vhost xerj_review >/dev/null
"${rabbit[@]}" add_user xerj_publisher "$XERJ_BROKER_PUBLISH_PASSWORD" >/dev/null
"${rabbit[@]}" add_user xerj_consumer "$XERJ_BROKER_CONSUME_PASSWORD" >/dev/null
# Topology bootstrap is permitted only to ephemeral CI admin.
"${rabbit[@]}" set_permissions -p xerj_review xerj_ci '.*' '.*' '.*' >/dev/null
# Publisher: may write to exactly one exchange; cannot read queues or alter topology.
"${rabbit[@]}" set_permissions -p xerj_review xerj_publisher '^$' '^aetheris\.events$' '^$' >/dev/null
# Consumer: may read exactly one queue; cannot publish or alter topology.
"${rabbit[@]}" set_permissions -p xerj_review xerj_consumer '^$' '^$' '^aetheris\.audit\.user-events$' >/dev/null
# The original root vhost remains inaccessible to both synthetic app identities.
"${rabbit[@]}" list_permissions -p xerj_review >/dev/null
echo "XERJ_RABBITMQ_SEPARATE_USER_VHOST_PERMISSIONS_CREATED"
