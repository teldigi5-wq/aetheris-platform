# Security Policy

Security is a core part of Aetheris because the project includes authentication, authorization, API gateway enforcement, refresh-token handling, distributed services and deployment configuration.

## Supported code

Security reports should target the current `main` branch unless a release or maintenance branch is explicitly identified as supported.

## Reporting a vulnerability

Please do **not** publish credentials, exploit details, tokens, private keys or sensitive logs in a public issue.

For a suspected vulnerability, provide enough information to reproduce and assess the problem while minimizing exposure. A useful report includes:

- affected component or service;
- expected behavior;
- observed behavior;
- reproducible steps;
- security impact;
- relevant logs with secrets removed;
- suggested mitigation, if known.

If no private reporting channel is available, open a minimal public issue requesting a private security discussion without including exploit details or secrets.

## Security expectations

Contributions should preserve these principles:

- access tokens and refresh tokens are treated as credentials;
- secrets must never be committed to source control;
- authorization decisions belong at trusted service/gateway boundaries;
- inputs from clients and external systems must be validated;
- production credentials must not use development defaults;
- logs must avoid leaking passwords, tokens, cookies or private keys;
- dependency and container updates should be reviewed for security impact;
- Kubernetes Secrets in local examples are development-only and are not a production secret-management strategy.

## Production note

The repository is designed primarily as a local and portfolio platform. Example credentials, local Kubernetes values and development configuration must be replaced before any real production deployment.

A production environment should add, at minimum:

- managed secret storage;
- TLS at ingress and service boundaries where appropriate;
- hardened identity configuration;
- restricted network policies/security groups;
- backup and recovery procedures;
- vulnerability/dependency scanning;
- centralized audit retention;
- production-specific Kubernetes values and access controls.

## Scope

Examples of security-relevant areas include:

- JWT validation and scope enforcement;
- refresh-token rotation/revocation;
- gateway routing and authorization;
- Redis-backed distributed rate limiting;
- RabbitMQ permissions and event trust boundaries;
- service-to-service configuration;
- container/Kubernetes configuration;
- sensitive logging or error responses.

Security improvements are welcome when they preserve the project's goal of remaining understandable and demonstrable as an engineering portfolio system.
