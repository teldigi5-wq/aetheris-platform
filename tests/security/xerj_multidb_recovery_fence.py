#!/usr/bin/env python3
"""Fail-closed synthetic three-database restore-fence rehearsal.

Only uses a uniquely named GitHub Actions Docker Compose PostgreSQL instance.
No production connectivity, no active XERJ submission, no live restore.
This tests a quiescent application-level fence, NOT distributed atomic backup.
"""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time

COMPOSE_FILE = "tests/security/xerj-disposable-deps.compose.yml"
IDENTITY = "xerj_fence_identity"
USER = "xerj_fence_user"
AUDIT = "xerj_fence_audit"
RESTORES = {"identity": "xerj_fence_identity_r",
            "user": "xerj_fence_user_r",
            "audit": "xerj_fence_audit_r"}
MIXED = "xerj_fence_audit_mixed"
ALLOWED = {IDENTITY, USER, AUDIT, MIXED, "postgres", *RESTORES.values()}
TENANT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
EVENT_A = "55555555-5555-4555-8555-555555555555"
EVENT_B = "66666666-6666-4666-8666-666666666666"
FENCE_A = "77777777-7777-4777-8777-777777777777"
FENCE_B = "88888888-8888-4888-8888-888888888888"
LEGACY = "historical-unowned-event"

def required_env():
    run = os.environ.get("GITHUB_RUN_ID", "")
    attempt = os.environ.get("GITHUB_RUN_ATTEMPT", "")
    project = os.environ.get("XERJ_COMPOSE_PROJECT", "")
    temp = os.environ.get("RUNNER_TEMP", "")
    if (os.environ.get("GITHUB_ACTIONS") != "true" or
            not re.fullmatch(r"[0-9]+", run) or
            not re.fullmatch(r"[0-9]+", attempt) or
            project != "xerj-mtls-ci-" + run + "-" + attempt or
            not temp.startswith("/home/runner/") or
            not Path(temp).is_dir()):
        raise RuntimeError("Not the isolated GitHub Actions project")
    return project, temp

PROJECT, TEMP = required_env()
COMPOSE = ["docker", "compose", "-p", PROJECT, "-f", COMPOSE_FILE]

def invoke(args, *, data=None, stdout=None, stdin=None):
    command = [*COMPOSE, "exec", "-T", "postgres", *args]
    result = subprocess.run(command, input=data, stdin=stdin, stdout=stdout or subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False, timeout=35)
    if result.returncode != 0:
        # Never expose SQL query content, credentials, rows, or raw STDERR.
        raise RuntimeError("Isolated database command rejected")
    return result.stdout if stdout is None else b""

def sql(db, statement):
    if db not in ALLOWED:
        raise RuntimeError("Unexpected database name")
    return invoke(["psql", "-X", "-q", "-t", "-A", "-v", "ON_ERROR_STOP=1",
                   "-U", "xerj_ci", "-d", db, "-f", "-"],
                  data=statement.encode()).decode().strip()

def sql_file(db, file):
    if db not in ALLOWED or not file.startswith(("docs/security/migrations/",
                                                   "audit-service/src/main/resources/db/migration/")):
        raise RuntimeError("Unexpected migration target")
    statement = Path(file).read_text(encoding="utf-8")
    sql(db, statement)

def create(db):
    if db not in ALLOWED or db == "postgres":
        raise RuntimeError("Invalid fixture database")
    # Names are fixed constants, never supplied by a user.
    sql("postgres", "CREATE DATABASE " + db + ";")

def backup(db, file):
    if db not in ALLOWED:
        raise RuntimeError("Invalid backup target")
    with open(file, "wb") as fp:
        invoke(["pg_dump", "-U", "xerj_ci", "-d", db, "-Fc", "--no-owner"],
               stdout=fp)
    if Path(file).stat().st_size < 100:
        raise RuntimeError("Empty snapshot")

def restore(db, file):
    if db not in ALLOWED:
        raise RuntimeError("Invalid restore target")
    with open(file, "rb") as fp:
        invoke(["pg_restore", "-U", "xerj_ci", "-d", db, "--no-owner",
                "--exit-on-error", "--single-transaction"], stdin=fp)

def digest(file):
    h = hashlib.sha256()
    with open(file, "rb") as fp:
        for block in iter(lambda: fp.read(65536), b""):
            h.update(block)
    return h.digest()

def set_fence(db, token):
    sql(db, "UPDATE public.xerj_dr_fence SET token='" + token + "';")

def check(db, statement):
    return sql(db, "SELECT (" + statement + ")::text;") == "true"

