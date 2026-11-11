# S0 — JWT signing secret fails closed (11 November 2026)

**Status:** DRAFT review branch. Stage 45 NO_GO, owner PC `BLOCKED_PENDING_HARDWARE`, XERJ HOLD — unchanged. Not a production-readiness claim.

## Problem

Gateway and identity-service sign and verify access tokens with one HMAC secret. If `AETHERIS_JWT_SECRET` was unset, both `application.yml` files and all three compose files fell back to `change-me-local-development-secret-32chars`, which is published in this public repository. Anyone holding it can mint a token with `role: ADMIN` and every scope, and the gateway accepts it.

The gateway unit test also used the literal `0123…cdef` fixture that Gitleaks flagged during the XERJ review (PR #361).

## Change

| Area | Before | After |
|---|---|---|
| `application.yml` (gateway, identity) | `${AETHERIS_JWT_SECRET:change-me-…}` | `${AETHERIS_JWT_SECRET:}` → empty → startup refused with a clear message |
| Compose (3 files, 6 refs) | `${AETHERIS_JWT_SECRET:-change-me-…}` | `${AETHERIS_JWT_SECRET:?…}` → `docker compose` refuses to run without it |
| `JwtSecretPolicy` (new, both services) | length ≥ 32 only | rejects missing/blank, < 32 chars, < 10 distinct characters, and any value published in this repo (matched by SHA-256, so no literal is reintroduced) |
| Gateway unit test key | committed literal | random per run (`SecureRandom`) |
| 9 workflows that start compose without a secret | relied on the default | generate a masked per-run secret with `openssl rand -hex 32` |
| Local start | `docker compose up --build` | one extra line: `echo "AETHERIS_JWT_SECRET=$(openssl rand -hex 32)" > .env` (README, `.env.example`) |

Helm already refused the old default for non-local installs; unchanged.

## Tests

- `JwtSecretPolicyTest` (gateway, identity): random accepted; null/blank, short, repetitive and both published values (incl. surrounding whitespace) rejected.
- `JwtServiceSecretTest` (identity) and `JwtAuthenticationFilterTest` (gateway): constructors refuse blank and the former default.
- `tests/test_jwt_secret_no_default.py` (in Build / release-hardening): no fallback in configs, compose uses `:?`, both services go through the policy, policies identical, every compose-starting workflow supplies a secret, `.env.example` carries no value.

## Evidence (this branch, before CI)

| Check | Result |
|---|---|
| `JwtSecretPolicy` compiled with javac 21 and exercised by a probe | 15/15 pass, including all 8 existing CI secrets still accepted |
| `tests.test_jwt_secret_no_default` | pass; on canonical code fails (4 failures, 2 errors) as intended |
| All 16 Python policy tests | pass |
| `scripts/stage22/release_gate.py` | PASS (388 files) |
| Maven unit tests, Docker runtime proofs | **not run locally** — this PR's exact-head CI is the first real run |

## Not addressed

- The old fixture and default remain in **Git history**. Treat both as compromised forever (that is why they are blocked by hash). The XERJ historical-key review still needs an owner decision; this change does not waive it.
- The 8 workflows with hard-coded `*-local-ci-secret-*` values still use them for throwaway CI stacks; they are accepted by the policy. Converting them to per-run secrets is a small follow-up.
- Remaining exposed-API fixes: self-registered users reading all users/audit, spoofable auth rate-limit key, allow-by-default gateway routes and actuator exposure.

## Rollback

Revert the single commit. Anyone who started the stack without a secret before this change should rotate: any token they issued was signed with a public key.
