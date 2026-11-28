# Security case studies

Four real bugs found in this platform in November 2026. Each was handled the same way:

1. Reproduce it with a test that **fails** on the unfixed code.
2. Fix it.
3. Keep the test as a permanent regression guard.

The links point to the pull requests with the CI evidence.

---

## 1. Cross-user data access through a trusted header (IDOR)

**PR:** [#445](https://github.com/teldigi5-wq/aetheris-platform/pull/445) · **Severity:** high (authenticated users)

**What was wrong.** The AI runtime's memory API decides whose records a request may read
or change from the `X-Aetheris-Owner-Id` header. The gateway removed the other identity
headers (`X-Aetheris-User-Id`, role, scopes) from incoming requests, but not this one. Any
logged-in user with orchestrator access could set the header to another user's ID and
read, edit or delete that user's memory records.

**How it was found.** Reviewing an old, unmerged PR that touched the same area, then
tracing the header from the runtime controller back through the gateway.

**Fix.** The gateway treats the header as an identity header:
- it is stripped from every request, including public routes
- on memory routes it is set only from the verified JWT user ID
- a token without a numeric user ID is rejected with 401

**Proof.** Three gateway tests (23 → 26) cover a spoofed owner being replaced, the header
never reaching other routes, and rejection without a user ID. A static guard test stops
the header from being dropped again.

**Lesson.** Any header a downstream service *trusts* is an authorization input. The edge
must strip it from clients and derive it from verified identity, and so must every
service the header passes through.

---

## 2. Authentication bypass with an encoded URL

**PR:** [#449](https://github.com/teldigi5-wq/aetheris-platform/pull/449) · **Severity:** high (service ports)

**What was wrong.** The User and Audit services each verify JWTs themselves, as a second
layer behind the gateway. To decide which requests needed that check, the filter looked at
the **raw** request path for an `/api` prefix. The servlet container and Spring MVC
normalise paths before choosing a controller: they decode `%61` to `a` and strip
`;matrix` parameters. So the filter and the controller disagreed about the path.

**Proof (test written before the fix).** On the unfixed code, a request with **no token**
to `GET /%61pi/users` returned **HTTP 200 with the user list**.

**Fix.** The filter now fails closed. It skips authentication only for *plain* paths:
- no `%`, `;`, `//`, `\` or dot segments
- not the API root in any representation (raw URI, servlet path, path info),
  compared case-insensitively

Everything else requires a valid token. This matches the gateway's model, which was not
affected because it rejects ambiguous paths outright.

**Proof after the fix.** A real-HTTP test against an embedded Tomcat covers eight path
spellings, including `/%61pi/`, `/api;v=1/`, `;jsessionid` and `//api/`. All are denied,
and the business service is never called.

**Lesson.** When a security check and request routing parse the same input differently,
attackers live in the gap. Decide on the *normalised* form, or fail closed on anything
that isn't plainly safe.

---

## 3. Audit service crash depending on container start order

**PRs:** [#452](https://github.com/teldigi5-wq/aetheris-platform/pull/452), [#454](https://github.com/teldigi5-wq/aetheris-platform/pull/454) · **Severity:** availability

**What was wrong.** All services share one PostgreSQL database. Audit manages its schema
with Flyway with `baseline-on-migrate: false`, a safety setting that refuses to touch a
non-empty schema with no Flyway history. Identity and User create their tables in the
same `public` schema at startup. If their containers started first, Audit crashed on boot,
and on every restart after that:

```
FlywayException: Found non-empty schema(s) "public" but no schema history table.
```

The normal end-to-end CI usually passed only because Audit usually won the race.

**Proof (test written before the fix).** A new CI workflow forces the bad order on a fresh
database (Identity and User first, then Audit) and requires all three to be healthy and
to survive a restart. On the unfixed code Audit crashed with the error above.

**Fix.** Audit owns its own `audit` schema. Its Flyway safety check now looks only there,
and its database connections default to it. The safety setting stays strict, and the
existing migration file is untouched, so its checksum is unchanged.

**Lesson.** "Works in CI" can be a race you keep winning. Force the worst ordering on
purpose. Also prefer one schema per service: shared schemas couple services in ways
nobody sees until startup.

---

## 4. One malformed message could stall the audit log

**PR:** [#460](https://github.com/teldigi5-wq/aetheris-platform/pull/460) · **Severity:** availability and integrity

**What was wrong.** Audit consumes User events from RabbitMQ.
- **Poison messages:** a malformed message (bad timestamp, missing user ID) threw a parse
  exception, which Spring AMQP treats as retryable by default. The broker redelivered the
  same message forever, and events behind it stopped being recorded.
- **Bad data:** missing fields were stored as the literal text `"null"`.
- **Silent drops:** `INSERT … ON CONFLICT DO NOTHING` silently dropped a *different*
  event that reused an existing event ID.

**Proof (test written before the fix).** On the unfixed code, CI failed with
`DateTimeParseException` instead of a no-requeue rejection.

**Fix.**
- Strict parsing of every field. Anything malformed is rejected **without requeue** and
  logged by event ID only, with no personal data.
- For a duplicate event ID, an identical replay (normal after a network hiccup) is
  accepted. Different content is rejected and logged as a conflict.
- No dead-letter queue was added, deliberately. Changing an existing durable queue's
  arguments makes RabbitMQ reject the redeclaration on existing installs, so that needs
  its own migration plan.

**Proof after the fix.** The unit tests pass, and full-stack CI still records real
User → RabbitMQ → Audit events, so valid traffic is unaffected.

**Lesson.** At-least-once delivery needs three things: classify errors as retryable or
not, keep handlers idempotent, and detect conflicts rather than silently dropping data.

---

## Supply-chain work in the same period

- **Blind spot closed** ([#464](https://github.com/teldigi5-wq/aetheris-platform/pull/464)).
  Dependency scanning only saw libraries declared directly in each `pom.xml` (about 55).
  CycloneDX SBOMs now give OSV the full graph: 588 components, 0 known vulnerabilities.
- **Remove, don't pin** ([#458](https://github.com/teldigi5-wq/aetheris-platform/pull/458)).
  Alpine withdrew a pinned libxml2 fix, which broke the dashboard image build. The
  static dashboard uses no XML nginx modules, so those modules and libraries were
  removed. Their CVEs no longer apply at all.
- **Desktop dependency advisories** ([#462](https://github.com/teldigi5-wq/aetheris-platform/pull/462)).
  `cookie` 0.18.3 and `url` 2.5.9 (RUSTSEC-2026-0594, RUSTSEC-2026-0592), changing the
  lockfile only.
- **Secrets.** A full-history scan of both repositories found no real credentials.
  GitHub secret scanning and push protection are enabled.
