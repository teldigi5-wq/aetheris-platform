#!/usr/bin/env bash
# B62-G14 disposable CI only. Never use these credentials or keys in prod.
set -eo pipefail
test "$#" -ge 1 -a "$#" -le 2 || exit 2
case "$1" in prepare|cleanup|overlap|cutover|terminate) ;; *) exit 2 ;; esac
set -u
test "$GITHUB_ACTIONS" = true
test "$B62_MTLS_CI_ONLY" = yes
test "$PGDATABASE" = b62g_synthetic
test "$PGHOST" = 127.0.0.1

name="b62-mtls-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
tls="$RUNNER_TEMP/b62-mtls-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"

# Rotating client CA trust does not rotate the server's SAN-bound identity:
# client SSLROOTCERT remains the original server CA. Only the disposable
# PostgreSQL ssl_ca_file used to validate incoming client certificates changes.
# An old already-established SSL session MUST be retired explicitly.
if [[ "$1" == overlap || "$1" == cutover ]]; then
  test "$#" -eq 1 || exit 2
  test -f "$tls/next-client-ca.crt"
  test -f "$tls/ca.crt"
  if [[ "$1" == overlap ]]; then
    cat "$tls/ca.crt" "$tls/next-client-ca.crt" > "$tls/trust-next.crt"
  else
    cp "$tls/next-client-ca.crt" "$tls/trust-next.crt"
  fi
  docker cp "$tls/trust-next.crt" "$name:/tmp/b62tls/ca.crt"
  docker exec -u root "$name" sh -c     'chown postgres:postgres /tmp/b62tls/ca.crt && chmod 644 /tmp/b62tls/ca.crt'
  docker exec "$name" psql -X -A -t -v ON_ERROR_STOP=1     -U b62g_synthetic -d b62g_synthetic     -c 'SELECT pg_reload_conf()' | grep -qx t
  sleep 2
  if [[ "$1" == overlap ]]; then
    echo B62_CLIENT_CA_DUAL_TRUST_RELOADED_WITHOUT_DB_RESTART
  else
    echo B62_OLD_CLIENT_CA_REMOVED_WITHOUT_DB_RESTART
  fi
  exit 0
fi

if [[ "$1" == terminate ]]; then
  test "$#" -eq 2 || exit 2
  [[ "$2" =~ ^[1-9][0-9]{0,8}$ ]] || exit 2
  pid="$2"
  # Do not terminate anything except the explicitly selected synthetic
  # reader session inside this run's own disposable database container.
  terminated=$(docker exec "$name" psql -X -A -t -v ON_ERROR_STOP=1     -U b62g_synthetic -d b62g_synthetic     -c "SELECT coalesce((SELECT pg_terminate_backend(pid)
          FROM pg_stat_activity WHERE pid=$pid
            AND usename='b62g8_user_reader'
            AND datname='b62g_synthetic'),false)")
  test "$terminated" = t
  echo B62_RETIRED_OLD_CLIENT_CERT_EXISTING_POSTGRES_SESSION
  exit 0
fi

if [[ "$1" == cleanup ]]; then
  docker rm -f "$name" >/dev/null 2>&1 || true
  test -z "$(docker ps -a --filter "name=^/$name$" --format '{{.Names}}')"
  if [[ -d "$tls" ]]; then
    test "$tls" = "$RUNNER_TEMP/b62-mtls-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
    rm -rf -- "$tls"
  fi
  echo B62_MUTUAL_TLS_DISPOSABLE_CONTAINER_AND_PKI_DESTROYED
  exit 0
fi

umask 077
mkdir -p "$tls"
echo "B62_MTLS_FIXTURE_DIR=$tls" >> "$GITHUB_ENV"
openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 2 \
  -subj '/CN=B62-Synthetic-CA' \
  -keyout "$tls/ca.key" -out "$tls/ca.crt" >/dev/null 2>&1
openssl req -newkey rsa:3072 -sha256 -nodes \
  -subj '/CN=localhost' \
  -keyout "$tls/server.key" -out "$tls/server.csr" >/dev/null 2>&1
printf 'basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\nsubjectAltName=DNS:localhost\n' > "$tls/server.ext"
openssl x509 -req -in "$tls/server.csr" \
  -CA "$tls/ca.crt" -CAkey "$tls/ca.key" \
  -CAcreateserial -days 2 -sha256 \
  -extfile "$tls/server.ext" -out "$tls/server.crt" >/dev/null 2>&1
printf 'basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature\nextendedKeyUsage=clientAuth\n' > "$tls/client.ext"

for role in b62g8_user_reader b62g13_emergency_writer \
            b62g13_audit_reviewer unrelated-client; do
  openssl req -newkey rsa:3072 -sha256 -nodes -subj "/CN=$role" \
    -keyout "$tls/$role.key" -out "$tls/$role.csr" >/dev/null 2>&1
  openssl x509 -req -in "$tls/$role.csr" \
    -CA "$tls/ca.crt" -CAkey "$tls/ca.key" \
    -CAcreateserial -days 2 -sha256 -extfile "$tls/client.ext" \
    -out "$tls/$role.crt" >/dev/null 2>&1
  openssl pkcs8 -topk8 -nocrypt -in "$tls/$role.key" \
    -outform DER -out "$tls/$role.pk8"
  chmod 600 "$tls/$role.pk8"
