# Dependency, License & Supply-Chain Policy

Stage 24 turns dependency management into a reviewable, reproducible process rather than a best-effort convention.

## Reproducibility rules

- Node direct dependencies and development dependencies must use exact versions. `latest`, `*`, caret, tilde, and other floating selectors are not permitted.
- `dashboard/package-lock.json` is mandatory and must be installed with `npm ci`; CI must never regenerate the graph implicitly with `npm install`.
- Python direct requirements live in `aetheris-quant/requirements.in`. The deployable graph is `requirements.lock.txt`, generated with `pip-compile --generate-hashes` and installed with `pip --require-hashes`.
- Maven is executed through the committed Maven Wrapper. The wrapper is pinned to wrapper 3.3.4 and Maven 3.9.11. Runtime dependency-tree snapshots for every Java service are committed under `build-evidence/maven/` because Maven has no native lockfile equivalent.
- CI pins the operating-system runner, Java, Node, Python, and third-party GitHub Actions. Build-container base images used by the dashboard are pinned by immutable digest.
- The build timestamp used for Maven artifacts is fixed through `.mvn/maven.config` so archive metadata does not change solely because the build was rerun later.

## Drift control

Any dependency change must be explicit in the same pull request as its regenerated lock/evidence files. CI regenerates the npm lock, Python hash lock, and Maven dependency trees and fails if `git diff` detects drift. `scripts/check_dependency_lockdown.py` also rejects floating versions, absent hashes, missing evidence, and selected prohibited license markers.

Dependabot is enabled weekly for npm, pip, Maven, and GitHub Actions. Automated update pull requests are inputs to review; they do not bypass tests, security checks, or reproducibility gates.

## Security policy

- High or critical npm advisories fail the dependency job.
- The Python hash-locked graph is audited with a pinned `pip-audit` release. Known vulnerabilities must be fixed, removed, or documented in a narrowly scoped, time-bounded exception before merge.
- Java dependency changes are reviewed through the committed dependency trees and Dependabot advisories. A vulnerability exception must identify the affected coordinate, advisory/CVE, exposure analysis, compensating control, owner, and expiry date.
- Lockfile integrity hashes are treated as supply-chain evidence, not as a substitute for vulnerability scanning.
- Security-tool failure is a failed check; it must not be silently converted into success.

## License policy

This is an engineering policy, not legal advice.

Permissive and commonly compatible licenses such as MIT, BSD-family, ISC, Apache-2.0, and similarly approved licenses are normally acceptable. New dependencies carrying AGPL, SSPL, Business Source License, Commons Clause, GPL-3.0-only, an unknown license, or a non-standard/custom license require explicit review before merge. Runtime dependencies receive stricter review than development-only tools.

The Node lock verifier blocks selected prohibited markers automatically. Python and Maven license metadata must remain visible in dependency evidence/review; an automated scanner does not override the requirement to review unusual or ambiguous terms.

## Artifact evidence

Every hardened CI run produces `build-evidence/` containing:

- SHA-256 hashes for the dependency manifests, lockfiles, Maven trees, and pinned dashboard Dockerfile;
- SHA-256 hashes for built Java JARs and a normalized dashboard archive;
- first-build and second-build hash manifests whose equality is checked by CI;
- Maven dependency-tree evidence for gateway, user-service, identity-service, and audit-service.

A hash proves identity only for the exact bytes it covers. It does not prove that an artifact is safe, bug-free, or validated on the owner's future physical PC.

## Physical-PC boundary

CI and repository evidence may prove source-level and hosted-runner reproducibility. They must not be described as successful execution, performance, thermals, GPU behavior, Docker Desktop/WSL behavior, or end-to-end validation on the owner's still-missing physical PC. Those claims remain pending until that machine exists and produces its own attested logs and hashes.
