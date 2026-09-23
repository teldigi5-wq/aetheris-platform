# Aetheris Operator v1 — Governed Generic Browser

Aetheris Operator extends Reach with a generic local browser-control plane for web applications that do not have a dedicated official API, MCP connector or CLI adapter.

The design goal is a Work-mode-like workflow while preserving Aetheris ownership, privacy, deterministic policy and evidence rules:

```text
Syntra request
  -> planner
  -> Aetheris owner policy
  -> Reach / dedicated API when available
  -> Generic Browser Operator only when appropriate
  -> local W3C WebDriver
  -> website
  -> verification evidence
  -> completion or UNVERIFIED
```

## What is implemented in the repository

The orchestrator now contains:

- a generic browser workflow model;
- explicit domain allowlists;
- HTTP/HTTPS-only navigation;
- protection against embedded URL credentials and cloud metadata endpoints;
- action-level effect and risk classification;
- deterministic owner-policy evaluation;
- `PRIVATE` handling for protected context sent to remote sites;
- approval requirements for external changes;
- hard blocking of live-money browser actions in the default trusted path;
- emergency-stop enforcement before browser execution;
- invocation auditing that redacts literal typed values and file contents;
- local W3C WebDriver execution support for Chrome/Edge-compatible drivers;
- runtime redirect checks after every browser action;
- screenshot evidence hashing;
- bounded text extraction;
- isolated browser download directories with explicit expected filenames;
- downloaded-file size, regular-file, symlink, containment and SHA-256 verification;
- structured relative-path/size/checksum evidence for verified downloads;
- REST endpoints for status, plan and execute.

API root:

```text
/api/orchestrator/operator/browser
```

Endpoints:

```text
GET  /status
POST /plan
POST /execute
```

## Generic actions

Operator v1 supports the following action contracts:

| Action | Purpose | Default minimum effect |
| --- | --- | --- |
| `NAVIGATE` | Open an allowlisted HTTP/HTTPS URL | `OBSERVE` |
| `CLICK` | Activate a CSS-selected page element | `EXTERNAL_CHANGE` |
| `TYPE` | Enter text from an ephemeral value reference | `LOCAL_DRAFT` |
| `UPLOAD` | Populate a file input from a supplied file reference | `EXTERNAL_CHANGE` |
| `DOWNLOAD` | Click an explicit selector and verify one expected downloaded artifact | `LOCAL_DRAFT` |
| `SCREENSHOT` | Capture screenshot evidence hash | `OBSERVE` |
| `EXTRACT_TEXT` | Read bounded text from a CSS-selected element | `OBSERVE` |
| `WAIT` | Bounded wait up to 30 seconds | `OBSERVE` |

`CLICK` is intentionally classified conservatively because a generic button may publish, deploy, delete, save or submit data. `DOWNLOAD` is treated as a local side effect rather than observation because it writes an artifact to disk.

## Owner approval

External mutations are high risk by default and therefore require owner approval through the existing Aetheris approval system.

A browser execution that requires approval checks for:

```text
browser:workflow
```

against the associated task before execution.

Examples that should require approval include:

- publishing a LinkedIn profile change;
- deploying from a web dashboard;
- deleting a cloud resource;
- submitting account-setting changes;
- uploading a file to an external service.

Read-only navigation/extraction and local-only download evidence can remain automatic when owner policy permits them. Download support does not bypass the independent runtime, physical-validation, emergency-stop or financial boundaries.

## LinkedIn example

A future Syntra command such as:

> Update my LinkedIn Featured section with the newest portfolio deployment.

can be decomposed into:

1. use a dedicated LinkedIn connector if an approved one exists;
2. otherwise create a generic browser workflow with `linkedin.com` in the explicit domain allowlist;
3. navigate and inspect the current profile;
4. draft fields through `TYPE` using ephemeral value references;
5. require owner approval before the final mutation click;
6. capture post-change evidence;
7. verify the resulting profile state before claiming success.

Operator v1 does not contain LinkedIn-specific selector recipes. Those belong in the higher-level site workflow adapter so fragile selectors do not become policy authority.

## Vercel example

For deployment, Aetheris should still prefer:

```text
Vercel official API / CLI -> MCP connector -> generic browser fallback
```

The generic browser can support a Vercel dashboard workflow when no better route exists, but publishing/deployment clicks remain approval-gated and must be verified afterward.

## Runtime

The repository adapter talks to a **loopback-local W3C WebDriver** endpoint only. Remote WebDriver endpoints are rejected by design.

