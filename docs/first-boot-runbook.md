# Syntra / Aetheris First-Boot Runbook

This runbook is prepared before the owner's physical PC arrives. It is an acceptance procedure, not evidence that the machine has already passed.

## Safety boundary

Keep `main` and the validated foundation history intact. Do not expose local APIs directly to the public internet, commit credentials, weaken approval gates, or enable live-money trading to make a test pass. Quant validation remains paper/testnet-only. A GitHub runner, cloud VM, or friend PC cannot substitute for owner-PC evidence.

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

## 4. Reproduce the pinned toolchain

```powershell
java -version
.\mvnw.cmd -version
node --version
python --version
git --version
```

Expected repository pins are Java `21.0.12`, Maven `3.9.11` through the committed wrapper, Node `22.23.2`, and Python `3.13.15` for Aetheris Quant/hardening tools.

## 5. Run Stage 25 host preflight

```powershell
python scripts/first_boot_preflight.py --mode host --evidence-dir build-evidence\physical-pc
```

Do not continue past mandatory failures. Keep failed evidence and add successful reruns beside it instead of deleting history.

## 6. Reproduce builds on the physical PC

Build gateway, user-service, identity-service, audit-service, orchestrator-service and workstation-agent through the committed Maven wrapper, plus the dashboard through the committed npm lock.

```powershell
.\mvnw.cmd -f gateway\pom.xml clean package
.\mvnw.cmd -f user-service\pom.xml clean package
.\mvnw.cmd -f identity-service\pom.xml clean package
.\mvnw.cmd -f audit-service\pom.xml clean package
.\mvnw.cmd -f orchestrator-service\pom.xml clean package
.\mvnw.cmd -f workstation-agent\pom.xml clean package

Push-Location dashboard
npm ci --ignore-scripts --no-audit --no-fund
npm run build
Pop-Location
```

Hash the first build, delete generated build outputs, rebuild, and compare SHA-256 values. Hosted CI reproducibility does not automatically prove cross-machine reproducibility.

## 7. Validate workstation-agent safety before activation

Run the existing Stage 11 packaging validation before any owner-PC activation:

```powershell
./workstation-agent/packaging/validate-scripts.ps1
./workstation-agent/packaging/build-package.ps1
```

The unsigned package must remain loopback-only, expose no shell capability, require no administrator rights during normal runtime, disallow production activation, and keep live money, withdrawals and transfers disabled.

## 8. Bring up the core platform

Only after host/build checks pass:

```powershell
docker compose config
docker compose up --build -d postgres redis rabbitmq identity-service user-service audit-service orchestrator-service gateway dashboard
```

Inspect health instead of assuming it:

```powershell
docker compose ps
docker compose logs --tail=200 orchestrator-service
docker compose logs --tail=200 gateway
docker compose logs --tail=200 identity-service
docker compose logs --tail=200 user-service
docker compose logs --tail=200 audit-service
```

## 9. Enable observability only after core health

```powershell
docker compose --profile observability up -d
```

Verify Prometheus, Tempo, Loki, OpenTelemetry Collector, Alloy and Grafana individually.

## 10. AI and quant validation boundary

Local AI validation may measure model loading, latency, VRAM/RAM usage, cancellation, fallback and stability only after the PC exists. No benchmark numbers should be invented beforehand.

Quant remains paper trading by default and testnet-only for exchange execution validation. Real-money execution requires a separate later stage with explicit owner approval and risk controls.

## Required owner-PC evidence

The acceptance pack must include host inventory, virtualization status, WSL status, Docker daemon info, toolchain versions, two build-hash manifests, service/orchestrator health evidence, workstation-agent safety validation, and observability health evidence.

Only that evidence can close the physical-machine gap left intentionally open by the pre-PC stages.
