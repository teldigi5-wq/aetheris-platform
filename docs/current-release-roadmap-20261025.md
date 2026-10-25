# Aetheris × Syntra — Current release and development gates

**As reviewed: 25 October 2026. Documentation checkpoint only; NOT a release authorization.**

## Preserve historical stage identity

The platform's original Syntra × Aetheris repository specification reached **Stage 34 / 34 COMPLETE**. This is a historical repository blueprint result, NOT completion of an owner-device installation or all enterprise capabilities. **Do not rename the next work Stage 35.** Its documented next work is **Execution Phase A** (physical PC validation; [issue #49](https://github.com/teldigi5-wq/aetheris-platform/issues/49)). Physical validation remains **BLOCKED_PENDING_HARDWARE**.

Two independent repositories own separate sources: [aetheris-platform](https://github.com/teldigi5-wq/aetheris-platform) owns gateway, identity, user, audit, dashboard, observability, deployment and contracts; [aetheris-ai-runtime](https://github.com/teldigi5-wq/aetheris-ai-runtime) owns orchestrator, workstation agent, reasoning and quant. The runtime may advance only through cross-repository contract and certification review; do not duplicate runtime source in platform.

## Current platform release candidate lineage

| Candidate | Purpose | Verification observed | Decision |
| --- | --- | --- | --- |
| [PR #262](https://github.com/teldigi5-wq/aetheris-platform/pull/262), 5cda08d0 | Consolidated Boot 4/Cloud, Redis/Helm/security, Flyway and Jackson fixes | 29/29 relevant GitHub workflow runs succeeded on this exact head | Open draft; release NO_GO |
| [PR #263](https://github.com/teldigi5-wq/aetheris-platform/pull/263) | Restore actual Boot 4 OTLP spans | Separate tracing and observability checks succeeded on its reviewed head | Isolated stacked fix; do not merge independently with reconciled candidate |
| [PR #264](https://github.com/teldigi5-wq/aetheris-platform/pull/264), 794e97eb | Reconciles #262 plus #263 OTLP; newer candidate | 29/30 workflows succeeded in initial full run; data-recovery job failed during Docker Maven artifact fetch due upstream HTTP 502 (org.objenesis:objenesis:3.3), before demonstrating recovery result | DRAFT, NO_GO; rerun/reverify exact head, then complete blockers |

An HTTP 502 downloading a dependency is not proof of a data-loss defect or proof of successful recovery. **Never mark the gate passed until the exact CI job runs and verifies the original assertions**. Preserve tests and dependencies. If the upstream artifact host remains unstable, diagnose separately; do not disable integration tests, substitute unofficial binaries, or claim all-green based on different SHAs. Never merge the older candidate and its reconciled descendant as separate main-targeted features.

## Outstanding platform release blockers

1. [#242 — persistent database migrations](https://github.com/teldigi5-wq/aetheris-platform/issues/242): replace unsafe automatic Identity/User schema mutation; rehearse real volume backups, recovery and rollback before promotion.
2. [#251 — direct-backend authorization](https://github.com/teldigi5-wq/aetheris-platform/issues/251): verify all service/object access without depending only on the gateway and cluster ingress.
3. [#255 — Java 21 runtime images](https://github.com/teldigi5-wq/aetheris-platform/issues/255): review exact published digest and run image-based regressions; no mutable tags or guessed digests.
4. [#259 — dependency advisories](https://github.com/teldigi5-wq/aetheris-platform/issues/259): verify patched Jackson versions on combined exact head and complete SBOM/reachability review.
5. Platform operational evidence: real PostgreSQL/RabbitMQ/Redis/JWT credential rotation, network/TLS exposure, Kubernetes CNI/NetworkPolicy tests, backup/restore, container provenance, rollback and operator approval.
6. Syntra-to-runtime integration: authenticated owner identity, scoped SSE/mission feeds, durable task/evidence, independent emergency STOP and per-action authorization without privileged default execution.

## Syntra and enterprise continuation — NOT additional Stage 35 milestones

The newer runtime Enterprise E0–E9 track is separately managed: E3.9G-12B4D genuine held-out model repair is next; E3.9H [cinematic scene capability #177](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/177) needs a real browser-measured positive control and independent model benchmark. E4 model routing, E5 website factory, E6 immersive factory, E7 agent coordinators, E8 enterprise cockpit, E9 integrated certification remain unclosed macro-stages, despite supporting research assets.

The [nine runtime release gates](https://github.com/teldigi5-wq/aetheris-ai-runtime/blob/feature/syntra-cinematic-web-standards-20261025/docs/security/aetheris-nine-release-gates-20261021.md) cover integration, isolation/egress, identity, secrets/migrations, reliability, model benchmarks, cinematic studio, physical PC and authorization. They are an engineering checklist, NOT extra independent roadmap stage numbers. The separate Stage45 set of **13 unresolved safety blockers** continues to block pilot/production activation.

## Next engineering order and exit evidence

1. **Rerun and independently verify #264's failed data-recovery workflow**; use exact source/head, reproduce failure if persistent, preserve strict assertions.
2. **Reconcile platform security blockers #242/#251/#255/#259** with safe evidence and prior isolated PR lineage; never blindly merge draft ancestors.
3. **Integrate runtime security stack #178–#186 only after exact-head review**, preserving worker sandbox restrictions, role/skill default-deny, provenance and release NO_GO.
4. **Prove E3.9G-12B4D / E3.9H benchmarks** in disposable isolated browser environments, then complete model/website/3D/agent capability stages without claiming invented successes.
5. **Execute owner-device Phase A** only on approved actual hardware. Record WSL2/GPU/Ollama/voice/browser/performance/STOP findings; maintain BLOCKED_PENDING_HARDWARE until verified.
6. **Promotion requires independent integration, full security/reliability evidence, owner approval and rollback**. Production, unsigned autonomous host execution, actual customer websites and live money are NOT enabled by passing documentation CI.

## Notes for reviewers

CI success certifies only its scoped assertions. A proposed company/division is metadata, an agent roster item is not a running employee, a local/cloud model route is not an installed model, and a screenshot is not proof of an autonomous website builder. No external customer project may be modified as part of Aetheris runtime or roadmap reconciliation.
