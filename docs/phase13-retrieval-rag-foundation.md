# Phase 13 Slice 3 — Scoped Retrieval / RAG Foundation

## Certified baseline

Canonical development baseline: `b28b0eaf37ff39115f762683893b3c6f88707813`.

That Phase 13 Slice 2 merge SHA was independently observed with **33/33 push-triggered workflows successful** before this slice was opened.

## Objective

Add a bounded, evidence-addressable retrieval foundation for Syntra-Core without creating a second memory database, a second embedding abstraction, or an unscoped RAG path.

This slice deliberately composes the memory and ingestion work already proven in Stages 8 and 9:

- `EmbeddingAdapter` remains the provider abstraction;
- `KnowledgeIngestionService` remains the bounded chunk writer;
- `IncrementalKnowledgeIngestionService` remains the source-hash/revision/tombstone lifecycle;
- `Stage9AdaptiveVectorIndexService` remains the adaptive local index with lexical fallback;
- Syntra-Core adds the stricter project/workspace scope contract and evidence-addressable retrieval facade.

## What this slice adds

### Explicit project/workspace scope

`RetrievalScope` accepts only `PROJECT` or `WORKSPACE` memory boundaries and converts a safe canonical id into an exact namespace:

- `project:<project-id>`
- `workspace:<workspace-id>`

The Syntra retrieval facade never performs an unscoped search.

### Bounded ingestion

`RetrievalBounds` rejects requests before storage/index execution when they exceed the Slice 3 limits:

- document content: 120,000 characters;
- query: 4,000 characters;
- result count: 20;
- evidence excerpt: 4,000 characters;
- source id: 180 characters;
- title: 240 characters;
- tags: 32 entries, 64 characters each.

The existing Stage 8 ingestion path still performs the actual bounded chunking (1,200-character chunk target), so this slice does not fork chunking logic.

### Incremental source lifecycle

`SyntraRetrievalService.ingest(...)` delegates to the existing Stage 9 incremental ingestion service. This preserves:

- SHA-256 change detection;
- source revisions;
- superseded-chunk tombstones;
- Stage 8 deterministic index compatibility;
- Stage 9 adaptive local indexing.

`SyntraRetrievalService.tombstone(...)` is also namespace-bound and fails closed if the returned source state does not match the requested source and scope.

### Evidence-addressable retrieval

Each accepted result is mapped to a stable repository-memory address:

`aetheris-memory://<project|workspace>/<scope-id>/<knowledge-node-uuid>`

The result also carries the durable knowledge-node id, memory key, bounded excerpt, retrieval score, retrieval method and update timestamp. This gives later RAG/context assembly code a concrete evidence handle rather than only free-form text.

## Isolation and protected-data boundary

The default Syntra RAG path always calls the adaptive index with `includeProtected=false`.

The facade then checks the returned node again. If an index implementation ever returns:

- a node from another project/workspace namespace;
- a node from another memory scope; or
- protected data on the default-deny path,

the retrieval fails closed instead of silently accepting the result.

This is intentionally redundant isolation: query-time filtering plus post-retrieval verification.

Protected data may still be ingested into the existing memory system, but Slice 3 does **not** create an owner-policy bypass for feeding that protected content into model context. A later policy-aware slice must make that authorization explicit.

## Embedding truth boundary

Slice 3 reuses the existing `EmbeddingAdapter` contract instead of introducing a competing Syntra embedding interface.

The Stage 9 adaptive index can use its local neural embedding configuration or deterministic local fallback according to the already-existing Stage 9 rules. This slice does not claim that an embedding model is installed, that GPU embedding inference occurred, or that semantic quality is better than the deterministic fallback.

No cloud embedding provider is enabled by this slice.

## Acceptance evidence

`Phase13RetrievalFoundationTest` proves that:

1. project and workspace namespaces are canonical and distinct;
2. ingestion is delegated to the existing incremental source lifecycle with the exact scope namespace;
3. Syntra's automatic RAG/scope tags are added without changing the underlying storage contract;
4. retrieval requests protected data as `false` and produces stable evidence addresses;
5. a cross-scope result fails closed;
6. a protected result on the default RAG path fails closed;
7. document/query/result limits fail before storage or retrieval execution; and
8. tombstones remain bound to the exact scope namespace.

The dedicated workflow also checks that Slice 3 reuses the established ingestion/index components and does not add an HTTP client or a second embedding-provider interface under `syntracore`.

## Non-goals

This slice does not:

- expose a new public RAG endpoint;
- silently crawl repositories, disks or owner files;
- add another memory database or vector store;
- add another embedding-provider hierarchy;
- send protected memory to a model;
- claim owner-PC embedding/model benchmarks;
- assemble retrieved evidence into prompts yet;
- change Phase 12 authority, connector, approval or audit rules;
- expose local inference remotely.

## Next dependency

After the exact PR head is fully green and the canonical merge SHA is certified, Phase 13 Slice 4 should add structured memory/context assembly: typed context packs, evidence citations carried into model invocations, explicit token budgets, deduplication, recency/relevance policy, and retrieval-quality evaluation without weakening the project/workspace isolation introduced here.
