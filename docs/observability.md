# Aetheris Observability — Stage 5

Stage 5 gives Aetheris three complementary telemetry signals: metrics, logs, and traces.

## Metrics

Every Java service exposes Spring Boot Actuator's `/actuator/prometheus` endpoint through Micrometer. Prometheus scrapes the gateway, user service, identity service, and audit service every five seconds. Grafana is provisioned with Prometheus and an Aetheris overview dashboard.

The `/actuator/health` endpoint remains available for health/readiness checks, but component details are hidden by default. Local operators who deliberately need detailed health output can opt in with `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`; that override should not be treated as an internet-facing production default.

Useful interview concepts: counters vs gauges, histograms, request rate, latency, JVM memory, scrape-based monitoring, labels, cardinality, and alerting.

## Logs

Applications continue writing normal container logs. An authorized host-side log exporter can copy selected real container logs to private, Git-ignored files under `observability/logs/`. Alloy reads those files through a read-only mount and forwards them to Loki. No Docker control socket is mounted inside Alloy. Metrics and traces remain operational without log export. Grafana uses Loki as the centralized log datasource.

Useful interview concepts: structured logging, correlation IDs, centralized logging, log retention, indexing tradeoffs, and why logs are not a replacement for metrics or traces.

## Traces

Micrometer Tracing uses the OpenTelemetry bridge in every Java service. In local development, tracing sampling is set to 100% so flows are easy to inspect. OTLP spans are exported to the OpenTelemetry Collector, batched, and forwarded to Tempo. Grafana is provisioned with Tempo as the trace datasource.

Useful interview concepts: trace IDs, spans, parent/child relationships, context propagation, sampling, OTLP, collectors, service boundaries, and distributed latency analysis.

## Local topology

```text
Java services -> /actuator/prometheus -> Prometheus -> Grafana
Java services -> OTLP -> OpenTelemetry Collector -> Tempo -> Grafana
Host-exported selected log files -> Grafana Alloy (read-only files) -> Loki -> Grafana
```

## Running the stack

The core Aetheris platform remains runnable with normal Docker Compose. The heavier observability components are in the optional `observability` profile:

```bash
docker compose --profile observability up --build
```

Local endpoints:

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001
- Loki: http://localhost:3100
- Tempo: http://localhost:3200
- Alloy: http://localhost:12345

Grafana local username is `aetheris`; its password must be set privately via `AETHERIS_GRAFANA_ADMIN_PASSWORD` (no default).

## Production hardening

The local setup favors learning and visibility rather than production defaults. A production deployment should reduce trace sampling, secure Grafana and telemetry endpoints, configure resource limits, add retention/storage planning, introduce alert rules, redact sensitive fields, use TLS/authentication between telemetry components, and define SLOs before scaling the telemetry stack.

For local log capture, use the trusted host's `docker compose logs --no-color --timestamps --tail=200 SERVICE > observability/logs/SERVICE.log` after creating the ignored directory. This captures a snapshot, not a live stream. Do not store private tokens in logs; restrict file permissions, retention and access. The observability container is not given Docker daemon API access.
