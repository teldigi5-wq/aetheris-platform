# Stage 25 — First-Boot Readiness & Environment Contract

Date: 2026-09-13

## Objective

Prepare Aetheris/Syntra for the owner's future physical PC without pretending that the machine already exists or that hardware-dependent tests have passed.

Stage 25 is deliberately a preparation and verification-contract stage. It does not rewrite service logic, enable live-money trading, expose remote access, or claim GPU/Docker/WSL performance.

## Why this stage exists

Stage 24 proved reproducible dependency resolution and byte-for-byte hosted builds. The next risk is not another feature; it is arriving at the physical-PC phase with undocumented machine assumptions, inconsistent setup steps, or no evidence standard.

Stage 25 converts those assumptions into versioned repository contracts and an executable preflight procedure.

## Deliverables

- `configs/first-boot-contract.json` — machine/toolchain/repository expectations and the explicit physical-validation truth boundary.
- `scripts/first_boot_preflight.py` — standard-library-only verifier with separate `ci` and `host` modes.
- `tests/test_first_boot_preflight.py` — tests that ensure CI can never report the physical PC as tested.
- `docs/first-boot-runbook.md` — ordered first-day acceptance procedure for Windows/WSL2/Docker/toolchains/build/services/observability.
- `.github/workflows/stage25-readiness.yml` — hosted validation for the repository-side contract only.
- `build-evidence/readiness/` — CI-generated readiness report artifacts; these are not physical-PC evidence.

## Acceptance criteria

Stage 25 preparation is complete only when:

1. the contract loads and declares Stage 25/schema v1;
2. all required repository evidence from Stage 24 is present;
3. Java, Node, Python and Maven repository pins agree with the first-boot contract;
4. the Docker Compose core-service and local-port contracts agree with the repository;
5. unit tests pass;
6. CI-mode preflight writes `physical_pc_status: NOT_TESTED`;
7. the first-boot runbook includes stop conditions, evidence requirements, security boundaries and paper/testnet-only trading boundaries;
8. the Stage 25 GitHub Actions artifact is retained as preparation evidence;
9. no claim is made that WSL2, Docker Desktop, GPU acceleration, local AI latency, thermals, or full-stack local execution has passed on the owner's PC.

## Prepared now vs. blocked until the PC arrives

| Area | Before PC | Physical PC required |
| --- | --- | --- |
| Toolchain version contract | Prepared/tested in CI | Confirm exact host installations |
| Repository file contract | Prepared/tested in CI | Confirm clean checkout on owner PC |
| Compose service/port contract | Prepared/tested statically | Start containers and inspect health |
| WSL2 | Runbook/check defined | Actual Windows/WSL evidence |
| Docker Desktop | Runbook/check defined | Daemon and compose evidence |
| GPU/local AI | Requirements boundary defined | Driver, VRAM, model load and benchmarks |
| Reproducible build | Hosted two-pass proof from Stage 24 | Cross-machine hash comparison |
| Observability | Config already in repo + runbook | Runtime health evidence |
| Quant execution | Paper/testnet boundary retained | Local paper/testnet validation only |

## Scoring rule

Stage 25 does **not** convert Stage 24's intentionally missing physical-PC points into fake points. Repository readiness may pass 100% of its own Stage 25 preparation checks while the overall physical-PC attestation remains pending.

## First physical-PC command

After cloning the repository on the real owner machine:

```powershell
python scripts/first_boot_preflight.py --mode host --evidence-dir build-evidence\physical-pc
```

Do not run that command in a cloud VM and label the result as owner-PC evidence.

## Next stage boundary

Further pre-PC work may continue only on areas that can be tested honestly without local hardware: contracts, policy, agent/task schemas, simulations, documentation, deterministic tests and hosted CI. Hardware execution, local-model benchmarks, WSL/Docker Desktop claims, microphone/voice latency and GPU optimization remain deferred until the physical PC exists.
