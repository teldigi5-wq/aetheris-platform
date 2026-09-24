# Syntra / Aetheris First-Boot Runbook — Split Repository Runtime

This runbook is prepared before the owner's physical PC arrives. It is an acceptance procedure, not evidence that the machine has already passed.

The platform repository is `teldigi5-wq/aetheris-platform`. The independently owned AI runtime is `teldigi5-wq/aetheris-ai-runtime`, with the current certified canonical runtime SHA pinned by `architecture/ai-runtime-certification-reference.json` as `6c714d1772db2db490cd035e11a78308f26f8a63`.

## Safety boundary

Keep validated history intact. Do not expose local APIs directly to the public internet, commit credentials, weaken approval gates, or enable live-money trading to make a test pass. Quant validation remains paper/testnet-only. A GitHub runner, cloud VM, or friend PC cannot substitute for owner-PC evidence.

The four runtime-owned roots are `orchestrator-service/`, `aetheris-quant/`, `aetheris-reasoning/`, and `workstation-agent/`. Runtime-specific validation must use the independent runtime repository/revision after extraction, not an obsolete platform copy.

## 1. Record the actual host

On the owner's Windows machine, record OS, CPU, RAM, storage, GPU/driver and virtualization state before changing anything.

```powershell
Get-ComputerInfo | Select-Object WindowsProductName, WindowsVersion, OsArchitecture, CsSystemType, CsTotalPhysicalMemory, HyperVisorPresent
Get-CimInstance Win32_Processor | Select-Object Name, NumberOfCores, NumberOfLogicalProcessors
Get-CimInstance Win32_VideoController | Select-Object Name, AdapterRAM, DriverVersion
Get-Volume | Select-Object DriveLetter, FileSystemLabel, Size, SizeRemaining
```

## 2. Verify virtualization and WSL2

```powershell
Get-ComputerInfo | Select-Object HyperVisorPresent
systeminfo.exe
wsl --status
wsl --version
wsl -l -v
```

Firmware virtualization must be enabled and the selected Linux distribution must use WSL2. Fix firmware/Windows prerequisites before attempting Docker Desktop.

## 3. Verify Docker Desktop

```powershell
docker --version
docker compose version
docker info
```

`docker info` must reach the daemon. The CLI merely being installed is not sufficient evidence.

## 4. Reproduce the pinned platform toolchain

```powershell
java -version
.\mvnw.cmd -version
node --version
python --version
git --version
```

Expected platform pins are Java `21.0.12`, Maven `3.9.11` through the committed wrapper, Node `22.23.2`, and Python `3.13.15`.

## 5. Run Stage 25 host preflight

```powershell
python scripts/first_boot_preflight.py --mode host --evidence-dir build-evidence\physical-pc
```

Do not continue past mandatory failures. Keep failed evidence and add successful reruns beside it instead of deleting history.

## 6. Reproduce platform-owned builds

Build only platform-owned application modules from this repository, plus the dashboard:

```powershell
.\mvnw.cmd -f gateway\pom.xml clean package
.\mvnw.cmd -f user-service\pom.xml clean package
.\mvnw.cmd -f identity-service\pom.xml clean package
.\mvnw.cmd -f audit-service\pom.xml clean package

Push-Location dashboard
npm ci --ignore-scripts --no-audit --no-fund
npm run build
Pop-Location
```

Hash the first build, delete generated build outputs, rebuild, and compare SHA-256 values. Hosted CI reproducibility does not automatically prove cross-machine reproducibility.

## 7. Validate the independent AI runtime revision

Use the exact runtime revision from `architecture/ai-runtime-certification-reference.json`. Do not silently substitute another branch or mutable tag.

For source-based owner-PC validation, check out `teldigi5-wq/aetheris-ai-runtime` at the certified SHA and run its repository-owned build, safety, Stage 26 runtime certification, Stage 28/29 certification, and workstation-agent validation procedures.

For Compose integration, provide an exact-revision runtime image through `AETHERIS_AI_RUNTIME_IMAGE`. The platform integration compose is intentionally fail-closed if that variable is missing.

## 8. Bring up the platform core

Platform-only validation uses the source-absent-compatible core file:

```powershell
docker compose -f docker-compose.core.yml config
docker compose -f docker-compose.core.yml up --build -d postgres redis rabbitmq identity-service user-service audit-service gateway dashboard
```

In core-only mode, point `AETHERIS_ORCHESTRATOR_URI` at a separately running certified runtime endpoint when orchestrator-dependent gateway behavior is under test.

## 9. Bring up platform + external AI runtime

After the platform core and certified runtime artifact are both available:

```powershell
$env:AETHERIS_AI_RUNTIME_IMAGE = '<exact-revision-runtime-image>'
docker compose -f docker-compose.integration-external.yml config
docker compose -f docker-compose.integration-external.yml up -d postgres redis rabbitmq identity-service user-service audit-service orchestrator-service gateway dashboard
```

Inspect health instead of assuming it:

```powershell
docker compose -f docker-compose.integration-external.yml ps
docker compose -f docker-compose.integration-external.yml logs --tail=200 orchestrator-service
docker compose -f docker-compose.integration-external.yml logs --tail=200 gateway
docker compose -f docker-compose.integration-external.yml logs --tail=200 identity-service
docker compose -f docker-compose.integration-external.yml logs --tail=200 user-service
docker compose -f docker-compose.integration-external.yml logs --tail=200 audit-service
```

## 10. Enable observability only after integration health

```powershell
docker compose -f docker-compose.integration-external.yml --profile observability up -d
```

Verify Prometheus, Tempo, Loki, OpenTelemetry Collector, Alloy and Grafana individually.

## 11. AI and quant validation boundary

Local AI validation may measure model loading, latency, VRAM/RAM usage, cancellation, fallback and stability only after the physical PC exists. No benchmark numbers should be invented beforehand.

Quant remains paper trading by default and testnet-only for exchange execution validation. Real-money execution requires a separate later stage with explicit owner approval and risk controls.

## Required owner-PC evidence

The acceptance pack must include host inventory, virtualization status, WSL status, Docker daemon info, toolchain versions, two platform build-hash manifests, platform service health evidence, external orchestrator health evidence, workstation-agent validation from the independent runtime repository, and observability health evidence.

Only that evidence can close the physical-machine gap left intentionally open by the pre-PC stages.
