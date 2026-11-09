# Aetheris / XERJ real disposable RSA-only integration review

Child of single-filter RS256 draft #382 at `6ebf578fc011dd1af717eab4350fc0ada224de3e`. Only adds hosted CI harness, synthetic documentation and non-sensitive report code. **No primary source, production cluster, live DNS, published XERJ corpus, secrets vault or existing Compose file is modified.**

## Evidence contract
Hosted Ubuntu 24.04 runner generates a fresh RSA-3072 keypair in runner-private temporary files. Only Identity's Java process receives the private signing PEM. Gateway/User/Audit Java processes are explicitly launched with the corresponding public verification PEM and no HMAC or RSA private signing key. The same ephemeral run starts real PostgreSQL, Redis and RabbitMQ containers with loopback ports on a uniquely named disposable Compose project, mirroring the previously verified 25-check integration proof in PR #380. All actual Java applications are packaged from the reviewed SHA, not mocked.

The additional end-to-end proof requires Identity's token to advertise exactly RS256 + pinned key ID, checks actual resource-server success with that token, and sends a deliberately HMAC-signed lookalike token to Gateway, User and Audit endpoints: all **must reject** it with 401. This demonstrates absence of HS256 fallback **within this hosted RSA-only environment**; it does not constitute an independent identity trust chain or production pentest.

No RSA PEM, JWT, refresh token, HTTP response body or user data is printed or uploaded. Only a generated report of PASS/FAIL check names and proof provenance is retained for seven days. Synthetic environment cleanup is unconditional.

## Unresolved gates
- Production Compose variants still require HMAC secrets; **RS256 production migration/rotation remains blocked**, and must not silently change the reviewed Compose contract.
- Route-specific audience contracts, real private-key custody/HSM, staged key rotation and provable revocation remain separate review items.
- Real Tomcat-to-Gateway mTLS workloads as staged in older PR #346 have **not** been combined with this stack.
- Per-tenant object ownership, database schema migration and rollback, least-privilege database roles and actual Kubernetes network policy remain unresolved. Historical credential reuse and XERJ source/licence consent remain pending.
- Current Stage45 NO_GO, XERJ #376 HOLD, and protected main unchanged.

A future CI passing this gate still does not imply all vulnerabilities are fixed or publication is authorized.
