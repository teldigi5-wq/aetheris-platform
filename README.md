# Aetheris Platform

**Build. Secure. Observe. Orchestrate.**

Aetheris is an open-source distributed API, identity, observability, and AI-agent platform built with Java, Spring Boot, React, PostgreSQL, Redis, RabbitMQ, Prometheus, Grafana, Loki, Tempo, OpenTelemetry, and cloud-native technologies.

## Current milestone — Stage 5 complete

Stage 5 delivers local metrics, logs, and traces for the Aetheris platform using Prometheus, Grafana, Loki, Grafana Alloy, OpenTelemetry Collector, and Tempo.

Runtime verification completed on 2026-09-10: Prometheus reports all four Java services UP, Grafana renders the Aetheris Platform Overview dashboard, Loki receives centralized container logs, and Tempo returns queryable application traces with trace IDs, service names, span names, start times, and durations.

## Roadmap

- [x] Stage 1 — Gateway + user microservice + PostgreSQL
- [x] Stage 1.5 — Dashboard, DTO/service layers, OpenAPI, structured errors, stronger CI
- [x] Stage 2 — Identity service, JWT authentication, RBAC, refresh tokens, scoped permissions
- [x] Stage 3 — Redis caching and distributed rate limiting
- [x] Stage 4 — RabbitMQ event messaging
- [x] Stage 5 — Prometheus, Grafana, centralized logs, OpenTelemetry
- [ ] Stage 6 — Circuit breakers, retries, timeouts, load balancing
- [ ] Stage 7 — Local Kubernetes + Helm
- [ ] Stage 8 — Aetheris CLI + SDK generation
- [ ] Stage 9 — AI Agent Gateway, policy engine, local Ollama integration
- [ ] Stage 10 — Chaos Lab + Security Lab
