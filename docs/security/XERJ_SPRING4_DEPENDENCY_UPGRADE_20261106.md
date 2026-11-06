# XERJ review: supported Spring modernization (draft, no release authority)

**Source parent:** `0e68bbafca720aa1f8352019f22757fc4eaaf17c` in security PR #378. This is a child change on an isolated review-only branch, not merged.

The historical four-service stack pinned Spring Boot 3.3.3 and Spring Cloud 2023.0.3, which no longer receive free open-source security maintenance. This patch attempts to update to **Spring Boot 4.1.2 + Spring Cloud 2025.1.4** (the latter is officially compatible with Boot 4.1.2), plus **springdoc 3.2.0**, published by October 2026. It also selects the explicit Gateway WebFlux starter and new property namespace. Maven and real Spring context tests MUST pass on the exact SHA before claims of compatibility.

Do not assume a successful Maven compile proves:
- live route behavior, Gateway Redis key rate limits, circuit-breaker/OTLP endpoints or real traffic from Docker/Helm;
- Jakarta/Jackson 3 RabbitMQ conversion compatibility or Flyway upgrade behavior on a real DB;
- transitive vulnerability/SBOM compliance (CVE report/lockfile needed);
- compatibility with existing production images, PC GPU/hardware or other release stages.

No secrets, production service or database are changed by this PR. Prior XERJ 40-file source at immutable main is unchanged. XERJ: HOLD; Stage 45: NO_GO. 
