#!/usr/bin/env bash
# Disposable only: real Spring @RabbitListener and restricted broker identity.
# Does not mutate a developer, shared environment, or production broker.
set -euo pipefail
: "${GITHUB_RUN_ID:?}"
: "${GITHUB_RUN_ATTEMPT:?}"
: "${XERJ_COMPOSE_PROJECT:?}"
: "${RUNNER_TEMP:?}"
: "${XERJ_TEST_RABBIT_PASSWORD:?}"
: "${XERJ_TEST_PG_PASSWORD:?}"
: "${XERJ_BROKER_PUBLISH_PASSWORD:?}"
test "${GITHUB_ACTIONS:-}" = true
[[ "$GITHUB_RUN_ID" =~ ^[0-9]+$ && "$GITHUB_RUN_ATTEMPT" =~ ^[0-9]+$ ]]
test "$XERJ_COMPOSE_PROJECT" = "xerj-mtls-ci-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
[[ "$RUNNER_TEMP" == /home/runner/* ]]
[[ -f tests/security/xerj-disposable-deps.compose.yml ]]
[[ -s "$RUNNER_TEMP/xerj-ci-rs256-public.pem" ]]
umask 077

work="$(mktemp -d "$RUNNER_TEMP/xerj-live-origin.XXXXXXXX")"
chmod 700 "$work"
app_pid=""
origin_created=false
origin_user_created=false
rabbit=(docker compose -p "$XERJ_COMPOSE_PROJECT" -f tests/security/xerj-disposable-deps.compose.yml exec -T rabbitmq rabbitmqctl)
cleanup() {
  if [[ -n "$app_pid" ]]; then
    kill "$app_pid" >/dev/null 2>&1 || true
    wait "$app_pid" >/dev/null 2>&1 || true
  fi
  if [[ "$origin_created" == true ]]; then
    "${rabbit[@]}" delete_vhost xerj_origin_listener >/dev/null 2>&1 || true
  fi
  if [[ "$origin_user_created" == true ]]; then
    "${rabbit[@]}" delete_user xerj_origin_consumer >/dev/null 2>&1 || true
  fi
  rm -rf -- "$work"
}
trap cleanup EXIT

export XERJ_ORIGIN_LISTENER_VHOST=xerj_origin_listener
export XERJ_ORIGIN_PRIVATE_PEM_PATH="$work/private.pem"
export XERJ_ORIGIN_NAMES_PATH="$work/event-names.txt"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
    -out "$work/private.pem" >/dev/null 2>&1
openssl pkey -in "$work/private.pem" -pubout \
    -out "$work/public.pem" >/dev/null 2>&1
test -s "$work/public.pem"

origin_consume_password="$(openssl rand -hex 32)"
echo "::add-mask::$origin_consume_password"
"${rabbit[@]}" add_vhost xerj_origin_listener >/dev/null
origin_created=true
"${rabbit[@]}" add_user xerj_origin_consumer "$origin_consume_password" >/dev/null
origin_user_created=true
"${rabbit[@]}" set_permissions -p xerj_origin_listener xerj_ci '.*' '.*' '.*' >/dev/null
"${rabbit[@]}" set_permissions -p xerj_origin_listener xerj_publisher '^$' '^aetheris[.]events$' '^$' >/dev/null
"${rabbit[@]}" set_permissions -p xerj_origin_listener xerj_origin_consumer '^$' '^$' '^aetheris[.]audit[.]user-events$' >/dev/null
echo XERJ_REAL_LISTENER_SEPARATE_VHOST_AND_RABBIT_ACL_PASS

# A real production UserEventPublisher instance, not custom Python signing.
./mvnw -B -ntp -f user-service/pom.xml \
  -Dtest=BrokerLiveListenerOriginProducerIT test
test -s "$XERJ_ORIGIN_NAMES_PATH"

jarfile="$(find audit-service/target -maxdepth 1 -type f \
    -name '*.jar' ! -name '*.original' ! -name '*sources*' ! -name '*javadoc*' \
    | head -n 1)"
[[ -n "$jarfile" && -s "$jarfile" ]]
# The Audit JWT verifier is for HTTP request auth, not event signing;
# both trust boundaries require independently scoped public keys.
jwt_public="$(cat "$RUNNER_TEMP/xerj-ci-rs256-public.pem")"
origin_public="$(cat "$work/public.pem")"

# No broker administrator, publisher password, JWT issuer private key or
# User event signing private PEM path is inherited by the Audit application.
env -u XERJ_TEST_RABBIT_PASSWORD -u XERJ_BROKER_PUBLISH_PASSWORD \
    -u XERJ_ORIGIN_PRIVATE_PEM_PATH \
    -u AETHERIS_JWT_SECRET -u AETHERIS_JWT_RSA_PRIVATE_KEY_PEM \
    SERVER_ADDRESS=127.0.0.1 SERVER_PORT=19083 \
    SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:55432/xerj_fence_audit' \
    SPRING_DATASOURCE_USERNAME=xerj_ci \
    SPRING_DATASOURCE_PASSWORD="$XERJ_TEST_PG_PASSWORD" \
    SPRING_FLYWAY_ENABLED=false \
    SPRING_RABBITMQ_HOST=127.0.0.1 \
    SPRING_RABBITMQ_PORT=55672 \
    SPRING_RABBITMQ_VIRTUAL_HOST=xerj_origin_listener \
    SPRING_RABBITMQ_DYNAMIC=false \
    SPRING_RABBITMQ_USERNAME=xerj_origin_consumer \
    SPRING_RABBITMQ_PASSWORD="$origin_consume_password" \
    AETHERIS_AUDIT_ORIGIN_VERIFY_REQUIRED=true \
    AETHERIS_AUDIT_ORIGIN_PUBLIC_KEY_PEM="$origin_public" \
    AETHERIS_AUDIT_ORIGIN_KEY_ID=xerj-listener-ci-1 \
    AETHERIS_JWT_ALGORITHM=RS256 \
    AETHERIS_JWT_RSA_PUBLIC_KEY_PEM="$jwt_public" \
    AETHERIS_JWT_RS256_KEY_ID=xerj-ci-20261107-listener \
    AETHERIS_JWT_ISSUER=xerj-ci-review-identity \
    AETHERIS_JWT_AUDIENCE=xerj-ci-review-api \
    AETHERIS_BACKEND_GATEWAY_MTLS_REQUIRED=false \
    MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0 \
    LOGGING_LEVEL_ROOT=WARN \
    java -jar "$jarfile" > "$work/audit-application.log" 2>&1 &
app_pid="$!"

# Verify an actual Spring Rabbit listener's broker consumer registration.
# Never infer startup from only an HTTP 200 or a boot banner.
started=false
for attempt in $(seq 1 100); do
  kill -0 "$app_pid" >/dev/null 2>&1 || break
  if "${rabbit[@]}" list_consumers -p xerj_origin_listener queue_name ack_required \
       2>/dev/null | grep -F 'aetheris.audit.user-events' >/dev/null; then
    started=true
    break
  fi
  sleep 0.25
done
if [[ "$started" != true ]]; then
  echo 'XERJ_REAL_SPRING_RESTRICTED_AUDIT_CONSUMER_FAILED_TO_REGISTER' >&2
  exit 1
fi
echo XERJ_REAL_SPRING_RESTRICTED_AUDIT_CONSUMER_REGISTERED_PASS

# Separate Maven/JVM process only OBSERVES database and DLQ, no invocation
# of AuditEventStore.consume or independent fake token-verification gate.
./mvnw -B -ntp -f audit-service/pom.xml \
    -Dtest=BrokerLiveListenerOriginObserverIT test
echo XERJ_REAL_SIGNED_SPRING_LISTENER_ISOLATED_VHOST_AND_DB_PASS
