# Phase 13 Slice 4 — Structured Memory / Context Assembly

## Certified baseline

Canonical development baseline: `b16a47547932541b4aae752e3187881537615298`.

That Phase 13 Slice 3 merge was observed with **34/34 push-triggered workflows successful** before this slice was opened.

## Objective

Turn the scoped retrieval evidence from Slice 3 into a bounded, typed context pack that can be carried into local model invocation without weakening project/workspace isolation or inventing evidence provenance.

Slice 4 deliberately remains an assembly layer. It does not add another memory store, vector index, embedding abstraction, network client, or remote inference surface.

## What this slice adds

### Typed context contracts

The new context path is explicit:

`ContextAssemblyRequest -> SyntraRetrievalService -> ContextPack -> ModelInvocation`

A `ContextPack` carries:

- the exact `RetrievalScope` used for retrieval;
- the retrieval query;
- selected typed `ContextEvidence` entries;
- exact `aetheris-memory://...` citations in selected order;
- deterministic context-budget accounting;
- a structural `ContextQualityAssessment`; and
- the assembly timestamp.

### Exact scope and evidence identity checks

Context assembly independently re-checks every candidate returned from retrieval. A candidate is rejected if:

- its `RetrievalScope` is not exactly the requested project/workspace scope; or
- its citation does not exactly match `aetheris-memory://<kind>/<scope-id>/<node-id>`.

This is intentionally redundant with Slice 3 retrieval isolation. A later layer must not silently trust a malformed or cross-scope retrieval result.

### Deterministic deduplication and ranking

Candidates are deduplicated by durable knowledge-node UUID. If duplicate records for the same node disagree on the evidence address or memory key, assembly fails closed.

For a consistent duplicate, the stronger retrieval score wins. Equal-score duplicates prefer the newer evidence timestamp.

Unique evidence is then ranked by:

1. retrieval score, descending;
2. evidence `updatedAt`, newest first; and
3. evidence address, lexicographically, as the final deterministic tie-breaker.

This is an explicit relevance-then-recency policy. It does **not** claim that retrieval score is a calibrated probability of correctness.

### Explicit context budgets

`ContextBudget` places independent limits on:

- total assembled evidence budget;
- per-evidence budget; and
- selected evidence item count.

The safe default is:

- 6,000 context token units total;
- 1,500 token units per evidence item; and
- at most 8 evidence items.

Slice 4 intentionally uses the UTF-8 byte length of the rendered evidence block as a deterministic, conservative accounting unit. This is **not** claimed to be the exact token count of Ollama, Qwen, Llama, Gemma, or any other model tokenizer. Provider-specific tokenizer accounting remains future work.

When an evidence excerpt exceeds the available item budget, the service truncates it by Unicode code point and appends an ellipsis while preserving the citation. Evidence that cannot fit its metadata plus at least some text is omitted.

### Citation continuity into model invocation

`ModelInvocation` now carries an immutable ordered list of evidence addresses while retaining its original three-argument constructor for existing callers.

Only `aetheris-memory://...` citations are accepted and duplicates are rejected.

`ContextPack.toModelInvocation(...)` renders the selected evidence into the local prompt and carries the same ordered citations as invocation metadata. The prompt explicitly labels retrieved evidence as **untrusted reference data**, not executable instructions, policy, approval, or authorization.

### Structural retrieval-quality evidence

`ContextQualityAssessment` records only things the runtime actually observed:

- raw candidate count;
- unique candidate count;
- selected count;
- duplicate count;
- budget-omitted count;
- average selected retrieval score;
- minimum selected score;
- maximum selected score; and
- selection coverage.

These are structural retrieval/assembly measurements. They are **not** semantic answer-quality, hallucination, factuality, or benchmark scores.

## Security boundary

Slice 4 preserves the Slice 3 default-deny protected-data path because all evidence still comes through `SyntraRetrievalService.retrieve(...)`, which requests `includeProtected=false` and validates the returned scope.

The context layer also treats evidence text as untrusted. That reduces structural prompt-boundary ambiguity, but this slice does **not** claim immunity to prompt injection or adversarial model behavior. Consequential actions remain governed by the Phase 12 authority/approval/audit boundaries rather than by text generated from retrieved context.

## Acceptance evidence

`Phase13ContextAssemblyTest` proves that:

1. duplicate node evidence is collapsed deterministically;
2. higher retrieval score wins and equal relevance uses recency as the tie-breaker;
3. exact citations survive into `ModelInvocation` metadata and rendered input;
4. legacy three-argument model invocations remain compatible;
5. context and per-evidence budgets truncate and omit evidence without dropping citation identity;
6. cross-scope evidence fails closed;
7. forged or inconsistent evidence addresses fail closed; and
8. an empty retrieval result creates an explicit empty context pack instead of inventing citations.

The dedicated workflow additionally verifies that Slice 4 adds no HTTP/network client and that the expected scope, citation, budget and structural-quality surfaces are present.

## Non-goals / truth boundary

This slice does not claim that:

- a provider-specific tokenizer was used;
- local model weights are installed on the owner PC;
- an RTX GPU or VRAM amount was observed;
- context improves model answer quality;
- prompt injection is solved;
- semantic retrieval quality has been benchmarked;
- TTFT, tokens/sec, RAM, VRAM, crash rate, or context-limit measurements have been collected on owner hardware;
- protected memory is authorized for model context;
- a remote/public RAG endpoint exists; or
- local model output can bypass tool authority, approvals, policies, or audit controls.

## Next dependency

After the exact PR head is fully green and the canonical merge SHA is certified, Phase 13 Slice 5 should connect these typed context packs to the local inference orchestration path with task-aware routing, cancellation/stream continuity, citation-preserving response evidence, and evaluation hooks. Owner-hardware benchmark claims should remain blocked until they are measured on the actual target machine.
