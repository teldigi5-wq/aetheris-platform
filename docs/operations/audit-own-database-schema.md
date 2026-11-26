# Audit Service owns the `audit` PostgreSQL schema

## Why

All core services share one PostgreSQL database. Audit runs Flyway with
`baseline-on-migrate: false`, so it refuses a non-empty schema that has no Flyway history.
Identity and User create their tables in `public` (Hibernate). On a fresh install, Audit
failed whenever it started after them:

```
FlywayException: Found non-empty schema(s) "public" but no schema history table.
```

CI reproduces this deterministically: `.github/workflows/service-startup-order-proof.yml`.

## Design

- Flyway for Audit uses `schemas: audit`, `default-schema: audit` and
  `create-schemas: true`. Its non-empty safety check now inspects only Audit's own schema,
  and stays fail-closed: no baseline-on-migrate.
- Audit's connections default to `audit` through the pool setting
  `spring.datasource.hikari.schema`. A deployment-supplied `SPRING_DATASOURCE_URL` keeps
  this.
- The existing `V1__create_audit_events.sql` is **unchanged**, so its checksum is
  unchanged.
- Identity and User are unchanged. Giving them Flyway migrations is a separate step that
  needs owner approval under the Stage 45 / S0-E6M / S0-E6N governance gates.

## Existing local databases

Old Audit tables, if Audit ever started, stay in `public`, untouched. On the new version
Audit creates `audit.audit_events` and starts with an empty event list. To carry local
events over, run this once, in one transaction, while `audit-service` is stopped and after
it has created its schema at least once:

```sql
BEGIN;
INSERT INTO audit.audit_events (event_id, event_type, occurred_at, user_id, name, email, recorded_at)
  SELECT event_id, event_type, occurred_at, user_id, name, email, recorded_at FROM public.audit_events
  ON CONFLICT (event_id) DO NOTHING;
COMMIT;
```

Production remains NO_GO. Any real database needs a reviewed, rehearsed migration plan.