def source_schema():
    for name in (IDENTITY, USER, AUDIT, *RESTORES.values(), MIXED):
        create(name)
    sql(IDENTITY, """
        CREATE TABLE public.identity_accounts (
          id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL,
          email VARCHAR(180) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
          role VARCHAR(32) NOT NULL);
        INSERT INTO public.identity_accounts VALUES
          (101,'Synthetic A','fence-a@example.test','synthetic-only','API_CONSUMER'),
          (102,'Historical account','fence-old@example.test','synthetic-only','API_CONSUMER');
    """)
    sql(USER, """
        CREATE TABLE public.users (
           id BIGINT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE);
        INSERT INTO public.users VALUES
          (201,'Synthetic A','fence-a-user@example.test'),
          (202,'Historical user','fence-old-user@example.test');
    """)
    sql_file(IDENTITY, "docs/security/migrations/20261106_01_identity_account_tenant.sql")
    for filename in (
        "docs/security/migrations/20261106_02_user_record_tenant.sql",
        "docs/security/migrations/20261106_03_user_transactional_outbox.sql",
        "docs/security/migrations/20261106_04_user_outbox_dual_control_recovery.sql"):
        sql_file(USER, filename)
    sql_file(AUDIT, "audit-service/src/main/resources/db/migration/V1__create_audit_events.sql")
    sql_file(AUDIT, "audit-service/src/main/resources/db/migration/V2__audit_event_tenant_attribution.sql")
    sql(IDENTITY, "UPDATE identity_accounts SET tenant_id='" + TENANT + "' WHERE id=101;")
    sql(USER, "UPDATE users SET tenant_id='" + TENANT + "' WHERE id=201;")
    for db in (IDENTITY, USER, AUDIT):
        sql(db, "CREATE TABLE public.xerj_dr_fence (token UUID PRIMARY KEY);")
        sql(db, "INSERT INTO public.xerj_dr_fence VALUES ('" + FENCE_A + "');")

def event_identity(user_id):
    if user_id == 201:
        return "Synthetic A", "fence-a-user@example.test"
    if user_id == 203:
        return "Synthetic B", "fence-b-user@example.test"
    raise RuntimeError("Unknown synthetic actor")

def append_outbox(event_id, event_type, user_id):
    # Synthetic event payload is copied from the corresponding persisted User.
    name, email = event_identity(user_id)
    sql(USER, """
      INSERT INTO public.user_event_outbox
        (event_id,event_type,occurred_at,user_id,name,email,
         tenant_id,state,attempts,available_at,recovery_generation)
      VALUES ('%s','%s','2026-11-06T00:00:00Z',%d,
              '%s','%s','%s',
              'delivered',1,'2026-11-06T00:00:00Z',0);
    """ % (event_id, event_type, user_id, name, email, TENANT))

def append_audit(event_id, event_type, user_id):
    name, email = event_identity(user_id)
    sql(AUDIT, """
      INSERT INTO public.audit_events
        (event_id,event_type,occurred_at,user_id,name,email,tenant_id)
      VALUES ('%s','%s','2026-11-06T00:00:00Z',%d,
              '%s','%s','%s');
    """ % (event_id, event_type, user_id, name, email, TENANT))

def snapshot_health(db_i, db_u, db_a):
    if not (check(db_i, "(SELECT COUNT(*) FROM xerj_dr_fence)=1") and
            check(db_u, "(SELECT COUNT(*) FROM xerj_dr_fence)=1") and
            check(db_a, "(SELECT COUNT(*) FROM xerj_dr_fence)=1")):
        return False
    stamps = [sql(db, "SELECT token::text FROM public.xerj_dr_fence;")
              for db in (db_i, db_u, db_a)]
    if not (stamps[0] == stamps[1] == stamps[2] == FENCE_B):
        return False
    ids = sql(db_i, "SELECT tenant_id::text FROM identity_accounts WHERE id=101;")
    uid = sql(db_u, "SELECT tenant_id::text FROM users WHERE id=201;")
    if ids != TENANT or uid != TENANT:
        return False
    # Do not certify a checkpoint with pending or in-flight delivery.
    if not check(db_u, "(SELECT COUNT(*) FROM user_event_outbox "
                       "WHERE state <> 'delivered')=0"):
        return False
    # Query from each independent database; compare event payload and tenant
    # without granting cross-db reads to application identities.
    outbox = sql(db_u, """
      SELECT event_id::text||'|'||event_type||'|'||user_id||'|'||
             coalesce(tenant_id::text,'NULL')||'|'||name||'|'||email
      FROM public.user_event_outbox ORDER BY event_id;
    """).splitlines()
    audit = sql(db_a, """
      SELECT event_id||'|'||event_type||'|'||user_id||'|'||
             coalesce(tenant_id::text,'NULL')||'|'||name||'|'||email
      FROM public.audit_events
      WHERE tenant_id IS NOT NULL ORDER BY event_id;
    """).splitlines()
    if len(outbox) != 2 or len(audit) != 2 or outbox != audit:
        return False
    # Cross-check delivered create events against the User resource, not
    # only against their mirrored Audit payloads.
    created = sql(db_u, """
      SELECT user_id::text||'|'||name||'|'||email||'|'||tenant_id::text
      FROM public.user_event_outbox WHERE event_type='user.created'
      ORDER BY user_id;
    """).splitlines()
    records = sql(db_u, """
      SELECT id::text||'|'||name||'|'||email||'|'||tenant_id::text
      FROM public.users WHERE tenant_id IS NOT NULL ORDER BY id;
    """).splitlines()
    if len(records) != 2 or created != records:
        return False
    if not check(db_u, "(SELECT COUNT(*) FROM users WHERE tenant_id IS NULL)=1"):
        return False
    if not check(db_i, "(SELECT COUNT(*) FROM identity_accounts WHERE tenant_id IS NULL)=1"):
        return False
    if not check(db_a, "(SELECT COUNT(*) FROM audit_events WHERE tenant_id IS NULL)=1"):
        return False
    return True

