param(
  [string]$OutputDir = ""
)

$ErrorActionPreference = 'Stop'
$desktopRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$tauriRoot = Join-Path $desktopRoot 'src-tauri'
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
  $OutputDir = Join-Path $desktopRoot 'dist'
}

Write-Host 'Generating development icon...'
& (Join-Path $PSScriptRoot 'generate-dev-icon.ps1')

$tauriCliVersion = '2.8.4'
$cliAvailable = $false
try {
  $null = & cargo tauri --version 2>$null
  if ($LASTEXITCODE -eq 0) { $cliAvailable = $true }
} catch {
  $cliAvailable = $false
}

if (-not $cliAvailable) {
  Write-Host "Installing pinned tauri-cli $tauriCliVersion..."
  & cargo install tauri-cli --version $tauriCliVersion --locked
  if ($LASTEXITCODE -ne 0) { throw 'Failed to install the pinned Tauri CLI.' }
}

Push-Location $tauriRoot
try {
  & cargo generate-lockfile
  if ($LASTEXITCODE -ne 0) { throw 'Failed to resolve the desktop Cargo lock.' }

  & cargo test --locked
  if ($LASTEXITCODE -ne 0) { throw 'Syntra native tests failed.' }

  & cargo tauri build --bundles nsis
  if ($LASTEXITCODE -ne 0) { throw 'Tauri NSIS packaging failed.' }
} finally {
  Pop-Location
}

$exe = Join-Path $tauriRoot 'target\release\Syntra.exe'
$installer = Get-ChildItem -Path (Join-Path $tauriRoot 'target\release\bundle\nsis') -Filter '*.exe' -File | Select-Object -First 1
if (-not (Test-Path $exe -PathType Leaf)) { throw 'Syntra.exe was not produced.' }
if ($null -eq $installer) { throw 'Syntra NSIS installer was not produced.' }

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
Copy-Item $exe (Join-Path $OutputDir 'Syntra.exe') -Force
Copy-Item $installer.FullName (Join-Path $OutputDir 'Syntra-Setup-0.3.0-x64.exe') -Force

$manifest = @(
  Get-FileHash (Join-Path $OutputDir 'Syntra.exe') -Algorithm SHA256
  Get-FileHash (Join-Path $OutputDir 'Syntra-Setup-0.3.0-x64.exe') -Algorithm SHA256
) | ForEach-Object { "{0}  {1}" -f $_.Hash.ToLowerInvariant(), (Split-Path $_.Path -Leaf) }
$manifest | Set-Content -Path (Join-Path $OutputDir 'SHA256SUMS.txt') -Encoding ascii

Write-Host 'Syntra Windows development package ready:'
Get-ChildItem $OutputDir | Select-Object Name, Length
Get-Content (Join-Path $OutputDir 'SHA256SUMS.txt')
