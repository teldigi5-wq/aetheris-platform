# Stage 24 — Reproducible Build & Dependency Lockdown

Date: 2026-09-13

## Result

**Stage 24 hardening reassessment: 98/100.**

This is a Stage 24 evidence-based reassessment of the repository's current hardening posture; it does not rewrite or invent the exact historical Stage 22 score. The remaining 2 points are deliberately withheld for validation that requires the owner's still-missing physical PC. Hosted CI is not treated as proof of local Windows/WSL/Docker/GPU/runtime behavior.

## What Stage 24 now proves

### Node / dashboard

- Direct runtime and development dependencies use exact versions.
- Node is pinned to `22.23.2` in both `package.json` engine metadata and `.nvmrc`.
- `dashboard/package-lock.json` is committed using lockfile format v3 with integrity hashes.
- CI installs with `npm ci`, never an implicit dependency re-resolution.
- CI regenerates the lockfile and fails if `git diff` detects dependency drift.
- `npm audit --audit-level=high` passed in the final validation run.
- Dashboard container build stages are pinned by immutable image digests.

### Python / Aetheris Quant

- Python is pinned to `3.13.15`.
- Direct dependencies are exact-pinned in `requirements.in`.
- `requirements.lock.txt` is generated with `pip-compile --generate-hashes` and contains transitive SHA-256 hashes.
- CI installs the deployable graph with `pip --require-hashes`.
- CI regenerates the Python lock and fails on drift.
- The first Stage 24 audit correctly discovered vulnerable `python-dotenv 1.1.1` and transitive `starlette 0.47.3`.
- They were remediated without an ignore/allowlist by moving to `python-dotenv 1.2.3`, `FastAPI 0.141.1`, explicitly pinned `Starlette 1.6.0`, and `Uvicorn 0.52.4`.
- `pip-audit` passed after the remediation.

### Maven / Java

- Java is pinned to `21.0.12`.
- Maven Wrapper is committed and pinned to wrapper `3.3.4` and Maven `3.9.11`.
- Maven runs in batch mode with strict checksum verification and a fixed `project.build.outputTimestamp`.
- Runtime dependency trees for gateway, user-service, identity-service, and audit-service are committed under `build-evidence/maven/`.
- CI regenerates all four runtime trees and fails on drift.
- Floating `LATEST`, `RELEASE`, and Maven version ranges are rejected by the Stage 24 verifier.

### CI and supply-chain controls

- The runner is fixed to `ubuntu-24.04` rather than `ubuntu-latest`.
- Critical GitHub Actions are pinned to immutable commit SHAs, using current supported majors:
  - `actions/checkout` v6
  - `actions/setup-node` v6
  - `actions/setup-python` v6
  - `actions/setup-java` v5
  - `actions/upload-artifact` v5
- Dependabot is configured for npm, pip, each Maven service, and GitHub Actions.
- `scripts/check_dependency_lockdown.py` rejects missing evidence, floating direct Node/Python versions, missing Python hashes, Maven version ranges, toolchain-pin drift, and selected prohibited license markers.
- `docs/dependency-policy.md` defines dependency, security, license, exception, drift, artifact, and physical-PC boundaries.

## Reproducibility proof

GitHub Actions run `34771630037` built the Java services and dashboard twice from a clean generated-artifact state. Pass 1 and pass 2 SHA-256 manifests were compared with `diff`; the comparison passed.

| Artifact | SHA-256 |
| --- | --- |
| audit-service JAR | `580d5560b4e017696beb1ecca392d1dcd99e9db35439b8b1079c85c6ef1b8585` |
| gateway JAR | `3bc0a4cbd7909574cafc577f2669ac629e5bdf3dd0435bb475a0d14046f6e42f` |
| identity-service JAR | `52e058a85daf34967fe5e14f90e0352db4a17980aaea0df547a6e629be506712` |
| user-service JAR | `3656a9eea5b7074f3e5d2321459176ecfb0efc0e35204c25154f6641b767b2e0` |
| normalized dashboard archive | `c8ec3dc495a0774dc70e38f6b966ad7ad1c98f466fbe50a37d3ce8d79bd302e7` |

The workflow uploaded two evidence bundles. GitHub recorded these bundle digests:

- `dependency-lock-evidence`: `sha256:83431bbf468ddb7b3262faeed38f2ebcabc7b35b0a8059a82e615ff72e339304`
- `reproducible-build-evidence`: `sha256:e68da580a7457021b6e29e1c716efd746614db5a19550c85c46cea6324c2bb89`

Selected committed/input evidence hashes from the run:

- `dashboard/package-lock.json`: `876686421c4f788a3a27c7b99a7ed4d1a36714b0976ae4c6afcf13dd444e94e9`
- `aetheris-quant/requirements.lock.txt`: `43b7c31a216fa695434dffcce03d5169bbc911f8893a97c5f6e5fc404c00e947`
- Maven wrapper properties: `8f2a4438810348270d9d98f8d6d530541ddb7271e373db207e8ac381e0e35b62`

## Score rubric

| Area | Score |
| --- | ---: |
| Dependency graph + lock evidence | 20/20 |
| Toolchain and build pinning | 15/15 |
| Dependency-drift detection | 15/15 |
| Two-pass reproducible artifact proof | 20/20 |
| Security + dependency/license policy | 15/15 |
| CI evidence and retained hash artifacts | 13/13 |
| Physical-PC attestation | **0/2** |
| **Total** | **98/100** |

## Why this is not 100/100

The repository now has hosted-runner proof for deterministic dependency resolution and repeatable build bytes. It still does **not** have honest evidence from the owner's physical machine for Windows host behavior, BIOS/virtualization state, WSL2, Docker Desktop, local filesystem differences, local networking, GPU/driver behavior, sustained operation, or machine-specific performance.

Those final 2 points must remain unearned until the physical PC exists and produces its own attested environment report, build logs, dependency evidence, and matching artifact hashes. Stage 24 intentionally does not fabricate that evidence.
