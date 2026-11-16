# S0-E6F — internal single-use refresh-credential to Runtime-token exchange

**29 October 2026 — Stage45 NO_GO. Review-only, no HTTP endpoint or live token issuance.**

## Exact reviewed parent

Built on [Platform draft PR #348](https://github.com/teldigi5-wq/aetheris-platform/pull/348) exact head `51fa14a4c9a62b7cb8ccfa35d096c0954531a79f`, [exact-head Actions #40892221428](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40892221428): **25 Platform Identity + 11 Runtime Java tests passed**, source-pinned Platform RS256 token issuer ↔ actual Runtime verifier interop verified with synthetic RSA. #348 stacks on [draft PR #347](https://github.com/teldigi5-wq/aetheris-platform/pull/347), which follows the canonical Platform development line. Neither is merged or deployed. Legacy HS256 browser/Gateway login tokens remain unchanged.

## What the new application code actually does

`RuntimeAudienceSessionExchangeService` is a normal Spring `@Service` with **NO HTTP endpoint, controller, filter registration, Gateway route or startup token action**. It is **not yet reachable from any user/client**. Its only potential internal operation, `exchangeUsingSingleUseRefreshCredential`, enforces:

1. A separate operator-configured runtime-audience RSA signing key must be enabled. If not, refuse **before consuming the refresh credential**.
2. The presented credential must match the existing refresh-service's **32-byte random, unpadded base64url** format (exactly 43 ASCII characters); do not log or expose it.
3. The existing `RefreshTokenService.rotate` must find the **SHA-256-hashed** stored credential, verify expiry and not-revoked, and use `RefreshTokenRepository.revokeIfActive`'s **atomic conditional update** to claim it only once. A stale, used, expired or revoked credential refuses exchange. The same transaction issues a fresh replacement refresh credential.
4. Reload the account by its verified persistent numeric ID from `IdentityAccountRepository.findById`. Reject missing/deleted/mismatched identities. Use **current database role** for signing, never the stale role cached in the original refresh record.
5. Only then invoke `RuntimeAudienceTokenIssuer.issueForVerifiedExchange` and return the internally held RS256 token and newly rotated refresh credential as an immutable record. The outer `@Transactional(rollbackFor=Exception.class)` ensures a signing/account error can roll back the refresh rotation **when invoked through the actual Spring transaction proxy**. This is a design guarantee, not proof of production PostgreSQL transaction behavior.

The new `RuntimeAudienceSessionExchangeServiceTest` is focused on unconfigured issuer/malformed input refusal, invalid/reused/revoked credentials, nonexistent or mismatched accounts, fresh-role scope ceilings, actual refresh-service atomic-claim path exercised with a simulated repository, and transaction/exception semantics. It uses **test-only RSA keys**, fake identities and in-memory Mockito data. No live database, API call, secret or customer session is used. Previous Identity, refresh, browser, signed issuer and cross-repo pinning tests must pass in the same exact-head CI run.

## Important security limitations

**This service is NOT safe to expose over HTTP yet.** A refresh credential proves its possession, but **does not independently prove caller workload identity**, browser origin/CSRF protection, mTLS certificate binding or permission to request a Runtime token. A route that exposes the method without these independent checks could turn a stolen refresh token into a privileged service audience token. It is deliberately not wired to `AuthController`, `BrowserAuthController` or the Gateway.

**Revocation is not complete**: once signed, an RS256 Runtime token remains cryptographically valid for up to five minutes even if the underlying refresh credential is revoked. Runtime does not query an active-session or token `jti` denylist and does not enforce key revocation lifecycle yet. It does not grant tenant/project/Task/Mission ownership or SSE. Next stages need durable session/jti validation, authenticated workload exchange, key rotation, cross-instance verification, HTTP resource server and safe rollout.

**E6 still in progress:** remaining S0-E6 user-session/workload checks and E7–E11 product gates; **six planned engineering packages E6–E11 remain**, including E6. All **9 Stage45 release gates remain NOT CERTIFIED**, production NO_GO and physical owner-PC testing pending. Existing [Platform security #240](https://github.com/teldigi5-wq/aetheris-platform/issues/240) dashboard `source-map-js`/Vite high npm audit findings are independent release blockers. Runtime [issue #23](https://github.com/teldigi5-wq/aetheris-ai-runtime/issues/23) and Platform [issue #251](https://github.com/teldigi5-wq/aetheris-platform/issues/251) remain OPEN. Parent Runtime S0-E4 unconditional full API/Actuator quarantine would cause a legitimate availability outage if deployed.

No protected-main merge, production deploy, credential provisioning, new public endpoint, Task/Mission/SSE unlock, runtime pinned-head promotion, or real money/tools enabled. **Only exact-head completed CI can verify this development slice.**
