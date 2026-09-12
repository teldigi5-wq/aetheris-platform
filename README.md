<div align="center">

# ⚡ Aetheris Platform

### Build. Secure. Observe. Orchestrate.

**A cloud-native platform engineering project for APIs, identity, distributed systems, observability and future AI-agent governance.**

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-TypeScript-61DAFB?style=for-the-badge&logo=react&logoColor=111827)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-Helm-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)

`Java 21` • `Spring Boot` • `React` • `PostgreSQL` • `Redis` • `RabbitMQ` • `Prometheus` • `Grafana` • `OpenTelemetry` • `Kubernetes` • `Helm`

</div>

---

## 🎯 What Aetheris demonstrates

Aetheris is a long-term engineering portfolio project designed to show how a modern platform grows from a small service into a distributed, observable and cloud-native system.

Instead of building isolated demos, each stage adds one production-relevant capability to the same platform: authentication, authorization, caching, rate limiting, event messaging, observability, resilience, Kubernetes deployment and eventually AI-agent governance.

> **Portfolio goal:** make every architectural decision explainable in an interview and verifiable in running code.

---

## ✅ Current milestone — Stage 7 complete

The platform now runs locally through both **Docker Compose** and **Kubernetes + Helm**.

Stage 7 includes PostgreSQL, Redis, RabbitMQ, gateway, identity, user, audit and dashboard workloads with Services, persistent volumes, ConfigMaps, Secrets, resource requests/limits, startup/readiness/liveness probes and configurable replicas.

Runtime verification includes successful authentication, protected gateway routing, two Ready `user-service` replicas, EndpointSlice-backed service discovery, replica self-healing and scaling back to the low-memory default.

---

## 🏗️ Architecture

```mermaid
flowchart LR
    U[Browser / Client] --> D[React Dashboard]
    D --> G[API Gateway]
    G --> I[Identity Service]
    G --> S[User Service]
    G --> A[Audit Service]
    G --> R[(Redis)]
    I --> P[(PostgreSQL)]
    S --> P
    S --> M[(RabbitMQ)]
    M --> A
    G --> O[Metrics / Traces]
    I --> O
    S --> O
    A --> O
    O --> OBS[Prometheus + Grafana + Loki + Tempo]
    K[Kubernetes + Helm] --> D
    K --> G
    K --> I
    K --> S
    K --> A
```

### Core engineering domains

| Domain | What Aetheris implements |
|---|---|
| API platform | Gateway routing, protected endpoints, structured failures |
| Identity | JWT access tokens, refresh-token rotation, RBAC and scopes |
| Data | PostgreSQL persistence with shared service data |
| Distributed systems | Redis caching/rate limiting and RabbitMQ events |
| Resilience | Circuit breakers, retries, timeouts and fallbacks |
| Observability | Metrics, logs and distributed traces |
| Cloud native | Docker Compose, Kubernetes, Helm and health probes |
| Future AI | Agent gateway, policies and local-model integration roadmap |

---

## 🔐 Security model

Access tokens are signed JWTs containing identity, role and effective scope claims. Refresh tokens are opaque, rotated on use, revocable and stored only as SHA-256 hashes in PostgreSQL.

The gateway validates tokens and enforces scopes before protected requests reach downstream services.

| Role | Default permissions |
|---|---|
| `API_CONSUMER` | `users:read`, `services:read` |
| `DEVELOPER` | consumer scopes + `users:write`, `identity:read` |
| `ADMIN` | developer scopes + `identity:write` |

---

## ⚡ Distributed traffic model

Redis provides shared caching and distributed token-bucket rate limiting. User-cache entries use a 60-second TTL and are evicted on writes.

RabbitMQ carries asynchronous `user.created` and `user.deleted` events to the audit service through the durable `aetheris.events` topic exchange.

---

## 📈 Observability

Each Java service exposes Prometheus metrics through Spring Boot Actuator. The observability stack combines:

- **Prometheus** for metrics
- **Grafana** for dashboards
- **Loki** for centralized logs
- **Tempo** for traces
- **OpenTelemetry** for instrumentation and trace export
- **Grafana Alloy** for container log discovery

The observability profile remains optional so the core platform stays practical on lower-memory development hardware.

---

## 🛡️ Resilience

The gateway protects downstream calls with Resilience4j circuit breakers and explicit network timeouts.

Safe reads can retry with exponential backoff, while mutating requests are not replayed automatically. Dependency failures return structured `503` responses instead of leaking raw connection errors.

---

## ☸️ Kubernetes + Helm

The Helm chart under `deploy/helm/aetheris` deploys the core platform into one namespace.

Kubernetes Services provide stable internal DNS, stateful infrastructure receives persistent volumes, stateless services use Deployments and health probes keep traffic away from unready pods.

The default values are intentionally sized for a small local machine. Production deployments should use external secrets, stronger storage/backup policies, ingress/TLS and separate production values.

---

## 🚀 Quick start

### Docker Compose

```bash
git clone https://github.com/teldigi5-wq/aetheris-platform.git
cd aetheris-platform
docker compose up --build
```

### Full observability stack

```bash
docker compose --profile observability up --build
```

### Main local endpoints

| Service | URL |
|---|---|
| Dashboard | `http://localhost:3000` |
| Gateway health | `http://localhost:8080/actuator/health` |
| Identity API | `http://localhost:8080/api/auth/*` |
| User API | `http://localhost:8080/api/users` |
| Event API | `http://localhost:8080/api/events` |
| RabbitMQ UI | `http://localhost:15672` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3001` |
| Loki | `http://localhost:3100` |
| Tempo | `http://localhost:3200` |

For Kubernetes deployment, see [`docs/kubernetes.md`](docs/kubernetes.md).

---

## 🗺️ Roadmap

- [x] Stage 1 — Gateway + user service + PostgreSQL
- [x] Stage 1.5 — Dashboard, DTO/service layers, OpenAPI, structured errors and CI
- [x] Stage 2 — Identity, JWT authentication, RBAC, refresh tokens and scopes
- [x] Stage 3 — Redis caching + distributed rate limiting
- [x] Stage 4 — RabbitMQ event messaging
- [x] Stage 5 — Prometheus, Grafana, logs and OpenTelemetry
- [x] Stage 6 — Circuit breakers, retries, timeouts and load balancing
- [x] Stage 7 — Local Kubernetes + Helm
- [ ] Stage 8 — Aetheris CLI + SDK generation
- [ ] Stage 9 — AI Agent Gateway + policy engine + local Ollama integration
- [ ] Stage 10 — Chaos Lab + Security Lab

---

## 📚 Documentation

- [Architecture](docs/architecture.md)
- [Interview talking points](docs/interview-guide.md)
- [Token flow and threat model](docs/security/token-flow.md)
- [Resilience runbook](docs/resilience.md)
- [Kubernetes + Helm runbook](docs/kubernetes.md)

---

## 💡 Design rule

The development stack has **no mandatory recurring software fee**. Open-source components and local infrastructure are the default; cloud deployment remains optional.

---

<div align="center">

### From fundamentals to production-style platform engineering.

**Built by Poojana Kaveesh Sellahewa**

</div>
