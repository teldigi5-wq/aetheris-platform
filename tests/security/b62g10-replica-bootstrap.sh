#!/bin/sh
# B62-G10 disposable physical streaming standby bootstrap. NOT for production.
set -eu
: "${PGDATA:?PGDATA must be set}"
: "${PGPASSWORD:?synthetic CI replication password required}"
mkdir -p "$PGDATA"
chown -R postgres:postgres /var/lib/postgresql/data
if [ ! -f "$PGDATA/PG_VERSION" ]; then
    # This MUST be an actual physical pg_basebackup over the private Docker
    # network, NEVER pg_dump or a second unrelated initialised database.
    su-exec postgres pg_basebackup \
        -h postgres -p 5432 -U b62g10_replication -D "$PGDATA" \
        -X stream -R --checkpoint=fast --no-password
fi
# The standby WAL receiver needs its OWN passfile after pg_basebackup exits.
printf '%s\n' 'postgres:5432:*:b62g10_replication:b62g10-replica-test-only-password' \
    > /var/lib/postgresql/.b62g10-pgpass
chown postgres:postgres /var/lib/postgresql/.b62g10-pgpass
chmod 600 /var/lib/postgresql/.b62g10-pgpass
printf "%s\n" \
    "primary_conninfo = 'host=postgres port=5432 user=b62g10_replication passfile=/var/lib/postgresql/.b62g10-pgpass application_name=b62g10-physical-standby'" \
    >> "$PGDATA/postgresql.auto.conf"
chown postgres:postgres "$PGDATA/postgresql.auto.conf"
test -f "$PGDATA/standby.signal"
echo B62G10_REAL_PG_BASEBACKUP_STANDBY_SIGNAL_PRESENT
exec su-exec postgres postgres -D "$PGDATA" -c hot_standby=on
