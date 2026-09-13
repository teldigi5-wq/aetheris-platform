# Syntra x Aetheris v2 — Execution Architecture

This document turns the long-term Syntra + Aetheris vision into an incremental engineering plan that fits the existing Aetheris platform.

## Product boundary

- **Syntra** is the owner-facing assistant: voice, text, personality, live task visibility, approvals, notifications and personal workflows.
- **Aetheris** is the execution platform: orchestration, policies, agents, models, tools, MCP, memory, observability, recovery and infrastructure.
- Models are replaceable workers. Syntra's identity and owner rules do not belong to any single LLM provider.

## Control-plane rule

Every non-trivial operation should eventually follow:

`UNDERSTAND -> PLAN -> POLICY CHECK -> APPROVAL IF NEEDED -> EXECUTE -> VERIFY -> RECORD -> REPORT`

The first implementation of this control plane lives in `orchestrator-service`.

## Agent organization

The initial catalog intentionally spans multiple disciplines instead of one generic agent:

### Executive Office
- Executive Planner
- Project Manager

### Software Engineering
- Software Architect
- Backend Engineer
- Frontend Engineer
- Desktop Engineer

Future additions: mobile engineer, database engineer, API specialist, performance engineer and developer-experience engineer.

### AI and Data
- AI Engineer
- Data Engineer

Future additions: evaluation engineer, RAG engineer, ML engineer, model-optimization engineer, vision specialist and speech engineer.

### DevOps and SRE
- DevOps Engineer
- Site Reliability Engineer

Planned capabilities include CI/CD, Docker, Kubernetes, Helm, environment provisioning, release promotion, deployment verification, SLOs, incident response, capacity planning, backup verification, chaos testing and rollback.

### QA and Release
- QA and Verification Engineer
- Release Manager

The QA organization should independently verify work from developer agents and prevent a coding agent from marking its own untested change as complete.

### Cybersecurity
- Security Architect
- Blue Team Security Officer
- Application Security Officer
- Authorized Red Team Officer
- Incident Commander

Planned additions: threat-intelligence analyst, DFIR specialist, cloud-security engineer, identity/IAM reviewer, dependency-security agent and secrets guardian.

Offensive capabilities are restricted to owner-controlled or explicitly authorized systems, isolated labs and CTFs. Target scope must not expand automatically.

### Tools and Integrations
- MCP Integration Engineer
- Automation Engineer

Aetheris should prefer structured integrations in this order: official APIs/connectors, MCP, CLI/structured files, accessibility/UI automation, then vision-based clicking only when necessary.

### Computer Operations
- PC Care and Windows Engineer

Planned capabilities: installed-app index, safe EXE launching, process control, driver/update analysis, official-source driver retrieval, storage cleanup, startup analysis, health checks, restore points, performance profiles, network diagnostics, crash analysis and rollback-first maintenance.

### Research Lab
- Research Scientist

Research outputs should distinguish source facts, model inference and recommendations, and should surface disagreement instead of inventing certainty.

### Syntra Academy
- Professor and Tutor

Planned modes: Teach Me, Hint Only, Review My Answer, Exam Mode, Solution Mode, coding labs, mastery tracking, spaced repetition and interview simulation.

### Career Intelligence
- Career and Public Profile Director

Planned capabilities: GitHub health, README and portfolio quality, profile consistency, LinkedIn review, skills synchronization, CV updates, recruiter simulation, achievement detection and career-opportunity monitoring.

Public-profile mutations should follow owner approval policy. Read-only auditing can run automatically.

### Business Intelligence
- Business Strategist

Planned capabilities: idea validation, competitor research, product requirements, pricing, simple unit economics, launch planning, documentation, operations and decision logs.

### Financial Intelligence
- Quant Researcher
- Trading Risk Officer

Aetheris Quant remains a separate subsystem. AI may research, score and explain setups, but deterministic risk controls must govern any execution path.

Live trading should progress through `OBSERVE -> PAPER -> ASSISTED LIVE -> AUTO LIVE`, with paper mode first. High-impact financial actions always remain policy-controlled.

## Deterministic policy layer

The first policy engine already reserves hard behavior for operation modes:

- **ZERO_COST**: billable actions are denied.
- **PRIVATE**: protected workflows can deny off-device data transfer.
- **HIGH** and **CRITICAL** risk: explicit owner approval is required by default.

Future owner rules should become structured/versioned policies with conditions, priorities, scope and audit history instead of relying only on prompt text.

## Task lifecycle and live UI

Tasks use explicit states so Syntra can display real work rather than fake progress:

- QUEUED
- PLANNING
- AWAITING_APPROVAL
- RUNNING
- VERIFYING
- PAUSED
- ROLLING_BACK
- COMPLETED
- FAILED
- CANCELLED

Every useful event should be streamable to the future Syntra/Aetheris UI: active agent, model, tool, current action, files affected, commands, tests, approvals, errors, retries, checkpoints and artifacts.

## MCP and tool gateway

