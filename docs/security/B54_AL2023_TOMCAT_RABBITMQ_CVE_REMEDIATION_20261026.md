# B54 — Remediate exact B53 findings in AL2023, embedded Tomcat and RabbitMQ Java

**26 October 2026 — DRAFT stacked on [B53 PR #287](https://github.com/teldigi5-wq/aetheris-platform/pull/287), which intentionally remains red on the vulnerable baseline.**

## Verified pre-remediation baseline

[Exact original B53 Actions #40288787722](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40288787722) built and scanned all four Corretto 21.0.13 runtime images with SHA256-verified Syft 1.55.0 and Grype 0.121.0; the fresh Grype DB was built 2026-10-26T06:38:44Z. CycloneDX inventories ranged 7,608–7,657 packages, with 9/12/22/22 Grype matches for Gateway/Identity/User/Audit, respectively; the strict release policy found 8/11/15/15 blocking HIGH/CRITICAL/UNKNOWN matches. The original B53 workflow **failed correctly** and retained a full 14-day SBOM/Grype report artifact ID 12635690268.

The actual report showed **22 unique advisory/package combinations** (some repeated across four images), grouped into:
- **Amazon Linux 2023 RPMs** with fixable high advisories: `libbrotli`, `curl-minimal/libcurl-minimal`, `libcap`, `python3/python3-libs`, `pcre2/pcre2-syntax`; plus fixable medium `ca-certificates`.
- **Identity/User/Audit** `org.apache.tomcat.embed:tomcat-embed-core:11.0.24` with three CRITICAL GHSAs; Grype identifies 11.0.25 as the earliest fixed floor. Maven Central has a newer **11.0.27** release.
- **User/Audit** `com.rabbitmq:amqp-client:5.30.0` with multiple high/medium GHSAs, newest minimum fixed floor 5.36.1. Maven Central has the newer **5.37.0**.

**The above are dependency version matches; not verified remote exploitability.** The advisory report must not be discarded or the original B53 failure concealed.

## Narrow remediation in this stacked draft

1. The existing exact JDK and runtime OCI SHA256 FROM pins for official Corretto 21.0.13 remain unchanged. Each of the four **headless runtime stages** now invokes `dnf -y --releasever=2023.12.20261026 upgrade` before creating the `10001:10001` service account, to apply AL2023 repository fixes to inherited packages. This is a **moving signed repository input** despite the pinned base image, and reproducibility requires capturing the full tested SBOM and rebuilt image digest; production promotion must use the exact reviewed binary.
2. `identity-service`, `user-service` and `audit-service` explicitly override Spring Boot's Tomcat family property to `11.0.27`. Maven resolved dependency trees must prove the property actually aligns `tomcat-embed-core` and sibling components; a POM property alone is not accepted as evidence.
3. `user-service` and `audit-service` explicitly declare direct `com.rabbitmq:amqp-client:5.37.0` to override their older transitively pulled AMQP client. Real Maven trees and tests must confirm the override works without changing RabbitMQ message semantics.
4. The B53 SHA256-verified full four-image Syft/Grype workflow is retargeted to run for B54's stacked PR. **It continues to FAIL on any High/Critical/Unknown advisory or malformed/stale scan**, retains full reports on failure and never deploys anything. B54 adds five source-invariant tests and executes all four Maven test suites plus exact resolved-version checks **inside the patched Corretto builders**.

## First B54 scan: dependency remediation verified; OS still pinned to older repository

The initial [B54 hosted scan #40290590420](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40290590420) **correctly failed its security policy**, even though 10/10 B53 tests, 5/5 B52 source tests, 5/5 B54 tests and the real patched Corretto Maven dependency checks passed. Grype's final images had no remaining Tomcat 11.0.24 or RabbitMQ client 5.30.0 advisory matches; all four images still reported the same eight HIGH AL2023 package advisory matches and one MEDIUM package match (9 matches per service). The data showed **unchanged RPM versions** after the previous `dnf -y upgrade`.

AWS documents why: AL2023 repository versions are deliberately pinned to the installed `system-release`, and a bare `dnf upgrade` only sees the old snapshot. The affected advisory pages for [ALAS2023-2026-3334](https://alas.aws.amazon.com/AL2023/ALAS2023-2026-3334.html) and [ALAS2023-2026-3267](https://alas.aws.amazon.com/AL2023/ALAS2023-2026-3267.html) explicitly cite **`--releasever=2023.12.20261026`**. Therefore, this branch now uses the fixed **dated repository** for BOTH runtime upgrade and the `shadow-utils` install, not the mutable `latest`. This source adjustment has to be re-tested with an exact-head full image build/scan; until then **NO_GO** remains.

## Verification and limits

This update is not accepted until the B54 exact-commit real Maven tests and full four-image scan both pass. A test failure remains evidence of a genuine compatibility/blocker problem, not permission to disable the gate. Even a scan with no High/Critical matches cannot prove all vulnerabilities absent, attest binary provenance, verify all arm64 targets, assess reachability or validate cross-service browser auth/AMQP lifecycle under real load.

No protected main, live credential, operator database, production Compose, deployment or persistent volume is changed. Issue #255 and #259 need a final security/release review, issue #242 historical migrations and issue #251 authorization/ownership remain open. Stage45 NO_GO and all nine certification gates are not certified.

**Do not merge/deploy or mark the release GO based solely on synthetic hosted CI.**
