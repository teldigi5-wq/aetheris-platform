# B50 — reverify transitive Jackson 2 and Jackson 3 on the actual Maven reactor

**26 October 2026 — review-only draft. Does NOT close issue #259 or authorize a release.**

## Root cause and current branch evidence

Platform issue [#259](https://github.com/teldigi5-wq/aetheris-platform/issues/259) originally identified a vulnerable `com.fasterxml.jackson.core:jackson-databind:2.21.5` brought into User Service via SpringDoc/Swagger. This is a real **dependency-version** risk, not evidence of a reachable exploit.

The current stacked development branch already sets `<jackson-2-bom.version>2.21.8</jackson-2-bom.version>` and `<jackson-bom.version>3.1.8</jackson-bom.version>` in all four Java service POMs, and the checked-in User Service Maven dependency tree currently records Jackson 2.21.8 beside Jackson 3.1.8. This draft **does not change these apparently patched versions blindly**. Jackson 2 `jackson-annotations` legitimately uses `2.21` and must not be misidentified as an unpatched `jackson-databind` artifact.

The FasterXML 2.21.6 security patch fixes the `javax.xml.datatype.Duration/XMLGregorianCalendar` parser CPU-consumption advisory CVE-2026-68497: https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21.6 . The approved checked-in 2.21.8 is newer. Other Jackson fixes require an independent current advisory/SBOM review; this limited policy test is not a complete dependency-vulnerability scan.

## B50 implementation

- `scripts/verify_jackson_resolved.py` parses **actual Maven-generated** dependency:tree output for each module and checks exact source POM BOM properties. It rejects any unrecognized Jackson line, missing dependencies, old/misaligned `com.fasterxml.jackson` 2 versions, old/misaligned `tools.jackson` 3 versions, or missing legacy Swagger Jackson 2 User Service libraries.
- `tests/test_jackson_resolution_gate.py` runs offline regression cases for known vulnerable 2.21.5, 2.21.6/2.21.7 mismatches, old 3.1 versions, wrong core and annotation variants, missing legacy Jackson, unsupported modules, parser drift and missing/empty tree.
- Dedicated GitHub Actions workflow builds the **real project reactor tests** with `./mvnw -B -ntp -f pom.xml test`, regenerates four separate Maven `dependency:tree` files, checks them with the strict parser, and publishes the generated trees as time-limited evidence. Existing checked-in Maven evidence is used as one regression input, **never as a substitute for actual regenerated dependency output**.
- Uses GitHub-owned runners, no production data, secrets, Docker deploys or external application calls. The pinned Java 21.0.12 build image/action remains separately blocked by issue #255; B50 is not Java security patch certification.

## Completion limits / release NO_GO

Success means the tested commit's four **Maven-resolved Jackson dependency families** match the reviewed version floor and actual reactor tests pass. This does not prove all CVEs are remediated, that third-party executable assets or container OS dependencies are patched, production SBOM provenance, API exploitability, real deployed runtime classpath, or any security test outside this specific scope. Do not mark issue #259 closed until operator review verifies the current advisory set, full SBOM/OSV/CodeQL checks and the final merged/deployed build.

Keep #242 database ownership, #251 authorization, #255 Java OCI update and #259 full SBOM acceptance open. Stage45 NO_GO and all nine runtime certification gates remain unapproved. Keep PR draft, main unchanged, and production undeployed.
