# Data Recovery Runtime Proof

Aetheris has completed its historical Stage 34/34 roadmap. This document describes a post-roadmap hosted runtime-evidence pass; it is **not Stage 35**.

## Purpose

The Data Recovery Runtime Proof turns data-durability and backup/restore claims into repeatable live evidence on GitHub-hosted Ubuntu with the real Docker Compose stack.

The proof deliberately keeps the primary proof database intact during restore validation. It restores a real PostgreSQL custom-format backup into a fresh isolated recovery database, then starts temporary Identity and User service instances against that recovered copy. This makes recovery evidence stronger than a file-exists check while avoiding unnecessary destruction of the primary CI database.

## What is proved

The workflow validates that:

- gateway, Identity Service, and User Service become healthy;
- a synthetic identity account, user record, and refresh-token hash metadata are persisted in PostgreSQL;
- those authoritative records survive a PostgreSQL container restart;
- protected reads survive Identity/User/Gateway service restarts;
- the `usersList` Redis cache can be populated, cleared, and reconstructed from PostgreSQL;
- `pg_dump` produces a non-empty PostgreSQL custom-format backup;
- the backup has a recorded SHA-256 digest and byte size while the raw backup remains inside the ephemeral CI container;
- a fresh isolated recovery database can be created and populated using `pg_restore`;
- recovered identity, user, and refresh-token metadata exactly match their pre-backup snapshots;
- a temporary Identity Service backed by the recovered database can authenticate the restored identity;
- the pre-backup refresh credential remains operational against the recovered identity database;
- a temporary User Service backed by the recovered database can read the restored user; and
- the original gateway path remains operational after isolated recovery validation.

The machine-readable contract is `build-evidence/runtime/data-recovery-runtime-contract.json`, and the live harness is `tools/data_recovery_runtime_smoke.py`.

## Evidence handling

The workflow publishes sanitized evidence under the `data-recovery-runtime-proof` artifact. It includes the JSON report, Compose state, synthetic-row counts, refresh-hash shape information, and cache-key names.

The database backup itself is intentionally **not** uploaded. It can contain application data and derived credential material, so it remains only inside the ephemeral PostgreSQL container and disappears when the workflow tears down the Compose stack and volumes. The report records only the backup format, SHA-256 digest, and byte size.

## Truth boundary

This is `HOSTED_RUNTIME` evidence for the checked GitHub-hosted Docker Compose environment. It is not:

- production disaster-recovery certification;
- point-in-time recovery or WAL-archive validation;
- encrypted, remote, or off-site backup validation;
- cross-region or multi-site disaster recovery;
- an RPO or RTO guarantee;
- Kubernetes persistent-volume / CSI recovery validation;
- production secrets, KMS, or backup-key validation;
- load, chaos, or large-dataset recovery certification; or
- physical-PC validation.

Target-PC-dependent Syntra/Aetheris work therefore remains `BLOCKED_PENDING_HARDWARE` until it is executed on the intended machine.

## Run locally

With Docker available from the repository root:

```bash
export AETHERIS_JWT_SECRET='replace-with-a-local-test-secret-at-least-32-chars'
docker compose up -d --build postgres redis rabbitmq user-service identity-service audit-service orchestrator-service gateway
python tools/data_recovery_runtime_smoke.py \
  --contract build-evidence/runtime/data-recovery-runtime-contract.json \
  --output build-evidence/runtime/data-recovery-runtime-report.json
docker compose down -v --remove-orphans
```

Use disposable development data when running recovery tests locally. The hosted workflow is the canonical automated evidence path for this pass.
