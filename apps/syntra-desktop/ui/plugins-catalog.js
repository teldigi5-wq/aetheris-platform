/* Curated, untrusted research snapshot from Aetheris runtime draft PR #18.
 * No network fetch, imports, execution, installed plugins or verified source licenses.
 * Generation/activation remains BLOCKED_PENDING_HARDWARE. */
(() => {
  "use strict";
  const payload = {
  "schemaVersion": 1,
  "snapshotOrigin": "https://github.com/teldigi5-wq/aetheris-ai-runtime/pull/18",
  "snapshotSourceHead": "cfde637963118ba175393e301d4b6835de606019",
  "snapshotDate": "2026-10-13",
  "catalogMode": "SNAPSHOT_READ_ONLY",
  "physicalPcStatus": "BLOCKED_PENDING_HARDWARE",
  "noExternalFetch": true,
  "automaticInstallAllowed": false,
  "executionAllowed": false,
  "label": "25 research entries • 14 repositories • 5 read-only templates • 6 directories",
  "entries": [
    {
      "id": "agentskills-standard",
      "type": "repository",
      "name": "Agent Skills Format",
      "category": "skill-format",
      "description": "Review standard SKILL.md format and progressive disclosure",
      "sourceUrl": "https://github.com/agentskills/agentskills",
      "reportedLicense": "Apache-2.0",
      "capabilityHints": [
        "Skill descriptions",
        "Template validation"
      ],
      "permissionReview": [
        "Documentation review"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "official-mcp-registry",
      "type": "repository",
      "name": "MCP Registry",
      "category": "mcp-discovery",
      "description": "Research published MCP server metadata; registration is not trust",
      "sourceUrl": "https://github.com/modelcontextprotocol/registry",
      "reportedLicense": "NOASSERTION",
      "capabilityHints": [
        "Published server metadata",
        "Registry review"
      ],
      "permissionReview": [
        "Public registry read"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "mcp-reference-servers",
      "type": "repository",
      "name": "MCP Reference Servers",
      "category": "mcp-examples",
      "description": "Review reference MCP servers and design patterns, not production-ready plugins",
      "sourceUrl": "https://github.com/modelcontextprotocol/servers",
      "reportedLicense": "NOASSERTION",
      "capabilityHints": [
        "MCP example patterns"
      ],
      "permissionReview": [
        "Code review"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "github-mcp",
      "type": "repository",
      "name": "GitHub MCP Server",
      "category": "source-control",
      "description": "Evaluate narrowly scoped GitHub metadata reads before grants",
      "sourceUrl": "https://github.com/github/github-mcp-server",
      "reportedLicense": "MIT",
      "capabilityHints": [
        "Repository metadata",
        "Potential account write operations"
      ],
      "permissionReview": [
        "Network access",
        "Repository read",
        "Potential repository write"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "playwright-mcp",
      "type": "repository",
      "name": "Playwright MCP",
      "category": "browser-automation",
      "description": "Evaluate browser automation only inside permissioned user workflow",
      "sourceUrl": "https://github.com/microsoft/playwright-mcp",
      "reportedLicense": "Apache-2.0",
      "capabilityHints": [
        "Browser interaction",
        "Potential page form writes"
      ],
      "permissionReview": [
        "Browser control",
        "Network access",
        "Potential form submission"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "ollama",
      "type": "repository",
      "name": "Ollama",
      "category": "local-llm",
      "description": "Research opt-in local model serving and hardware compatibility",
      "sourceUrl": "https://github.com/ollama/ollama",
      "reportedLicense": "MIT",
      "capabilityHints": [
        "Local inference",
        "Model downloads"
      ],
      "permissionReview": [
        "Local models and disk",
        "GPU/CPU compute",
        "Potential model downloads"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "comfyui",
      "type": "repository",
      "name": "ComfyUI",
      "category": "local-media",
      "description": "Research local image/video backend; model licenses and GPU tests separate",
      "sourceUrl": "https://github.com/Comfy-Org/ComfyUI",
      "reportedLicense": "GPL-3.0",
      "capabilityHints": [
        "Image/video workflows",
        "Custom-node execution"
      ],
      "permissionReview": [
        "GPU and local files",
        "Checkpoint licensing",
        "Potential custom code execution"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "faster-whisper",
      "type": "repository",
      "name": "Faster Whisper",
      "category": "local-speech",
      "description": "Research local transcription; model weight licenses separate",
      "sourceUrl": "https://github.com/SYSTRAN/faster-whisper",
      "reportedLicense": "MIT",
      "capabilityHints": [
        "Speech recognition or synthesis",
        "Model downloads"
      ],
      "permissionReview": [
        "Audio data access",
        "Local compute",
        "Model weights"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "piper-tts",
      "type": "repository",
      "name": "Piper TTS",
      "category": "local-speech",
      "description": "Research local speech synthesis; voice licenses separate",
      "sourceUrl": "https://github.com/OHF-Voice/piper1-gpl",
      "reportedLicense": "GPL-3.0",
      "capabilityHints": [
        "Speech recognition or synthesis",
        "Model downloads"
      ],
      "permissionReview": [
        "Audio data access",
        "Local compute",
        "Model weights"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "qdrant",
      "type": "repository",
      "name": "Qdrant",
      "category": "local-memory",
      "description": "Research vector search with owner-scoped data access",
      "sourceUrl": "https://github.com/qdrant/qdrant",
      "reportedLicense": "Apache-2.0",
      "capabilityHints": [
        "Indexing",
        "Owner-scoped retrieval"
      ],
      "permissionReview": [
        "Document access",
        "Local storage",
        "Owner isolation"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "chroma",
      "type": "repository",
      "name": "Chroma",
      "category": "local-memory",
      "description": "Research alternative vector search and provenance",
      "sourceUrl": "https://github.com/chroma-core/chroma",
      "reportedLicense": "Apache-2.0",
      "capabilityHints": [
        "Indexing",
        "Owner-scoped retrieval"
      ],
      "permissionReview": [
        "Document access",
        "Local storage",
        "Owner isolation"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "langchain",
      "type": "repository",
      "name": "LangChain",
      "category": "orchestration-library",
      "description": "Evaluate optional agent-library patterns without replacing safety policy",
      "sourceUrl": "https://github.com/langchain-ai/langchain",
      "reportedLicense": "MIT",
      "capabilityHints": [
        "Agent workflow patterns",
        "Tool orchestration"
      ],
      "permissionReview": [
        "Runtime code",
        "External tool scopes"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "llama-index",
      "type": "repository",
      "name": "LlamaIndex",
      "category": "retrieval-library",
      "description": "Evaluate optional retrieval and grounded document indexing",
      "sourceUrl": "https://github.com/run-llama/llama_index",
      "reportedLicense": "MIT",
      "capabilityHints": [
        "Document parsing",
        "Retrieval"
      ],
      "permissionReview": [
        "File content read",
        "Index storage"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "n8n",
      "type": "repository",
      "name": "n8n",
      "category": "workflow-automation",
      "description": "Review source-available fair-code terms, network access and execution authority",
      "sourceUrl": "https://github.com/n8n-io/n8n",
      "reportedLicense": "NOASSERTION",
      "capabilityHints": [
        "Workflow orchestration",
        "Potential external actions"
      ],
      "permissionReview": [
        "Network connections",
        "External account actions",
        "Credentials"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "github-readonly",
      "type": "skill",
      "name": "Github Readonly",
      "category": "read-only-skill",
      "description": "Inspect GitHub repository metadata, PRs, checks and changes without mutations.",
      "sourceUrl": "https://github.com/teldigi5-wq/aetheris-ai-runtime/blob/cfde637963118ba175393e301d4b6835de606019/skills/github-readonly/SKILL.md",
      "reportedLicense": "Apache-2.0 (repository; verify separately)",
      "capabilityHints": [
        "Read-only template",
        "Tool authority not wired"
      ],
      "permissionReview": [
        "Proposed only: github.repo.read",
        "Proposed only: github.commit.read",
        "Proposed only: github.pr.read",
        "Proposed only: github.checks.read"
      ],
      "status": "TEMPLATE_ONLY",
      "trust": "LOCAL_SOURCE_TEMPLATE",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "incident-investigation",
      "type": "skill",
      "name": "Incident Investigation",
      "category": "read-only-skill",
      "description": "Investigate incidents from pre-authorized metrics, logs and traces without changing systems.",
      "sourceUrl": "https://github.com/teldigi5-wq/aetheris-ai-runtime/blob/cfde637963118ba175393e301d4b6835de606019/skills/incident-investigation/SKILL.md",
      "reportedLicense": "Apache-2.0 (repository; verify separately)",
      "capabilityHints": [
        "Read-only template",
        "Tool authority not wired"
      ],
      "permissionReview": [
        "Proposed only: observability.health.read",
        "Proposed only: observability.metrics.read",
        "Proposed only: observability.logs.read",
        "Proposed only: observability.traces.read"
      ],
      "status": "TEMPLATE_ONLY",
      "trust": "LOCAL_SOURCE_TEMPLATE",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "mcp-extension-review",
      "type": "skill",
      "name": "Mcp Extension Review",
      "category": "read-only-skill",
      "description": "Research published MCP server metadata against trusted evidence without connecting, executing or approving tools.",
      "sourceUrl": "https://github.com/teldigi5-wq/aetheris-ai-runtime/blob/cfde637963118ba175393e301d4b6835de606019/skills/mcp-extension-review/SKILL.md",
      "reportedLicense": "Apache-2.0 (repository; verify separately)",
      "capabilityHints": [
        "Read-only template",
        "Tool authority not wired"
      ],
      "permissionReview": [
        "Proposed only: mcp.registry.read",
        "Proposed only: github.repo.read"
      ],
      "status": "TEMPLATE_ONLY",
      "trust": "LOCAL_SOURCE_TEMPLATE",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "license-provenance-review",
      "type": "skill",
      "name": "License Provenance Review",
      "category": "read-only-skill",
      "description": "Review exact source repository, declared license and dependency provenance without installing packages or approving licensing.",
      "sourceUrl": "https://github.com/teldigi5-wq/aetheris-ai-runtime/blob/cfde637963118ba175393e301d4b6835de606019/skills/license-provenance-review/SKILL.md",
      "reportedLicense": "Apache-2.0 (repository; verify separately)",
      "capabilityHints": [
        "Read-only template",
        "Tool authority not wired"
      ],
      "permissionReview": [
        "Proposed only: github.repo.read",
        "Proposed only: github.commit.read"
      ],
      "status": "TEMPLATE_ONLY",
      "trust": "LOCAL_SOURCE_TEMPLATE",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "local-model-readiness",
      "type": "skill",
      "name": "Local Model Readiness",
      "category": "read-only-skill",
      "description": "Review owner-authorized local model inventory and read-only health telemetry without loading models or certifying a GPU.",
      "sourceUrl": "https://github.com/teldigi5-wq/aetheris-ai-runtime/blob/cfde637963118ba175393e301d4b6835de606019/skills/local-model-readiness/SKILL.md",
      "reportedLicense": "Apache-2.0 (repository; verify separately)",
      "capabilityHints": [
        "Read-only template",
        "Tool authority not wired"
      ],
      "permissionReview": [
        "Proposed only: observability.health.read",
        "Proposed only: models.catalog.read"
      ],
      "status": "TEMPLATE_ONLY",
      "trust": "LOCAL_SOURCE_TEMPLATE",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "agent-skills",
      "type": "directory",
      "name": "Agent Skills Hub",
      "category": "skills-format",
      "description": "External directory for researching extension candidates; not a connected plugin or installation source.",
      "sourceUrl": "https://agentskills.io",
      "reportedLicense": "Varies by listing",
      "capabilityHints": [
        "Candidate discovery",
        "Manual source verification"
      ],
      "permissionReview": [
        "User-initiated source browsing only",
        "License and security review"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "official-mcp",
      "type": "directory",
      "name": "MCP Server Registry",
      "category": "mcp-server-index",
      "description": "External directory for researching extension candidates; not a connected plugin or installation source.",
      "sourceUrl": "https://registry.modelcontextprotocol.io",
      "reportedLicense": "Varies by listing",
      "capabilityHints": [
        "Candidate discovery",
        "Manual source verification"
      ],
      "permissionReview": [
        "User-initiated source browsing only",
        "License and security review"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "python-pypi",
      "type": "directory",
      "name": "Python Package Index",
      "category": "python-packages",
      "description": "External directory for researching extension candidates; not a connected plugin or installation source.",
      "sourceUrl": "https://pypi.org",
      "reportedLicense": "Varies by listing",
      "capabilityHints": [
        "Candidate discovery",
        "Manual source verification"
      ],
      "permissionReview": [
        "User-initiated source browsing only",
        "License and security review"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "npm",
      "type": "directory",
      "name": "npm Packages",
      "category": "javascript-packages",
      "description": "External directory for researching extension candidates; not a connected plugin or installation source.",
      "sourceUrl": "https://www.npmjs.com",
      "reportedLicense": "Varies by listing",
      "capabilityHints": [
        "Candidate discovery",
        "Manual source verification"
      ],
      "permissionReview": [
        "User-initiated source browsing only",
        "License and security review"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "hugging-face-models",
      "type": "directory",
      "name": "Hugging Face Models",
      "category": "model-weights-and-cards",
      "description": "External directory for researching extension candidates; not a connected plugin or installation source.",
      "sourceUrl": "https://huggingface.co/models",
      "reportedLicense": "Varies by listing",
      "capabilityHints": [
        "Candidate discovery",
        "Manual source verification"
      ],
      "permissionReview": [
        "User-initiated source browsing only",
        "License and security review"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    },
    {
      "id": "ollama-model-library",
      "type": "directory",
      "name": "Ollama Model Library",
      "category": "local-model-library",
      "description": "External directory for researching extension candidates; not a connected plugin or installation source.",
      "sourceUrl": "https://ollama.com/library",
      "reportedLicense": "Varies by listing",
      "capabilityHints": [
        "Candidate discovery",
        "Manual source verification"
      ],
      "permissionReview": [
        "User-initiated source browsing only",
        "License and security review"
      ],
      "status": "REFERENCE_ONLY",
      "trust": "UNTRUSTED_EXTERNAL",
      "licenseReview": "NOT_REVIEWED",
      "installed": false,
      "enabled": false,
      "executionEnabled": false,
      "sourceRevisionReviewed": false
    }
  ]
};
  for (const entry of payload.entries) {
    Object.freeze(entry.capabilityHints);
    Object.freeze(entry.permissionReview);
    Object.freeze(entry);
  }
  Object.freeze(payload.entries);
  Object.freeze(payload);
  Object.defineProperty(window, "SyntraExtensionCatalog", { value: payload, writable: false, configurable: false });
})();
