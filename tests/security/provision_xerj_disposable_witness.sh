#!/usr/bin/env bash
# Hosted CI only: second disposable PostgreSQL16 cluster, not production authority.
set -euo pipefail
: "${RUNNER_TEMP:?}"
: "${GITHUB_ACTIONS:?}"
: "${GITHUB_RUN_ID:?}"
: "${GITHUB_RUN_ATTEMPT:?}"
: "${XERJ_COMPOSE_PROJECT:?}"
: "${XERJ_WITNESS_ADMIN_PASSWORD:?}"
: "${XERJ_WITNESS_READER_PASSWORD:?}"
[[ "$GITHUB_ACTIONS" == true &&
   "$RUNNER_TEMP" == /home/runner/* &&
   "$GITHUB_RUN_ID" =~ ^[0-9]+$ &&
   "$GITHUB_RUN_ATTEMPT" =~ ^[0-9]+$ &&
   "$XERJ_COMPOSE_PROJECT" == "xerj-mtls-ci-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}" &&
   "$XERJ_WITNESS_ADMIN_PASSWORD" != "$XERJ_WITNESS_READER_PASSWORD" ]] || exit 2
witness=(docker compose -p "${XERJ_COMPOSE_PROJECT}-witness" -f tests/security/xerj-witness-disposable.compose.yml)
"${witness[@]}" up -d --wait
# psql quotes the generated password as a literal; shell never builds SQL.
"${witness[@]}" exec -T witness psql -X -q -v ON_ERROR_STOP=1 -v "reader_password=$XERJ_WITNESS_READER_PASSWORD" -U xerj_witness_admin -d xerj_witness_ci -f - <<'SQL'
BEGIN;
CREATE TABLE public.xerj_epoch_witness (
    singleton INTEGER PRIMARY KEY CHECK (singleton=1),
    generation BIGINT NOT NULL CHECK (generation>0),
    phase TEXT NOT NULL CHECK (phase IN ('ACTIVE','FROZEN')),
    tenant_id UUID NOT NULL
);
INSERT INTO public.xerj_epoch_witness (singleton,generation,phase,tenant_id)
VALUES (1,1,'ACTIVE','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
CREATE ROLE xerj_witness_reader LOGIN PASSWORD :'reader_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
REVOKE CONNECT ON DATABASE xerj_witness_ci FROM PUBLIC;
GRANT CONNECT ON DATABASE xerj_witness_ci TO xerj_witness_reader;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO xerj_witness_reader;
REVOKE ALL ON TABLE public.xerj_epoch_witness FROM PUBLIC;
GRANT SELECT ON TABLE public.xerj_epoch_witness TO xerj_witness_reader;
COMMIT;
SQL
echo "XERJ_SECOND_POSTGRES_CLUSTER_WITNESS_READ_ONLY_PROVISIONED"