def main():
    source_schema()
    append_outbox(EVENT_A, "user.created", 201)
    append_audit(EVENT_A, "user.created", 201)
    sql(AUDIT, """
      INSERT INTO public.audit_events
        (event_id,event_type,occurred_at,user_id,name,email)
      VALUES ('historical-unowned-event','user.created',
              '2026-11-05T00:00:00Z',202,'Historical user','fence-old-user@example.test');
    """)
    with tempfile.TemporaryDirectory(prefix="xerj-fence-", dir=TEMP) as tmp:
        old_audit = Path(tmp, "audit-old.dump")
        backup(AUDIT, old_audit)
        # Source now advances to another fully reconciled quiescent fence.
        sql(USER, "INSERT INTO public.users VALUES "
                  "(203,'Synthetic B','fence-b-user@example.test','" + TENANT + "');")
        append_outbox(EVENT_B, "user.created", 203)
        append_audit(EVENT_B, "user.created", 203)
        for db in (IDENTITY, USER, AUDIT):
            set_fence(db, FENCE_B)
        bundle = {
            "identity": Path(tmp, "identity.dump"),
            "user": Path(tmp, "user.dump"),
            "audit": Path(tmp, "audit.dump"),
        }
        for db, name in ((IDENTITY, "identity"), (USER, "user"), (AUDIT, "audit")):
            backup(db, bundle[name])
        signed = {k: digest(v) for k, v in bundle.items()}
        if not snapshot_health(IDENTITY, USER, AUDIT):
            raise RuntimeError("Source fence inconsistent")
        for kind in ("identity", "user", "audit"):
            if digest(bundle[kind]) != signed[kind]:
                raise RuntimeError("Snapshot integrity failure")
            restore(RESTORES[kind], bundle[kind])
        if not snapshot_health(*RESTORES.values()):
            raise RuntimeError("Restored fence inconsistent")
        print("XERJ_THREE_DATABASE_QUIESCENT_FENCE_RESTORE_PASS")

        # A mixed snapshot is never accepted even if every individual
        # pg_restore succeeds. Old Audit is missing event B and has old fence.
        restore(MIXED, old_audit)
        if snapshot_health(RESTORES["identity"], RESTORES["user"], MIXED):
            raise RuntimeError("Mixed recovery points were incorrectly accepted")
        print("XERJ_MIXED_RECOVERY_POINT_FAIL_CLOSED_PASS")

        # A tenant attribution mismatch is a security failure even when
        # counts and event IDs match.
        sql(RESTORES["audit"], """
           UPDATE public.audit_events
           SET tenant_id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
           WHERE event_id='66666666-6666-4666-8666-666666666666';
        """)
        if snapshot_health(*RESTORES.values()):
            raise RuntimeError("Forged Audit tenant metadata accepted")
        print("XERJ_CROSS_DATABASE_TENANT_ATTRIBUTION_MISMATCH_DENIED_PASS")
        restore_check = digest(bundle["audit"])
        if restore_check != signed["audit"]:
            raise RuntimeError("Immutable snapshot unexpectedly changed")
        # Local file-corruption witness: data bytes cannot silently become
        # trusted solely because the dump filename matches.
        corrupt = Path(tmp, "audit-corrupt.dump")
        corrupt.write_bytes(bundle["audit"].read_bytes()[:128])
        if digest(corrupt) == signed["audit"]:
            raise RuntimeError("Corrupted dump accepted")
        print("XERJ_SNAPSHOT_DIGEST_TAMPER_DENIAL_PASS")

    print("XERJ_MULTIDB_DISPOSABLE_FENCE_RECONCILIATION_PASS")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        # Deliberately no payload, SQL statement, credentials or raw stderr.
        print("XERJ_MULTIDB_RECOVERY_HOLD " + type(exc).__name__, file=sys.stderr)
        sys.exit(1)
