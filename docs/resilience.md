# Stage 6 — Resilience

Aetheris Stage 6 adds gateway-level resilience so a failing downstream service does not automatically become a platform-wide failure.

## Policies

- Global downstream connect timeout: 2 seconds.
- Global downstream response timeout: 5 seconds.
- Resilience4j time limiter: 4 seconds.
- Circuit breakers use a 10-call count window, require 5 calls before evaluation, open at a 50% failure rate, wait 10 seconds, then allow 3 half-open probe calls.
- GET/HEAD requests to the user and audit services retry at most 2 times with exponential backoff (100 ms to 500 ms).
- Mutating user requests are never automatically retried, avoiding accidental duplicate writes.
- Identity operations are circuit-broken but not retried because registration/login/refresh/logout are not universally safe to replay.
- Structured fallback responses return HTTP 503 and identify the unavailable downstream service.

## Why retries are restricted

Retries are useful for transient failures, but replaying a POST, PATCH, or DELETE can duplicate or repeat side effects. Aetheris therefore retries only idempotent reads at the gateway.

## Verification

Run the platform normally, authenticate, and verify `/api/users` returns 200. Then stop only the user service:

```bash
docker compose stop user-service
```

Call the protected user endpoint several times. The gateway should return a structured HTTP 503 fallback instead of an unhandled 5xx/connection failure. The response should identify `user-service` and set `retryable` to `true`.

Inspect circuit breaker state:

```bash
curl http://localhost:8080/actuator/circuitbreakers
```

or on PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/circuitbreakers
```

Restore the downstream service:

```bash
docker compose start user-service
```

After the open-state wait period and successful half-open probes, normal requests should recover without restarting the gateway.

## Interview talking points

Explain the difference between timeouts, retries, circuit breakers, and fallbacks; why retrying writes is dangerous; closed/open/half-open circuit states; retry storms; backoff; bulkheads; idempotency; graceful degradation; and how metrics/traces from Stage 5 help diagnose resilience behavior.
