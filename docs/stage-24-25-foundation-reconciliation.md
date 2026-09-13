# Stage 24–25 Foundation Reconciliation

## Why this reconciliation exists

The Syntra/Aetheris AI foundation had advanced on `feature/syntra-aetheris-foundation-v2` while the initial Stage 24 reproducibility work was developed from `main`. The branches therefore diverged rather than forming one linear history.

No foundation history was rewritten or discarded. Instead, `integration/stage24-25-foundation` was created from the full foundation head and the hardening controls were ported into that branch.

## Foundation preserved

The reconciliation keeps the foundation's existing capabilities and gates, including:

- orchestrator service and agent/model/policy foundation;
- workstation-agent packaging and non-production safety checks;
- Stage 22 release gate, schema guard, SBOM, release manifest and merge-readiness evidence;
- Stage 23 compatibility baseline and contract guard;
- Aetheris Quant paper/testnet safety boundaries;
- existing service, observability and deployment code.

## Hardening layered on top

The integration adds:

- exact Node/Python/toolchain pins;
- npm and Python hash lock evidence;
- Maven Wrapper 3.3.4 / Maven 3.9.11;
- runtime dependency trees for gateway, user-service, identity-service, audit-service, orchestrator-service and workstation-agent;
- vulnerability and drift checks;
- immutable GitHub Action pins;
- two-pass reproducibility checks across all six Java build surfaces plus the normalized dashboard artifact;
- Stage 25 first-boot readiness contract covering the complete foundation.

## Merge rule

The integration branch must pass the foundation's original CI, the Stage 24 full-foundation hardening workflow and Stage 25 readiness checks before it is eligible to merge into `feature/syntra-aetheris-foundation-v2`.

It must not be merged directly to `main` as a shortcut.

## Physical-PC truth boundary

This reconciliation strengthens source/build evidence only. It does not claim successful execution of WSL2, Docker Desktop, GPU acceleration, local models, workstation-agent activation, thermals or the complete stack on the owner's still-missing physical PC.
