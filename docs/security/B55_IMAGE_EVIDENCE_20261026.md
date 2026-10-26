# B55 — unsigned, source-bound four-image/SBOM/Grype evidence chain

**26 October 2026 — draft stacked on verified [B54 PR #288](https://github.com/teldigi5-wq/aetheris-platform/pull/288). No production approval.**

## Why this is needed

B54's [exact-head CI #40291624773](https://github.com/teldigi5-wq/aetheris-platform/actions/runs/40291624773) rebuilt four real patched Corretto 21.0.13 images, ran 79/79 real Java tests, and generated four CycloneDX inventories and four current Grype JSON reports. Its scanner database produced **zero matches** across the tested Linux AMD64 images. Those reports are valid scoped vulnerability evidence, **not image signatures, reproducible-build proof, target registry digests or deployment authorization**.

A reviewer needs a bounded way to tell which code checkout, local image, temporary exported archive, SBOM and scan output belong together, and to reject mismatched or silently edited artifacts.

## B55 implementation

- Source of truth for this CI is the exact PR **head SHA**, checked out explicitly instead of the default GitHub-generated merge commit. The workflow compares `git rev-parse HEAD` against `github.event.pull_request.head.sha`.
- The unchanged actual four-image build+scan step records one `<service>.evidence.json` per Gateway, Identity, User and Audit. Each record includes source SHA, service, locally inspected Docker `sha256:` **image ID**, SHA-256 of its temporary `docker save` archive, SHA-256 of the generated CycloneDX SBOM, SHA-256 of its Grype JSON report, and observed package/match counts. The archive is deleted after recording to avoid uploading gigantic container layers; its hash is historical build-run evidence, not independently re-verifiable from the retained ZIP.
- `scripts/verify_b55_image_evidence.py` validates **exactly four** service records, repository/name/source consistency, strict JSON (including duplicate-key refusal), digest syntax, unique Docker image IDs, and **actual preserved file hashes and package/vulnerability counts**. Invalid, swapped, missing, stale or tampered evidence exits nonzero with a fixed token rather than echoing untrusted contents.
- `tests/test_b55_image_evidence.py` contains ten synthetic negative and positive tests and never accesses production, Docker socket, a user database or external URLs.
- The same independent B53 severity gate remains untouched: the current Grype database must be fresh, high/critical/unknown findings must fail, and full raw evidence is preserved on a failure. The exact four per-image manifests are included with existing 14-day SBOM/Grype artifacts.
- The original patched Corretto image and application security tests remain in the workflow. No container image is pushed, signed, or deployed. All testing is in CI-owned ephemeral resources.

## Important limits and next production requirements

A local Docker image ID is **NOT** an immutable OCI **registry** manifest digest. SHA-256 of a temporarily exported image archive does not prove independent reproduction if the archive is deleted. B55 generates **unsigned internally consistent evidence**, not SLSA provenance or Sigstore attestations. The CI runner and checkout must be trusted and the SHA256 manifest has no independently trusted signature; a compromised runner could forge the entire bundle.

Production requires an approved source-to-build pipeline, registry-pushed final artifact digest, verified image signatures/attestations, accepted multiarchitecture packages, controlled staging and rollback, actual Backend User/Identity/Audit/Gateway authorization tests, network/TLS/credentials and owner review. The dated AL2023 `--releasever=2023.12.20261026` repository eliminates the prior moving `latest` alias but package distribution remains an external input, and cross-build bit-for-bit reproducibility is not established.

**Stage45 and the nine release gates remain NO_GO.** Issues #242 (historical databases and restore), #251 (object-level authorization), #255 (production image acceptance), and #259 (final advisories/SBOM) are still unapproved. Do not merge the draft stack or activate production based on B55 green CI alone.
