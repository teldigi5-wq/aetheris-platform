# Stage 11 — Owner Policy

Stage 11 preserves the owner-first, least-privilege and evidence-first rules from Stages 1–10.

## Automatic / read-only

The system may automatically:

- read workstation health and package metadata;
- read process status for explicitly named processes;
- read watcher/evidence history;
- evaluate DPAPI/speech/transport/testnet readiness without exposing secrets;
- run CI regression, packaging and security tests;
- hash owner-approved watcher content for integrity verification; and
- record CI evidence with a CI-specific source/status.

## Automatic only inside pre-approved owner scope

When the owner has already registered the scope, the system may:

- ingest verified `OWNER_FILE` watcher changes;
- create tombstones for verified delete events;
- launch an exact allowlisted application without extra CLI arguments; and
- open an existing regular file inside an approved workspace root.

These actions do not grant arbitrary filesystem, shell or process-execution authority.

## Preview / approval required

Owner approval is required before:

- installing/upgrading/removing the host agent on the physical PC;
- changing approved workspace roots or application allowlists;
- pairing a new remote device or granting new remote capabilities;
- changing a private-tunnel or certificate configuration;
- enabling a production notification provider;
- adding a new external market-data source that uses owner credentials;
- enabling an exchange testnet adapter; or
- changing a rollback/update channel.

## Strong confirmation required

Strong confirmation is required for any future action that would:

- request administrator elevation;
- change firewall/security settings;
- modify OS-wide persistence/startup beyond the approved user-level installation path;
- rotate or replace critical trust anchors/certificates;
- weaken hard trading risk limits; or
- introduce live-money execution.

Stage 11 itself does not implement a live-money path.

## Prohibited

The system must not:

- expose an unrestricted shell or generic arbitrary-process primitive through the host agent;
- bypass UAC, endpoint protection, firewall, platform security or owner policy;
- accept watcher content whose SHA-256 does not match the recorded watcher event;
- expand an owner watch root into drive root, user-home root, Windows, ProgramData or AppData-scale access;
- treat CI as proof that target DPAPI, microphone, GPU, private tunnel or external providers are working;
- return credential values through readiness APIs, consoles, task logs or normal audit payloads;
- reuse credentials previously exposed in chats/logs/docs as production/testnet credentials;
- let a model, agent or benchmark self-promote permissions or deployment state;
- send live-money exchange orders;
- enable withdrawals or transfers; or
- hide failed validation by changing evidence labels from pending/failed to verified.

## Evidence precedence

For target-dependent claims, evidence strength is ordered as:

1. target measurement/attestation collected from the relevant owner-controlled environment;
2. controlled deployment-test evidence;
3. CI/runtime software evidence; and
4. configuration/intention only.

Lower-strength evidence may prove software behavior but must never be presented as a higher-strength target claim.

## Emergency controls

`STOP ALL`, `PAUSE` and `TAKE CONTROL` remain deterministic priority controls. They must not be delayed behind model inference, provider calls, benchmarks, watcher ingestion or workstation automation.

A recovery flow must preserve owner data, audit evidence, revocation history and paper-trading evidence while disabling affected automation until the relevant trust/health gate is re-established.