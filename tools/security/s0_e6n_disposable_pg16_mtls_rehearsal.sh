#!/usr/bin/env bash
# S0-E6N: ephemeral PostgreSQL16 TLS mutual client authentication proof.
# ONLY a disposable CI container; NEVER controls existing real databases.
set -euo pipefail
umask 077
fail(){ echo S0_E6N_TLS_OR_MIGRATION_REHEARSAL_FAILED >&2; exit 1; }
eq(){ [[ "$1" == "$2" ]] || fail; }
eq "${GITHUB_ACTIONS:-}" true
eq "${AETHERIS_E6N_DISPOSABLE_MARKER:-}" ONLY_DISPOSABLE_E6N_PG16
eq "${AETHERIS_E6N_APPROVAL_SCOPE:-}" CI_ISOLATED_REVIEW_ONLY
eq "${PGHOST:-}" 127.0.0.1
eq "${PGPORT:-}" 5432
eq "${PGDATABASE:-}" aetheris_e6n_disposable
eq "${PGUSER:-}" e6n_ci_root
eq "${PGPASSWORD:-}" e6n_ci_only_password
for tool in openssl docker psql git sha256sum sed; do
  command -v "$tool" >/dev/null || fail
done

tmp="$(mktemp -d -t aetheris-e6n-pg16-tls-XXXXXXXX)"
chmod 700 "$tmp"
trap 'rm -rf -- "$tmp"' EXIT

# Require exactly ONE disposable postgres:16 Actions service image.
mapfile -t ids < <(docker ps --filter ancestor=postgres:16 --format '{{.ID}}')
eq "${#ids[@]}" 1
cid="${ids[0]}"
test -n "$cid" || fail
eq "$(docker exec "$cid" sh -c 'echo "$POSTGRES_DB"')" aetheris_e6n_disposable
eq "$(docker exec "$cid" sh -c 'echo "$POSTGRES_USER"')" e6n_ci_root
eq "$(docker exec "$cid" sh -c 'echo "$PGDATA"')" /var/lib/postgresql/data

# A throwaway root CA, server certificate and per-role client certificates.
# Keys are never saved to the repo, workflow artifacts or job output.
openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 1 \
  -keyout "$tmp/ca.key" -out "$tmp/ca.crt" \
  -subj '/CN=Aetheris E6N Synthetic CI Root' \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign' >/dev/null 2>&1
openssl req -new -newkey rsa:2048 -nodes \
  -keyout "$tmp/server.key" -out "$tmp/server.csr" \
  -subj '/CN=localhost' >/dev/null 2>&1
cat > "$tmp/server.ext" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:localhost,IP:127.0.0.1
EOF
openssl x509 -req -sha256 -days 1 -in "$tmp/server.csr" \
  -CA "$tmp/ca.crt" -CAkey "$tmp/ca.key" -CAcreateserial \
  -out "$tmp/server.crt" -extfile "$tmp/server.ext" >/dev/null 2>&1
cat > "$tmp/client.ext" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=clientAuth
EOF
for role in e6n_reader_login e6n_issuer_login e6n_revoker_login; do
  openssl req -new -newkey rsa:2048 -nodes \
    -keyout "$tmp/$role.key" -out "$tmp/$role.csr" \
    -subj "/CN=$role" >/dev/null 2>&1
  openssl x509 -req -sha256 -days 1 -in "$tmp/$role.csr" \
    -CA "$tmp/ca.crt" -CAkey "$tmp/ca.key" -CAcreateserial \
    -out "$tmp/$role.crt" -extfile "$tmp/client.ext" >/dev/null 2>&1
  chmod 600 "$tmp/$role.key"
done
chmod 600 "$tmp/ca.key" "$tmp/server.key"
openssl verify -CAfile "$tmp/ca.crt" "$tmp/server.crt" >/dev/null
for role in e6n_reader_login e6n_issuer_login e6n_revoker_login; do
  openssl verify -CAfile "$tmp/ca.crt" "$tmp/$role.crt" >/dev/null
done

# Configure TLS ONLY on the dedicated postgres:16 service container. Never
# change a host PostgreSQL installation, docker-compose, or managed database.
for name in ca.crt server.crt server.key; do
  docker cp "$tmp/$name" "$cid:/var/lib/postgresql/data/e6n_$name"
done
docker exec -u root "$cid" sh -c '
  chown postgres:postgres /var/lib/postgresql/data/e6n_*
  chmod 600 /var/lib/postgresql/data/e6n_server.key
  chmod 644 /var/lib/postgresql/data/e6n_server.crt /var/lib/postgresql/data/e6n_ca.crt