done
# Create a separate client-auth CA for the NEXT workload certificate.
# A new client must never be trusted before the explicit overlap phase.
openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 2 \
  -subj '/CN=B62-Next-Client-CA' \
  -keyout "$tls/next-client-ca.key" \
  -out "$tls/next-client-ca.crt" >/dev/null 2>&1
openssl req -newkey rsa:3072 -sha256 -nodes \
  -subj '/CN=b62g8_user_reader' \
  -keyout "$tls/b62g8_user_reader.next.key" \
  -out "$tls/b62g8_user_reader.next.csr" >/dev/null 2>&1
openssl x509 -req -in "$tls/b62g8_user_reader.next.csr" \
  -CA "$tls/next-client-ca.crt" -CAkey "$tls/next-client-ca.key" \
  -CAcreateserial -days 2 -sha256 -extfile "$tls/client.ext" \
  -out "$tls/b62g8_user_reader.next.crt" >/dev/null 2>&1
openssl pkcs8 -topk8 -nocrypt -in "$tls/b62g8_user_reader.next.key" \
  -outform DER -out "$tls/b62g8_user_reader.next.pk8"
chmod 600 "$tls/b62g8_user_reader.next.pk8"
openssl verify -CAfile "$tls/next-client-ca.crt" \
  "$tls/b62g8_user_reader.next.crt" >/dev/null

openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 2 \
  -subj '/CN=Untrusted-Synthetic-CA' \
  -keyout "$tls/bad-ca.key" -out "$tls/bad-ca.crt" >/dev/null 2>&1
openssl verify -CAfile "$tls/ca.crt" \
  "$tls/server.crt" "$tls/b62g8_user_reader.crt" \
  "$tls/b62g13_emergency_writer.crt" \
  "$tls/b62g13_audit_reviewer.crt" >/dev/null
echo B62_SYNTHETIC_CA_AND_DISTINCT_WORKLOAD_CERTIFICATES_CREATED

docker run -d --rm --name "$name" \
  -e POSTGRES_USER=b62g_synthetic \
  -e POSTGRES_DB=b62g_synthetic \
  -e POSTGRES_PASSWORD=b62g-ci-owner-synthetic-only \
  -e POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256 \
  -p 127.0.0.1:55435:5432 postgres:16 >/dev/null

ready=0
for i in $(seq 1 50); do
  if docker exec "$name" pg_isready -U b62g_synthetic \
      -d b62g_synthetic >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
test "$ready" -eq 1

for sql in \
  tests/security/b62g-revocation-prototype.sql \
  tests/security/b62g2-readonly-role.sql \
  tests/security/b62g8-login-roles.sql \
  tests/security/b62g13-function-only-writer.sql; do
  docker exec -i "$name" psql -X -q -v ON_ERROR_STOP=1 \
    -U b62g_synthetic -d b62g_synthetic < "$sql" >/dev/null
done

docker exec -u root "$name" mkdir -p /tmp/b62tls
docker cp "$tls/ca.crt" "$name:/tmp/b62tls/ca.crt"
docker cp "$tls/server.crt" "$name:/tmp/b62tls/server.crt"
docker cp "$tls/server.key" "$name:/tmp/b62tls/server.key"
docker exec -u root "$name" sh -c \
  'chown -R postgres:postgres /tmp/b62tls && chmod 600 /tmp/b62tls/server.key && chmod 644 /tmp/b62tls/ca.crt /tmp/b62tls/server.crt'

# Prepend PostgreSQL HBA: both SCRAM and matching per-role client-cert CN
# are mandatory. Never allow default host all/all to bypass clientcert.
docker exec -u root "$name" bash -ceu '
  cat >> "$PGDATA/postgresql.conf" <<EOF
ssl = on
ssl_cert_file = '\''/tmp/b62tls/server.crt'\''
ssl_key_file = '\''/tmp/b62tls/server.key'\''
ssl_ca_file = '\''/tmp/b62tls/ca.crt'\''
EOF
  hba="$PGDATA/pg_hba.conf"
  {
    printf "%s\n" "hostnossl b62g_synthetic b62g8_user_reader,b62g13_emergency_writer,b62g13_audit_reviewer 0.0.0.0/0 reject"
    printf "%s\n" "hostssl b62g_synthetic b62g8_user_reader,b62g13_emergency_writer,b62g13_audit_reviewer 0.0.0.0/0 scram-sha-256 clientcert=verify-full"
    cat "$hba"
  } > "$hba.b62"
  mv "$hba.b62" "$hba"
  chown postgres:postgres "$hba" "$PGDATA/postgresql.conf"
'
docker restart "$name" >/dev/null
ready=0
for i in $(seq 1 50); do
  if docker exec "$name" pg_isready -U b62g_synthetic \
      -d b62g_synthetic >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
test "$ready" -eq 1
docker exec "$name" psql -X -A -t -v ON_ERROR_STOP=1 \
  -U b62g_synthetic -d b62g_synthetic \
  -c 'SHOW ssl' | grep -qx on
echo B62_POSTGRES16_TLS_CA_CERTIFICATE_AND_SCRAM_CLIENTCERT_REQUIRED