Default configuration:

```text
aetheris.browser.runtime-enabled=false
aetheris.browser.physical-validated=false
aetheris.browser.webdriver-endpoint=http://127.0.0.1:9515/
aetheris.browser.browser-name=chrome
aetheris.browser.download-root=build/browser-downloads
aetheris.browser.download-max-bytes=52428800
```

The download root is resolved to an absolute path at runtime. Each workflow containing a `DOWNLOAD` action receives a fresh isolated subdirectory. Chrome/Edge download preferences point at that subdirectory for the WebDriver session.

These defaults mean repository code can compile and be reviewed without pretending the owner's physical browser is ready. On the target machine, a validated ChromeDriver/EdgeDriver-compatible runtime can be enabled only after physical setup and evidence-driven validation.

## Domain containment

Every workflow must provide at least one allowed domain. A navigation target must be an exact allowed domain or its subdomain.

The runtime checks the current URL **after every action**. An unexpected redirect outside the workflow allowlist causes the workflow to fail closed.

This prevents a generic browser sequence from silently wandering to unrelated origins.

## Secret handling

Browser plans do not embed literal typed values or literal file paths. `TYPE` uses `valueRef`; both `UPLOAD` and `DOWNLOAD` use `fileRef`.

For a download, `filesByRef[fileRef]` supplies the expected completed filename at execution time. The filename must be a single path-safe filename: absolute paths, traversal, directory separators and temporary download suffixes are rejected.

Invocation audit metadata records that values/files were redacted. It does not log literal typed secrets, uploaded file contents or downloaded file contents.

Browser profiles, cookies, password stores and credential directories are not recursively scanned or committed to the repository.

## Download evidence boundary

A download click is not success by itself.

Before a `DOWNLOAD` action clicks its selector, Aetheris requires that the expected target filename does not already exist in the fresh execution directory. After the click it waits for that exact file and accepts it only when all of the following are true:

- the expected name is one path-safe filename;
- the target remains inside the isolated configured download root;
- the target is a regular file and not a symbolic link;
- the completed file is stable before hashing;
- the file does not exceed `aetheris.browser.download-max-bytes`;
- SHA-256 can be computed without reading beyond the configured size boundary.

Successful evidence contains only the root-relative artifact path, byte size and SHA-256 digest. File contents are not copied into browser audit metadata.

A missing, renamed, oversized, symlinked, path-escaping or unstable file fails closed. The default wait is 15 seconds and the action-level maximum is 30 seconds.

## Evidence rules

A successful mutation must have verification evidence. Operator requires the workflow planner to surface before/after and final-state evidence requirements for external mutations.

Screenshot actions return a SHA-256 evidence reference rather than placing raw screenshot bytes into audit metadata. Download actions return SHA-256 evidence plus structured artifact metadata only after the downloaded-file boundary above has passed.

## Financial boundary

`FINANCIAL_CHANGE` is hard-blocked in the generic browser path.

The generic browser must not be used to bypass Aetheris trading boundaries, execute live-money orders, perform withdrawals or transfer funds. Those remain outside the default trusted AI execution path.

## Physical truth boundary

Physical-machine status remains:

```text
BLOCKED_PENDING_HARDWARE
```

The repository now has a real generic-browser execution adapter and repository-tested downloaded-file evidence logic, but this does **not** prove:

- ChromeDriver or EdgeDriver is installed on the future PC;
- Chrome/Edge launches correctly under automation;
- login sessions work;
- LinkedIn/Vercel selectors are stable;
- Chrome/Edge download preferences behave correctly on the owner's real machine;
- real-site uploads/downloads work on the target machine;
- browser evidence is sufficient for every site;
- long-running web workflows are reliable.

Physical-PC validation remains pending. Hosted CI is repository evidence, not physical browser validation.

## Next physical-browser validation

When the owner PC is available:

1. install/identify an owner-approved browser and matching W3C driver;
2. keep the driver bound to loopback only;
3. run a read-only local test page;
4. validate allowlisted navigation and redirect blocking;
5. validate screenshots and bounded extraction;
6. validate a non-sensitive form draft;
7. validate one sandboxed test download and independently compare its path, size and SHA-256 evidence;
8. validate owner approval before an external mutation;
9. validate emergency stop while a workflow is active;
10. test a dedicated Vercel workflow;
11. test a dedicated LinkedIn workflow only after selectors and account boundaries are reviewed.

Only after evidence from these steps should `aetheris.browser.physical-validated` be enabled on the real machine.
