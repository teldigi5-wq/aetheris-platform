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
