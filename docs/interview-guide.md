# Aetheris Interview Guide

This document records the reasoning behind the platform. The goal is to understand the system well enough to defend its design, not memorize buzzwords.

## Explain Aetheris in 30 seconds

Aetheris is a locally reproducible distributed platform built around an API gateway and independently deployable services. The current version routes user operations through Spring Cloud Gateway to a Spring Boot user service backed by PostgreSQL, exposes health and OpenAPI endpoints, and includes an operator dashboard. Future stages add identity, distributed rate limiting, messaging, observability, resilience and agent governance.

## Questions to be ready for

### Why not start with ten microservices?

Because service boundaries should represent real domain or scaling needs. Starting with one service plus a gateway lets the project demonstrate distributed communication while keeping failure modes understandable. New services will be introduced only when they add a distinct responsibility.

### Why PostgreSQL?

It provides transactions, constraints, mature tooling and production relevance. A relational model is appropriate for user/identity data and keeps the first stage focused on platform architecture rather than database novelty.

### Why DTOs instead of returning JPA entities?

API contracts and persistence models evolve for different reasons. DTOs prevent accidental field exposure, allow validation at the boundary and reduce coupling between clients and the database model.

### What happens if the user service goes down?

Today gateway requests fail because Stage 1.5 does not hide that dependency. A later resilience stage will add bounded timeouts, circuit breakers, retries only where safe, health-aware routing and metrics. Being explicit about what is not implemented is part of the design.

### What changes in Stage 2?

An identity service will issue and validate credentials/tokens. The gateway will become a policy-enforcement point, while downstream services will still validate authorization assumptions rather than blindly trusting arbitrary client input.