Aetheris will act as an MCP client and may later expose owner-approved Aetheris capabilities as MCP servers.

Each integration must have:

- identity and endpoint metadata,
- approved capabilities,
- allowed data classifications,
- credential requirements,
- risk level,
- audit events,
- enable/disable controls,
- sandbox/testing status.

MCP increases tool interoperability; it does not bypass model/provider billing or platform restrictions.

## Model fabric

Planned model tiers:

1. deterministic local commands with no LLM,
2. small fast local model for routing and everyday work,
3. stronger local/free cloud models for medium tasks,
4. premium-capable models only when enabled and justified.

Routing signals should include capability, latency, throughput, privacy, context size, remaining free quota, cost, health, recent failure rate and benchmark quality.

Future components:

- local Ollama adapter,
- OpenAI-compatible adapter,
- optional 9Router adapter,
- provider health checks,
- model arena/benchmarking,
- confidence and escalation logic,
- context compression,
- model council for difficult decisions.

## Syntra intelligence systems to add

- Personal Knowledge Graph
- Project Digital Twin
- PC Digital Twin
- Proactive Intelligence Engine
- Meta-Reasoning Engine
- Verifier/Critic layer
- Contradiction Detector
- Causal/impact analysis
- Simulation/Sandbox Lab
- Decision Ledger
- Experience Replay
- Goal and Priority Manager
- Environment Awareness
- Resource Scheduler
- Self-Healing Runtime
- Safe Auto-Update Manager
- Data Lineage and Source Trust
- Context Compression
- Skill Factory
- Opportunity Radar
- Artifact Factory
- Automation Composer
- Notification Intelligence
- Offline Continuity
- Secure Remote Operations
- Forensic Audit
- Emergency Stop and Recovery

## DevOps and platform systems to add

- dedicated orchestration persistence,
- typed event bus,
- workflow engine,
- job scheduler,
- secrets vault abstraction,
- feature flags,
- configuration service,
- service discovery/health aggregation,
- artifact registry integration,
- SBOM generation,
- dependency scanning,
- container scanning,
- IaC scanning,
- release promotion environments,
- canary deployment support,
- backup/restore validation,
- disaster-recovery runbooks,
- chaos experiments,
- SLO/error-budget dashboards.

## Security principles

- least privilege and capability-based access,
- explicit target scope for security testing,
- no raw master credentials in agent prompts,
- secret redaction in logs and screenshots,
- preview/approval for high-impact actions,
- checkpoint before risky mutations,
- verifiable rollback,
- audit every sensitive capability grant,
- emergency stop revokes temporary capabilities,
- external/public actions require stronger policy than local read-only work.

## PC control principles

Syntra should be able to open approved applications and EXEs, locate files, start development workspaces and operate owner-approved tools. Unknown executables should be inspected before launch. Administrator/UAC boundaries must never be bypassed.

Driver updates, firmware, registry edits, destructive cleanup, disk changes and other high-impact maintenance require preview, trusted-source verification, backup/restore preparation and owner approval.

## Career automation principles

Aetheris may continuously inspect GitHub and, where supported, LinkedIn/portfolio data for outdated information, weak project presentation and new achievements. Automatic public changes should be constrained by owner policy; destructive or identity-changing edits require explicit approval.

## Trading principles

The trading subsystem may generate a full proposed ticket including direction, entry, TP levels, stop loss, leverage recommendation, position sizing, fees, liquidation distance, setup score and rationale.

The risk engine—not the LLM—owns hard limits such as maximum leverage, per-trade risk, daily loss, drawdown, open positions and correlated exposure. Live orders require the configured approval policy and withdrawal permissions should remain disabled for trading API keys.

## Phased implementation

### Phase A — current branch
- orchestrator-service
- typed agent catalog
- deterministic mode/risk policy foundation
- task lifecycle/event contract
- tool/MCP descriptor contracts
- CI, Docker Compose and Prometheus integration

### Phase B
- persistent task store
- server-sent events/WebSocket live event stream
- rules engine with versioned owner rules
- approval queue
- audit integration
- first local model adapter

### Phase C
- MCP client manager
- safe file/terminal/Git tools
- GitHub integration
- engineering + QA + research multi-agent workflow
- model routing and ZERO_COST enforcement

### Phase D
- Syntra desktop shell
- voice pipeline
- high-refresh live task UI
- PC app index and safe launch
- project memory and knowledge graph

### Phase E
- DevOps/SRE automation
- security operations center
- PC care/maintenance workflows
- career/public-profile automation
- Syntra Academy
- business/research divisions

### Phase F
- advanced model arena/council
- proactive intelligence
- simulation/digital twins
- secure remote companion
- deeper Aetheris Quant integration
- final installers, update/rollback and private benchmark suite

## Definition of success

Aetheris is successful when it can safely plan, delegate, execute, verify and recover real work while Syntra gives the owner a fast, smooth, transparent and interruptible experience. The project should win through system architecture, not unsupported claims that one home-trained model is universally smarter than every frontier model.
