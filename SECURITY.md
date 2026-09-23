# Security Policy

Security is a core part of Aetheris because the project includes authentication, authorization, API gateway enforcement, refresh-token handling, distributed services, deployment configuration and an external AI-runtime boundary.

## Supported code and repository ownership

Security reports should target the current supported branch/revision of the repository that owns the affected source.

- `teldigi5-wq/aetheris-platform` owns gateway, identity, user, audit, dashboard, platform deployment/observability and cross-repository integration assets.
- `teldigi5-wq/aetheris-ai-runtime` owns `orchestrator-service`, `workstation-agent`, `aetheris-reasoning` and `aetheris-quant`.

For cross-repository vulnerabilities, identify both the platform contract/integration surface and the runtime revision involved. Do not assume that runtime `main` is the same thing as the platform-recorded certified runtime checkpoint.

## Reporting a vulnerability

Please do **not** publish credentials, exploit details, tokens, private keys or sensitive logs in a public issue.

For a suspected vulnerability, provide enough information to reproduce and assess the problem while minimizing exposure. A useful report includes:

- affected repository, component or service;
- exact commit/runtime reference when known;
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
- authorization decisions belong at trusted service/gateway/runtime boundaries;
- inputs from clients and external systems must be validated;
- production credentials must not use development defaults;
- logs and evidence must avoid leaking passwords, tokens, cookies or private keys;
- dependency and container updates should be reviewed for security impact;
- Kubernetes Secrets in local examples are development-only and are not a production secret-management strategy;
- runtime-owned source must not be duplicated into the platform to bypass a boundary or certification problem;
- model output cannot override deterministic owner-policy/approval boundaries;
- live-money execution remains disabled by policy unless a future separately reviewed safety boundary explicitly changes that status.

## External AI-runtime boundary

The platform consumes the AI runtime through versioned integration/certification assets. Security-sensitive runtime upgrades must preserve exact provenance and compatibility evidence. A newer runtime SHA is not automatically trusted or certified merely because it exists on `main`.

Failures at the platform/runtime boundary should fail explicitly. They must not silently select an uncertified runtime, weaken authentication/authorization, or fabricate successful execution evidence.

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
- production-specific Kubernetes values and access controls;
- a deliberate runtime artifact publication/provenance process if external runtime images are distributed.

No current hosted CI result by itself is a production-activation or registry-publication claim.

## Scope

Examples of platform security-relevant areas include:

- JWT validation and scope enforcement;
- refresh-token rotation/revocation;
- gateway routing and authorization;
- Redis-backed distributed rate limiting;
- RabbitMQ permissions and event trust boundaries;
- platform-to-runtime contract/integration behavior;
- service-to-service configuration;
- container/Kubernetes configuration;
- sensitive logging or error responses.

Runtime security-relevant areas such as workstation privileges, browser/operator execution, reasoning/tool authorization and quant/trading controls are owned by `teldigi5-wq/aetheris-ai-runtime` and should be reviewed there together with the relevant platform boundary when applicable.

## Physical validation boundary

Hosted CI is repository evidence, not proof of the future owner PC. Physical-machine status remains `BLOCKED_PENDING_HARDWARE` until the required target-hardware validation is performed. Do not use a security test result to imply GPU, browser, microphone, Windows/WSL2, thermal or other physical-machine validation that did not occur.

Security improvements are welcome when they preserve the project's goal of remaining understandable and demonstrable as an engineering portfolio system.
