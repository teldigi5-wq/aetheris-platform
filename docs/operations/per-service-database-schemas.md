# Per-service PostgreSQL schemas (Identity, User, Audit)

## Why

All core services share one PostgreSQL database. Audit runs Flyway with
`baseline-on-migrate: false`, so it refuses a non-empty schema without Flyway history.
When Identity/User created their tables in `public` (Hibernate `ddl-auto: update`), Audit
failed on a fresh install whenever it started after them:

```
FlywayException: Found non-empty schema(s) "public" but no schema history table.
```

This was reproduced in CI by `.github/workflows/service-startup-order-proof.yml`.

## Design

| Service  | Schema     | Flyway history table             | Schema changes     |
|----------|------------|----------------------------------|--------------------|
| Identity | `identity` | `flyway_identity_schema_history` | Flyway migrations  |
| User     | `users`    | `flyway_users_schema_history`    | Flyway migrations  |
| Audit    | `public`   | `flyway_audit_schema_history`    | Flyway migrations  |

- Hibernate runs with `ddl-auto: validate` everywhere. It never changes the schema.
- Flyway stays fail-closed: `baseline-on-migrate: false`, `clean-disabled: true`, and
  `validate-on-migrate: true`.
- Each Flyway instance only inspects its own schema, so start order no longer matters.

## Fresh installs

Nothing to do. Each service creates its schema and tables on first start.

## Existing local databases (created before this change)

Old tables stay in `public`, untouched, and the new `identity` and `users` schemas start
empty. Pick **one** option.

**A. Start fresh** (deletes all local data):

```bash
docker compose down -v
docker compose up -d
```

**B. Keep local accounts and users** (copies them, then removes the old tables):

1. Start Postgres plus Identity and User once on the new version, so they create their
   schemas. Then stop the three services:
   `docker compose stop identity-service user-service audit-service`
2. Run the copy in **one transaction**. Any error, such as a user row with a NULL name
   (now NOT NULL), rolls back everything.

```sql
BEGIN;
INSERT INTO identity.identity_accounts (id, name, email, password_hash, role)
  SELECT id, name, email, password_hash, role FROM public.identity_accounts;
INSERT INTO identity.identity_refresh_tokens (id, account_id, token_hash, expires_at, revoked, created_at)
  SELECT id, account_id, token_hash, expires_at, revoked, created_at FROM public.identity_refresh_tokens;
INSERT INTO users.users (id, name, email)
  SELECT id, name, email FROM public.users;
SELECT setval(pg_get_serial_sequence('identity.identity_accounts', 'id'),
              GREATEST(COALESCE(MAX(id), 0), 1)) FROM identity.identity_accounts;
SELECT setval(pg_get_serial_sequence('identity.identity_refresh_tokens', 'id'),
              GREATEST(COALESCE(MAX(id), 0), 1)) FROM identity.identity_refresh_tokens;
SELECT setval(pg_get_serial_sequence('users.users', 'id'),
              GREATEST(COALESCE(MAX(id), 0), 1)) FROM users.users;
DROP TABLE public.identity_refresh_tokens, public.identity_accounts, public.users;
COMMIT;
```

3. Start the stack again: `docker compose up -d`.

If Audit had previously failed with the error above, `public` now holds no foreign tables,
so Audit starts and creates its own history.

## Production

Production remains NO_GO. Any real database needs a reviewed, rehearsed migration plan;
this procedure is for local development volumes only.
