# XERJ security: disposable real service boundary experiment (review only)

**Source parent:** draft #379 at `bb582f5c8d0e821bf62db63d7c3fbe910ef96481`. This child proposal does NOT edit existing Compose files, protected main, production databases, deployed applications, or XERJ pinned corpus.

## Aim and test environment
A GitHub-hosted Ubuntu 24.04 runner creates a **uniquely named disposable Compose project** containing new PostgreSQL 16, Redis 7, RabbitMQ 4 instances. Only three backing dependency ports are mapped on **127.0.0.1**. All four actual packaged Java services (Identity, User, Audit and Gateway) start directly on the same runner and bind **127.0.0.1**, with the original production-style Gateway route hosts mapped to loopback on this ephemeral hosted runner only.

Each run generates a fresh random signing key, PostgreSQL password, and RabbitMQ password; they are masked, never committed and never included in artifacts. No pre-existing volumes, named containers or external production hostnames are used. An EXIT trap stops each Java process, and a separate unconditional Actions step tears down only the explicitly named disposable Compose project and its synthetic data.

## Tests and invariants
- Healthy real Java service processes, JDBC PostgreSQL, Redis Gateway route limiter and AMQP RabbitMQ.
- A real self-registered `API_CONSUMER` signed by Identity must be denied from global User and Audit reads at both direct service and Gateway boundaries.
- Anonymous and spoofed `X-Aetheris-User-*` header requests must be denied directly by backends; invalid bearer is rejected at Gateway.
- One other **synthetic** account is elevated to `ADMIN` exclusively inside this disposable database using a fixed, unparameterized SQL statement; a real Identity login then issues the admin token for successful Gateway and backend requests.
- Real signed-admin User create/read/delete traverses Gateway, User, PostgreSQL; published `user.created` traverses RabbitMQ to Audit, persists in PostgreSQL, and is observable through the authorized Gateway route.
- Report contains only check names/status, exact CI source SHA, review-only evidence class and explicit `production_verified: false`, `tenant_isolation_verified: false` flags. Never print bearer values, secret values, raw login responses or personal data.

## Limitations — required release blockers
These are **isolated hosted integration tests**, not a deployed Docker/Kubernetes application stack. Identity and User use `SPRING_JPA_HIBERNATE_DDL_AUTO=update` ONLY for this new synthetic ephemeral database because the platform lacks a verified production-grade migration set for both tables; normal services retain the original `validate` default. This run cannot establish real migration safety, backup/restore, tenant mapping, production TLS/mTLS ingress, database least privilege, asymmetric signed JWT, network/host isolation or operational secret rotation.

The CI service container tags remain to be reviewed and pinned to digests for supply-chain reproducibility. Do not interpret a completed test as Stage 45 GO or authorization to repin/publish XERJ. PR #376 and Stage 45 stay HOLD / NO_GO.