'
pg_inside(){ docker exec -u postgres "$cid" psql -X -w -v ON_ERROR_STOP=1 \
  -U e6n_ci_root -d aetheris_e6n_disposable "$@"; }
pg_inside -q -c "ALTER SYSTEM SET ssl='on'"
pg_inside -q -c "ALTER SYSTEM SET ssl_cert_file='/var/lib/postgresql/data/e6n_server.crt'"
pg_inside -q -c "ALTER SYSTEM SET ssl_key_file='/var/lib/postgresql/data/e6n_server.key'"
pg_inside -q -c "ALTER SYSTEM SET ssl_ca_file='/var/lib/postgresql/data/e6n_ca.crt'"

# HBA first match: require BOTH server TLS and a client cert with matching CN
# for the three restricted roles; explicitly reject unencrypted network TCP.
docker cp "$cid:/var/lib/postgresql/data/pg_hba.conf" "$tmp/pg_hba.original"
{
  for role in e6n_reader_login e6n_issuer_login e6n_revoker_login; do
    printf 'hostssl aetheris_e6n_disposable %s 0.0.0.0/0 cert clientname=CN\n' "$role"
    printf 'hostssl aetheris_e6n_disposable %s ::/0 cert clientname=CN\n' "$role"
  done
  printf 'hostnossl all all 0.0.0.0/0 reject\n'
  printf 'hostnossl all all ::/0 reject\n'
  cat "$tmp/pg_hba.original"
} > "$tmp/pg_hba.conf"
docker cp "$tmp/pg_hba.conf" "$cid:/var/lib/postgresql/data/pg_hba.conf"
docker exec -u root "$cid" sh -c '
  chown postgres:postgres /var/lib/postgresql/data/pg_hba.conf
  chmod 600 /var/lib/postgresql/data/pg_hba.conf
'
pg_inside -q -c "SELECT pg_reload_conf()" >/dev/null

