# Phase 10 — Governed Computer-Use Production Hardening

Phase 10 is a post-roadmap implementation pass. The canonical numbered repository roadmap remains **Stage 34 / 34 COMPLETE**; this work does not invent a Stage 35.

The master build order places **computer-use integrations** immediately after the memory production slice. Phase 10 therefore hardens the existing Aetheris Operator browser path instead of duplicating it.

## Scope

This slice closes one explicit Operator v1 repository gap: a browser click could not previously claim a successful download because there was no downloaded-file path/checksum evidence boundary.

Phase 10 adds:

- a dedicated browser download evidence service;
- a configurable local download root;
- a fresh isolated download directory per browser workflow containing a download action;
- Chrome/Edge WebDriver download preferences bound to that directory;
- an explicit expected-filename contract supplied through the existing ephemeral `fileRef` map;
- rejection of absolute paths, traversal, directory separators and temporary download suffixes;
- rejection of pre-existing targets so stale files cannot be reused as success evidence;
- regular-file and symbolic-link checks;
- a configurable maximum evidence size, defaulting to 50 MiB;
- bounded completion waiting with a maximum of 30 seconds;
- stability checks before hashing;
- SHA-256 evidence over the completed artifact;
- structured root-relative path, size and checksum evidence on the browser action result;
- targeted tests for containment, stale-file rejection, size limits, symlinks, timeouts and planner behavior;
- a dedicated `Phase 10 Computer Use Production Proof` GitHub Actions workflow.

## What Phase 10 does not do

Phase 10 does **not**:

- enable the browser runtime by default;
- set `aetheris.browser.physical-validated=true`;
- claim that ChromeDriver or EdgeDriver is installed on the owner's future PC;
- claim that production-site selectors are currently valid;
- bypass owner approval for external mutations;
- weaken `PRIVATE` protected-data handling;
- enable live-money browser actions;
- scan arbitrary download directories looking for a likely file;
- treat a click, browser message or model statement as proof of a completed download.

Physical-machine status therefore remains:

```text
BLOCKED_PENDING_HARDWARE
```

## Download contract

A `DOWNLOAD` browser action now requires:

- a prior allowlisted browser origin;
- an explicit CSS selector to activate;
- a non-empty `fileRef`;
- an optional timeout between 1 and 30 seconds.

At execution time, `filesByRef[fileRef]` supplies the expected completed filename. The filename is intentionally ephemeral rather than embedded in the durable workflow plan.

`DOWNLOAD` has a minimum effect of `LOCAL_DRAFT` because it creates a local artifact. Declaring a weaker effect cannot downgrade it to observation.

## Evidence contract

A successful download returns:

- `evidenceRef = sha256:<digest>`;
- `artifactEvidence.relativePath` — relative to the configured download root, never an absolute owner filesystem path;
- `artifactEvidence.sizeBytes`;
- `artifactEvidence.sha256`.

The file contents are not copied into invocation audit metadata.

A download fails closed when the expected artifact is missing, stale, path-unsafe, a symlink, not a regular file, too large, unstable, or cannot be hashed.

## Runtime configuration

Defaults:

```properties
aetheris.browser.runtime-enabled=false
aetheris.browser.physical-validated=false
aetheris.browser.download-root=build/browser-downloads
aetheris.browser.download-max-bytes=52428800
```

The existing browser runtime and physical-validation gates remain authoritative. Repository tests can prove the filesystem evidence boundary without pretending that the future physical browser has been validated.

## CI proof

The dedicated workflow runs:

```text
AetherisBrowserOperatorTest
BrowserDownloadEvidenceServiceTest
```

These tests prove repository-level behavior for:

- physical truth remaining fail-closed;
- download planning requiring an explicit selector/file reference;
- download effect escalation to `LOCAL_DRAFT`;
- SHA-256 and relative-path evidence;
- traversal and Windows-style path escape rejection;
- stale target rejection;
- maximum-size enforcement;
- symlink rejection where the platform supports symlinks;
- missing-file timeout behavior.

Hosted CI remains repository evidence only. The physical-PC download proof must later exercise a real local Chrome/Edge + WebDriver session and independently compare the resulting artifact checksum.

## Completion gate

Phase 10 is complete only when:

1. the exact PR head passes all applicable repository workflows, including the dedicated Phase 10 proof and Stage 26 safety certification;
2. the PR is merged to the canonical development branch;
3. the exact canonical merge SHA passes the full post-merge workflow set with no failed or cancelled required proof;
4. physical status still remains truthful unless owner-PC evidence exists.
