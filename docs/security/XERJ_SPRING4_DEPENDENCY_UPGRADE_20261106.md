# XERJ review: supported Spring modernization (draft, no release authority)

**Source parent:** `0e68bbafca720aa1f8352019f22757fc4eaaf17c` in security PR #378. This is a child change on an isolated review-only branch, not merged.

The historical four-service stack pinned Spring Boot 3.3.3 and Spring Cloud 2023.0.3, which no longer receive free open-source security maintenance. This patch attempts to update to **Spring Boot 4.1.2 + Spring Cloud 2025.1.4** (the latter is officially compatible with Boot 4.1.2), plus **springdoc 3.2.0**, published by October 2026. It also selects the explicit Gateway WebFlux starter and new property namespace. Maven and real Spring context tests MUST pass on the exact SHA before claims of compatibility.

Do not assume a successful Maven compile proves:
- live route behavior, Gateway Redis key rate limits, circuit-breaker/OTLP endpoints or real traffic from Docker/Helm;
- Jakarta/Jackson 3 RabbitMQ conversion compatibility or Flyway upgrade behavior on a real DB;
- transitive vulnerability/SBOM compliance (CVE report/lockfile needed);
- compatibility with existing production images, PC GPU/hardware or other release stages.

No secrets, production service or database are changed by this PR. Prior XERJ 40-file source at immutable main is unchanged. XERJ: HOLD; Stage 45: NO_GO. 

## 2026-11-06 OSV supply-chain findings and pinned review fixes

At exact review head `f79a26da613aa66993ef70d6b8db81d9a61867f1`, CycloneDX v2.9.3 emitted four Maven BOMs covering 220 distinct coordinates. The public OSV querybatch returned **11 advisory matches**, 10 associated with `com.rabbitmq:amqp-client:5.30.0` and one with `io.opentelemetry:opentelemetry-api:1.62.0`; CI correctly blocked.

The Spring Boot 4.1 parent BOM supports `rabbit-amqp-client.version` and `opentelemetry.version` overrides, confirmed in Spring's official dependency properties. This candidate pins Rabbit client **5.36.0** for User/Audit, and OpenTelemetry **1.67.0** for all four services. Versions must be independently confirmed in *resolved* SBOMs. No ignored GHSA or bypass flags.

RabbitMQ upstream: https://www.rabbitmq.com/client-libraries/java-client
Advisory example: https://github.com/advisories/GHSA-5m9f-rphj-c435
OpenTelemetry advisory: https://github.com/advisories/GHSA-ccrj-gw8r-854m

Run the exact-head OSV CI to verify no remaining reported hits; any failure stays NO_GO. Passing OSV is point-in-time advisory coverage only, not proof of complete supply-chain security or real deployment integrity.

## Last OSV advisory remediation attempt: RabbitMQ 5.37.0

The exact-head CI for `9143dc6c07974464b522f7bf6b70ee53979c6824`, run [42167135190](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/42167135190), still reported one advisory: `com.rabbitmq:amqp-client:5.36.0`, `GHSA-cqgh-8p3p-mx4m`. No suppression or ignore is permitted. RabbitMQ's official GitHub release and Maven Central list Java client 5.37.0, a newer maintenance release beyond the affected version range. This child draft updates User and Audit to **5.37.0**, with a new exact-head OSV and Java regression CI required to confirm the resolved dependency and advisory outcome.

Earlier 79/79 Java tests passed on the prior head, but **were not run on the new 5.37.0 candidate** until its CI completes. Neither a clean OSV result nor compiling Java would be proof that Stage 45 is production-ready.

## Real application route-binding and build smoke gates

After the exact-head OSV scan returns no reported known advisories, the CI now starts a disposable **real Spring Boot Gateway/Netty application context** with an ephemeral synthetic JWT key and no external upstream traffic. It verifies that `RouteLocator` has five live registered upstream routes. This is stronger than parsing YAML; it is **not** yet an end-to-end proxy/Redis/HTTPS integration test.

CI also runs Maven `package -DskipTests` for each module after its verified targeted tests and OSV gate, checking for `BOOT-INF/classes` and `BOOT-INF/lib` in the resulting executable JAR. Do not claim a package/build until its exact-head run is successful; no deployments are part of this workflow.
