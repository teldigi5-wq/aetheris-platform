# Testing Strategy

Aetheris treats testing as engineering evidence rather than a checkbox. The current suite focuses on security-sensitive identity behavior first, while broader integration and failure-path coverage remains an explicit next step.

## What is tested today

The `identity-service` contains focused unit tests for authentication and refresh-token behavior.

### Identity security

`IdentityServiceSecurityTest` verifies that:

- registration normalizes email addresses before persistence;
- plaintext passwords are not stored directly;
- BCrypt hashes match the submitted password;
- new accounts receive the expected default role;
- login with an incorrect password is rejected before access or refresh tokens are issued.

### Refresh-token rotation

`RefreshTokenServiceTest` verifies that:

- presenting a valid refresh token revokes the token that was used;
- a replacement refresh token is issued;
- the replacement differs from the original token;
- a revoked refresh token cannot be reused.

These tests use mocked repositories and collaborators so they validate service behavior without requiring PostgreSQL or other infrastructure.

## CI execution

The GitHub Actions CI workflow runs the identity-service unit tests on every push and pull request targeting `main`:

```bash
mvn -B -pl identity-service test
```

The Java build only proceeds after that test job succeeds. The dashboard is validated independently with a clean npm install and production build.

## What this does not prove

Passing unit tests is not production certification. The current suite does not yet provide complete end-to-end coverage of PostgreSQL, Redis, RabbitMQ, Kubernetes, network failures or full browser-to-service flows.

Those behaviors are currently supported by documented runtime verification and should progressively move into automated integration tests.

## Next test layers

Planned improvements:

1. repository and persistence integration tests using an isolated database;
2. gateway authorization tests for protected routes and scope failures;
3. Redis cache and rate-limit behavior tests;
4. RabbitMQ publish/consume integration tests;
5. resilience tests for timeouts, retries, circuit breakers and fallbacks;
6. Docker Compose smoke tests for core service health;
7. Kubernetes deployment smoke checks;
8. dashboard component and API-contract tests.

## Evidence rule

A capability should only be described as automatically tested when a repeatable test exists and runs successfully in CI. Manual verification and architecture documentation remain useful, but they should be labelled separately from automated test evidence.
