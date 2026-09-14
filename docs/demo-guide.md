# Aetheris Demo Guide

This guide is the shortest reviewer-friendly path through the repository. It is designed for interviews, portfolio review and local engineering verification.

The goal is not to prove every capability in one session. The goal is to demonstrate the platform coherently while preserving the repository's truth boundaries.

## 1. What this demo proves

A successful repository-side demo can show:

- the distributed service topology starts locally;
- gateway, identity, user, audit and orchestrator surfaces are reachable;
- the dashboard is served;
- persistence/cache/messaging dependencies start;
- the optional observability stack can be enabled;
- repository governance, safety and verification contracts are visible and reproducible.

It does **not** prove the complete target-PC stack, GPU acceleration, voice hardware, browser/phone control, sustained thermals or unrestricted workstation authority.

## 2. Prerequisites

Recommended for the containerized demo:

- Git
- Docker with Docker Compose v2

For module-level work you may also need Java 21, Node and Python according to the repository's pinned/local version files and module requirements.

## 3. Clone and inspect

```bash
git clone https://github.com/teldigi5-wq/aetheris-platform.git
cd aetheris-platform
```

Before starting anything, orient the reviewer with:

- `README.md`
- `docs/architecture.md`
- `docs/master-build-spec.md`

Explain the distinction:

```text
Syntra = owner-facing assistant experience
Aetheris = infrastructure, orchestration and governance layer
Models = replaceable reasoning workers, not policy authority
```

## 4. Start the core stack

```bash
docker compose up --build
```

In another terminal:

```bash
docker compose ps
```

Expected local surfaces include:

| Surface | Address |
|---|---|
| Dashboard | `http://localhost:3000` |
| Gateway | `http://localhost:8080` |
| User Service | `http://localhost:8081` |
| Identity Service | `http://localhost:8082` |
| Audit Service | `http://localhost:8083` |
| Orchestrator | `http://localhost:8090` |
| RabbitMQ management | `http://localhost:15672` |

The credentials and fallback secrets in Compose are development defaults only.

## 5. Demonstrate identity flow

The identity service exposes:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
```

A useful interview explanation is that access/refresh tokens are credentials, not ordinary identifiers. Refresh flows should be treated as security-sensitive lifecycle state.

Do not paste real passwords, tokens or private credentials into screenshots, issues or recorded demos.

## 6. Demonstrate the request path

Use the architecture diagram to explain the service flow:

```text
Dashboard / Client
  → API Gateway
  → trusted auth/routing boundary
  → downstream service
  → PostgreSQL / Redis / RabbitMQ when needed
  → response
  → audit / metrics / traces
```

The important point is not merely that requests return 200. Explain which component owns each responsibility and what should happen when a dependency fails.

## 7. Show the dashboard

Open:

```text
http://localhost:3000
```

Use the dashboard to explain that the UI is an operator/developer surface. It displays and exercises platform behavior; it is not the final owner-policy authority.

If a UI surface is unavailable during a demo, use the service/API and observability evidence rather than hiding the failure.

## 8. Enable observability

Stop the core stack if needed, then start the observability profile:

```bash
docker compose --profile observability up --build
```

Additional surfaces:

| Tool | Address |
|---|---|
| Grafana | `http://localhost:3001` |
| Prometheus | `http://localhost:9090` |
| Tempo | `http://localhost:3200` |
| Loki | `http://localhost:3100` |

Explain the roles:

- **Prometheus** — metrics;
- **Grafana** — visualization/exploration;
- **Loki** — logs;
- **Tempo** — traces;
- **OpenTelemetry** — instrumentation/telemetry transport foundation.

Observability is diagnostic evidence. It does not by itself prove a business action succeeded.

## 9. Demonstrate governance

Open `docs/master-build-spec.md` and `docs/stage-33-governance-approvals.md`.

Explain the universal lifecycle:

```text
UNDERSTAND
  → PLAN
  → CHECK RULES
  → ASSESS RISK
  → SIMULATE / PREVIEW when required
  → APPROVE when required
  → EXECUTE
  → VERIFY
  → RECORD
  → LEARN
  → REPORT
```

Then explain three key semantics:

1. `ALLOW` means **eligible**, not executed.
2. no execution observation means `EXECUTE` is incomplete;
3. success requires verification evidence or an explicit `UNVERIFIED` state.

Emergency precedence is:

```text
STOP > TAKE_CONTROL > PAUSE > NORMAL
```

## 10. Demonstrate repository quality controls

Show GitHub Actions and the protected `main` ruleset.

Explain that stable promotion is gated by checks covering areas such as:

- backend/service tests;
- dashboard build;
- workstation-agent checks;
- CodeQL;
- dependency lockdown;
- reproducible-build comparison;
- readiness/safety/governance checks;
- Stage 30–34 regression/integrity validation.

The repository intentionally separates stable history from temporary development work.

## 11. Run focused repository validators

Stage 34 specification integrity:

```bash
python tools/validate_master_build_spec.py
```

Reasoning regression:

```bash
cd aetheris-reasoning
python -m unittest discover -s tests -v
```

Later-stage orchestrator regressions can be run from the repository root:

```bash
mvn -B -f orchestrator-service/pom.xml -Dtest=Stage31FoundationTest test
mvn -B -f orchestrator-service/pom.xml -Dtest=Stage32FoundationTest test
mvn -B -f orchestrator-service/pom.xml -Dtest=Stage33GovernanceTest test
```

## 12. Strong 10-minute interview sequence

### Minute 0–1 — Problem and identity

Explain Syntra vs Aetheris and the owner-control principle.

### Minute 1–3 — Architecture

Walk through gateway, identity, user, audit, orchestrator, PostgreSQL, Redis and RabbitMQ.

### Minute 3–5 — Running system

Show the dashboard/core services and one identity/API flow.

### Minute 5–7 — Observability

Show how you would diagnose a distributed request or service failure.

### Minute 7–9 — Governance and verification

Explain deterministic owner policy, approval, emergency precedence and the difference between `ALLOW`, execution and verified success.

### Minute 9–10 — Engineering truth

Show protected `main`, reproducibility/CI evidence and the physical-PC boundary:

```text
Repository roadmap: 34 / 34 complete
Physical-machine status: BLOCKED_PENDING_HARDWARE
```

This ending demonstrates that the project distinguishes implementation from proof.

## 13. Failure-demo ideas

When appropriate, demonstrate a controlled failure rather than only the happy path.

Examples:

```bash
docker compose stop user-service
```

Then observe gateway/service behavior and logs. Restore it afterward:

```bash
docker compose start user-service
```

Use `docs/resilience.md` for the intended resilience demonstration and recovery notes.

## 14. Cleanup

Stop the stack:

```bash
docker compose down
```

To remove local volumes as well, only do so when you intentionally want to destroy local development data:

```bash
docker compose down -v
```

## 15. What not to claim during the demo

Do not claim:

- full production readiness;
- physical-PC validation before hardware evidence exists;
- unrestricted autonomous workstation/admin authority;
- autonomous live-money trading;
- that CI proves GPU/voice/thermal performance;
- that policy eligibility proves execution success.

The credibility of the demo comes from showing both capabilities **and boundaries**.
