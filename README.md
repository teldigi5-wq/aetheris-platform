# Aetheris Platform

**Build. Secure. Observe. Orchestrate.**

Aetheris is an open-source distributed API, identity, observability, and AI-agent platform built with Java, Spring Boot, and cloud-native technologies.

## Stage 1

Current MVP includes:

- Java 21
- Spring Boot
- Spring Cloud Gateway
- PostgreSQL
- Spring Data JPA
- Docker Compose
- Health endpoints
- Basic user service
- Gateway routing

## Architecture

```text
Client
  |
  v
Aetheris Gateway :8080
  |
  v
User Service :8081
  |
  v
PostgreSQL :5432
```

## Roadmap

1. Core gateway and microservices
2. Authentication and identity
3. Redis caching and rate limiting
4. Messaging with Kafka or RabbitMQ
5. Centralized logging and observability
6. OpenTelemetry, Prometheus, Grafana
7. Resilience and circuit breakers
8. Developer CLI and SDK generation
9. AI Agent Gateway and policy engine
10. Chaos Lab and Security Lab

## Goal

Aetheris is being built as an interview-grade distributed systems portfolio project focused on API management, identity, security, resilience, observability, cloud-native engineering, and agentic AI.