export PGSSLMODE=verify-full
export PGSSLROOTCERT="$tmp/ca.crt"
unset PGSSLCERT PGSSLKEY
admin(){ psql -X -w -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" \
  -U "$PGUSER" -d "$PGDATABASE" "$@"; }
eq "$(admin -At -c "SHOW ssl")" on
eq "$(admin -At -c "SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()")" t
eq "$(admin -At -c "SELECT count(*) FROM pg_hba_file_rules WHERE error IS NOT NULL")" 0
eq "$(admin -At -c "SELECT current_database()")" aetheris_e6n_disposable
echo S0_E6N_PINNED_SERVER_CA_VERIFY_FULL_TLS16_PASS

# Deliberate no-TLS attempt: reject even with a correct username/password.
if PGSSLMODE=disable admin -q -c "SELECT 1" >/dev/null 2>&1; then fail; fi
# Name mismatch is rejected even when connecting to same actual IP address.
if psql -X -w -q "host=wrong.identity.invalid hostaddr=127.0.0.1 port=5432 dbname=aetheris_e6n_disposable user=e6n_ci_root sslmode=verify-full sslrootcert=$tmp/ca.crt" \
   -c "SELECT 1" >/dev/null 2>&1; then fail; fi
echo S0_E6N_PLAINTEXT_AND_INVALID_TLS_SERVER_IDENTITY_DENIED_PASS

# One transactional schema migration and version+checksum journal.
bash tools/security/s0_e6n_versioned_review_migration.sh --apply
bash tools/security/s0_e6n_versioned_review_migration.sh --verify
if bash tools/security/s0_e6n_versioned_review_migration.sh --apply \
      >/dev/null 2>&1; then fail; fi
eq "$(admin -At -c "SELECT count(*) FROM e6n_review_meta.applied_migrations")" 1
echo S0_E6N_DUPLICATE_VERSION_REPLAY_DENIED_PASS

# Create distinct NO-PASSWORD cert-authenticated scoped network identities.
admin -q <<'SQL'
BEGIN;
CREATE ROLE e6n_reader_login LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE e6n_issuer_login LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE e6n_revoker_login LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
GRANT e6j_reader TO e6n_reader_login;
GRANT e6j_issuer TO e6n_issuer_login;
GRANT e6j_revoker TO e6n_revoker_login;
COMMIT;
SQL
client(){
  local role="$1"; shift
  PGSSLCERT="$tmp/$role.crt" PGSSLKEY="$tmp/$role.key" \
   psql -X -w -q -At -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" \
   -U "$role" -d "$PGDATABASE" "$@"
}
for role in e6n_reader_login e6n_issuer_login e6n_revoker_login; do
  eq "$(client "$role" -c "SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()")" t
  eq "$(client "$role" -c "SELECT client_dn LIKE '%CN=$role%' FROM pg_stat_ssl WHERE pid=pg_backend_pid()")" t
done
if PGSSLCERT="$tmp/e6n_reader_login.crt" PGSSLKEY="$tmp/e6n_reader_login.key" \
  psql -X -w -q -At -h "$PGHOST" -p "$PGPORT" -U e6n_revoker_login \
   -d "$PGDATABASE" -c 'SELECT 1' >/dev/null 2>&1; then fail; fi
if PGSSLCERT="$tmp/no-certificate.crt" PGSSLKEY="$tmp/no-certificate.key" \
  psql -X -w -q -At -h "$PGHOST" -p "$PGPORT" -U e6n_reader_login \
   -d "$PGDATABASE" -c 'SELECT 1' >/dev/null 2>&1; then fail; fi
if PGSSLMODE=disable client e6n_reader_login -c 'SELECT 1' \
    >/dev/null 2>&1; then fail; fi
echo S0_E6N_THREE_DISTINCT_TLS_CLIENT_CERT_IDENTITIES_AND_NEGATIVES_PASS

# Verifier and issuer have function-only DB privileges even over mTLS.
eq "$(admin -At -c "SELECT has_table_privilege('e6n_reader_login','e6j_runtime_auth.session_registrations','SELECT')")" f
eq "$(admin -At -c "SELECT has_table_privilege('e6n_issuer_login','e6j_runtime_auth.session_registrations','INSERT')")" f
eq "$(admin -At -c "SELECT has_table_privilege('e6n_revoker_login','e6j_runtime_auth.session_registrations','UPDATE')")" f
if client e6n_reader_login -c "SELECT * FROM e6j_runtime_auth.session_registrations" \
    >/dev/null 2>&1; then fail; fi
if client e6n_reader_login -c "SELECT e6j_runtime_auth.enroll_signed_session('a','b','c',1,1,2,gen_random_uuid())" \
    >/dev/null 2>&1; then fail; fi
echo S0_E6N_MTLS_READER_TABLE_AND_ISSUER_AUTHORITY_DENIED_PASS

iss='https://identity.aetheris.example.invalid'
kid='e6n_ci_issuer_key'
jti='e6n_test_real_certificate_session_6b91f3'
iat="$(($(date +%s)-3))"
exp="$((iat+180))"
admin -q -v e6niss="$iss" -v e6nkid="$kid" <<'SQL'
INSERT INTO e6j_runtime_auth.signer_key_states(issuer,kid,state)
VALUES(:'e6niss',:'e6nkid','ACTIVE');
INSERT INTO e6j_runtime_auth.principal_epochs(
 principal_id,reject_issued_at_or_before_epoch) VALUES(42,0);
SQL
check="SELECT e6j_runtime_auth.check_signed_session('$iss','$kid','$jti',42,$iat)"
eq "$(client e6n_reader_login -c "$check")" f
eq "$(client e6n_issuer_login -c "SELECT e6j_runtime_auth.enroll_signed_session('$iss','$kid','$jti',42,$iat,$exp,gen_random_uuid())")" t
eq "$(client e6n_reader_login -c "$check")" t
eq "$(client e6n_revoker_login -c "SELECT e6j_runtime_auth.revoke_signed_session('$iss','$kid','$jti',gen_random_uuid())")" t
eq "$(client e6n_reader_login -c "$check")" f
eq "$(admin -At -c 'SELECT count(*) FROM e6j_runtime_auth.authority_audit')" 2
echo S0_E6N_MTLS_ISSUE_READ_REVOKE_FUNCTION_ONLY_LIFECYCLE_PASS

# The runner's PostgreSQL service container is destroyed by Actions. The
# review schema/roles are also explicitly removed for a verifiable zero-state.
admin -q <<'SQL'
DROP SCHEMA e6j_runtime_auth CASCADE;
DROP SCHEMA e6n_review_meta CASCADE;
DROP ROLE e6n_reader_login;
DROP ROLE e6n_issuer_login;
DROP ROLE e6n_revoker_login;
DROP ROLE e6j_reader;
DROP ROLE e6j_issuer;
DROP ROLE e6j_revoker;
DROP ROLE e6j_outsider;
DROP ROLE e6j_exec_owner;
SQL
eq "$(admin -At -c "SELECT count(*) FROM pg_namespace WHERE nspname IN ('e6j_runtime_auth','e6n_review_meta')")" 0
eq "$(admin -At -c "SELECT count(*) FROM pg_roles WHERE rolname LIKE 'e6n_%_login'")" 0
echo S0_E6N_NO_PROD_MIGRATION_MTLS_AND_JOURNAL_REHEARSAL_CLEANUP_PASS
