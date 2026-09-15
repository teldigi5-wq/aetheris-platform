# Core Platform Evidence Pack

This page is the reviewer-facing evidence index for Aetheris's core platform-engineering story. It is intentionally narrower than the full repository.

The rule is simple:

> A claim belongs here only when a reviewer can trace it to concrete source/configuration and the repository can verify that evidence deterministically.

Repository evidence is not the same as runtime proof. Screenshots, live Kubernetes behavior, external-user adoption, production readiness, and physical-PC validation require separate evidence.

## Machine-readable source of truth

The canonical claim manifest is:

```text
build-evidence/portfolio/core-platform-evidence.json
```

Validate it locally with:

```bash
python tools/validate_portfolio_evidence.py
python -m unittest tests/test_portfolio_evidence.py -v
```

The dedicated `Portfolio Evidence` GitHub Actions workflow runs the validator twice and compares the generated reports byte-for-byte.

## Evidence matrix

| Engineering claim | Primary evidence | What it demonstrates | What it does not prove |
|---|---|---|---|
| Refresh-token lifecycle | `identity-service/.../RefreshTokenService.java`, `RefreshTokenServiceTest.java` | secure random token issuance, SHA-256 stored hash, rotation, revocation, rejection path | production identity hardening, external penetration testing |
| Redis caching | `user-service/.../CacheConfig.java` | Spring caching configuration, Redis serialization, bounded TTL, null-cache avoidance | production cache hit rate or latency |
| Gateway traffic controls | `gateway/src/main/resources/application.yml` | Redis-backed rate limiting, route boundaries, circuit breakers | internet-scale throughput |
| Safe retry semantics | gateway config + `docs/resilience.md` | GET/HEAD retry is bounded; mutating user requests are not automatically retried | exactly-once semantics for every subsystem |
| RabbitMQ events | `UserEventPublisher.java`, `RabbitEventConfig.java` | asynchronous user-domain event publishing through a topic exchange | production delivery guarantees under every failure mode |
| Observability stack | `observability/` | Prometheus, Loki, Tempo, OpenTelemetry collector and Grafana configuration exist as one stack | a specific production SLO or live incident response |
| Helm deployment | `deploy/helm/aetheris/` | repository-native chart, values and templates | validation on every Kubernetes distribution |
| Core CI | `.github/workflows/build.yml` | backend, dashboard, workstation, compatibility and release-hardening checks | physical-PC behavior |

## Quick reviewer commands

### 1. Verify the evidence manifest

```bash
python tools/validate_portfolio_evidence.py
```

Expected repository-side result:

```text
"status": "PASS"
"runtime_claim": "NOT_EVALUATED"
"physical_pc_status": "BLOCKED_PENDING_HARDWARE"
```

The last two fields are deliberate. This validator checks repository evidence, not live runtime behavior.

### 2. Run identity tests

```bash
mvn -B -f identity-service/pom.xml test
```

Review these files while the tests run:

```text
identity-service/src/main/java/io/aetheris/identity/RefreshTokenService.java
identity-service/src/test/java/io/aetheris/identity/RefreshTokenServiceTest.java
```

Questions worth asking:

- Why hash refresh tokens at rest?
- Why rotate rather than reuse them indefinitely?
- What should happen to a revoked/expired token?

### 3. Inspect safe retry and circuit-breaker policy

```bash
sed -n '1,240p' gateway/src/main/resources/application.yml
```

On Windows PowerShell:

```powershell
Get-Content gateway/src/main/resources/application.yml
```

Look for the separation between `user-service-read` and `user-service-write`. GET/HEAD routes can retry bounded infrastructure failures; mutating routes have circuit breaking but no blind retry filter.

### 4. Inspect asynchronous messaging

```bash
cat user-service/src/main/java/io/aetheris/users/events/UserEventPublisher.java
```

The publisher sends `user.created` and `user.deleted` events through the `aetheris.events` exchange instead of coupling every audit concern to the synchronous request path.

### 5. Inspect observability as a system

```bash
ls observability
```

Key files:

```text
observability/prometheus.yml
observability/loki.yml
observability/tempo.yml
observability/otel-collector.yml
observability/grafana/
```

### 6. Inspect the Helm package

```bash
helm lint deploy/helm/aetheris
helm template aetheris deploy/helm/aetheris > /tmp/aetheris-rendered.yaml
```

These commands require Helm locally. They are reviewer commands, not part of the Python evidence validator.

## Runtime evidence still worth capturing

The repository should eventually add sanitized screenshots or short recordings for the following, captured from a real validated environment:

1. successful login/refresh/logout flow;
2. Redis-backed cache/rate-limit behavior;
3. RabbitMQ exchange/queue event flow;
4. Grafana/Prometheus/Loki/Tempo views from one traced request;
5. circuit breaker opening and recovering during a controlled service outage;
6. Kubernetes replica scaling and pod self-healing;
7. rendered Helm release and healthy service endpoints.

Until those artifacts exist, do not replace repository/configuration evidence with invented screenshots or simulated claims.

## Interview use

A strong interview sequence is:

1. pick one claim from this matrix;
2. open the exact source/config file;
3. explain the design choice and trade-off;
4. run the narrow verification command;
5. state what the evidence proves and what remains unproven.

That is more credible than presenting a large feature count without traceable proof.
