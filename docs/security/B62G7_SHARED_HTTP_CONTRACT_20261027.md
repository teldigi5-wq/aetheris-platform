# B62-G7 — Shared loopback HTTP revocation contract across real Java filters

**27 October 2026 · REVIEW-ONLY · NOT INTEGRATED · NO_GO.**

**Base:** B62-G6 PR #311 at `9f871aebcee86ab9a7a8ffcfdbdba9717faa43b1`, successful CI. This incremental slice does not change live Gateway, User or Audit production Java source. It changes only test classes, an isolated loopback-only **synthetic** HTTP decision fixture, CI and roadmap documentation.

## Purpose

B62-G5 exercised actual User and Audit servlet filter seams with mock callbacks. B62-G6 tested the genuine reactive Gateway filter with an injected `Mono<Boolean>` callback. B62-G7 verifies that all **three real Java filter classes** independently honor the **same externally served decision contract**, without faking three independent database states. It is not a durable revocation system or real end-to-end microservice deployment.

## Executable test plan

One ephemeral `ThreadingHTTPServer` binds only `127.0.0.1:15377`. The server has no production credentials, keys, private database connection, administrative mutation API, public listening port or deployable configuration. The fixture accepts only four allowlisted signed-claim query fields (UID, issuer, kid, iat) after each Java filter has **already** verified genuine ephemeral RSA-3072/JJWT signatures, issuer, kid, audience, role, scope and token lifetime. It returns exactly `ALLOW` or `DENY`; both Java servlet and Gateway test adapters treat malformed, unexpected, missing, errored or timed-out results as DENY.

The same pinned issuer/kid, synthetic principal 42 and unaffected principal 43, and fixed JWT timestamp are tested in each module. The isolated shell runner advances through four stages:
1. `baseline`: both valid principals accepted; invalid signature denied.
2. `epoch`: signed IAT on or before 2026-10-27T09:00:00Z is rejected for principal 42; principal 43 remains valid.
3. `blocked`: current signing kid blocked for both principals.
4. `outage`: the fixture process is **stopped**, so neither signer is accepted even with an otherwise valid signature.

Stages are independent synthetic fixture processes. Their restart is **not** a persisted PostgreSQL migration, certified clock, continuous atomic write or live cross-node propagation proof. Both direct servlet filters invoke an explicitly bounded loopback test client. Gateway wraps the same test call with `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` to avoid blocking the WebFlux event loop. Each module's `B62G7SharedHttpContractIT` suite runs only on explicit Maven selection; the ordinary four-module reactor regression does not require a temporary server. CI verifies exact PR SHA, source-security constraints, full Java regression and all twelve (three modules × four phases) explicit test-suite runs, then unconditionally stops the mock server process. Individual tests assert JWT signature rejection before contacting any fixture and ensure denied Gateway requests never forward verified identity headers.

## What remains — NO_GO

**NOT INTEGRATED**: Production code still constructs all three service filters with no revocation callback, so no deployed request enforces these simulated revocation decisions. No real PostgreSQL state, historical migration, restore operation, durable writer, audited emergency owner approval, read-only service identity, production JWKS key custody or tenant ownership was exercised by this new HTTP fixture. It must not be described as real multi-node service HTTP certification or production fail-closed enforcement.

Before adoption: complete approved historical database migration and recovery (#242), establish authenticated dedicated revocation reader and audited writer paths, guarantee primary **read-after-write** without stale replicas, provision resource/object-owner authorization (#251), bound reactive and servlet transport error budgets under load, establish cross-node rollout and rollback evidence, reconcile **certificate** trust release PR #309, and secure independent **owner approval**. Nine runtime gates and Stage45 `NO_GO` remain; Syntra E3.9 and E4–E9 also remain incomplete. No automatic protected-main merge or production activation is authorized.
