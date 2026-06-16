param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

function Get-SystemPython {
  $pyLauncher = Get-Command "py" -ErrorAction SilentlyContinue
  if ($pyLauncher) {
    try {
      & $pyLauncher.Source -3 --version *> $null
      if ($LASTEXITCODE -eq 0) { return @($pyLauncher.Source, "-3") }
    } catch { }
  }

  $python = Get-Command "python" -ErrorAction SilentlyContinue
  if ($python) {
    try {
      & $python.Source --version *> $null
      if ($LASTEXITCODE -eq 0) { return @($python.Source) }
    } catch { }
  }

  return $null
}

function Ensure-PortablePython([string]$rootPath) {
  $pythonRoot = Join-Path $rootPath "tools\python-embed"
  $pythonExe = Join-Path $pythonRoot "python.exe"
  if (Test-Path -LiteralPath $pythonExe) {
    return @($pythonExe)
  }

  $zipUrl = "https://www.python.org/ftp/python/3.11.9/python-3.11.9-embed-amd64.zip"
  $tempRoot = Join-Path $env:TEMP ("minecraft-live-python-" + [guid]::NewGuid().ToString("N"))
  $zipPath = Join-Path $tempRoot "python-embed.zip"
  $extractPath = Join-Path $tempRoot "extract"

  Write-Host "Python no esta instalado. Descargando Python portable para este juego..." -ForegroundColor Yellow
  Write-Host "  $zipUrl"
  New-Item -ItemType Directory -Force -Path $tempRoot,$extractPath,$pythonRoot | Out-Null

  try {
    Invoke-WebRequest -Uri $zipUrl -OutFile $zipPath -UseBasicParsing
    Expand-Archive -LiteralPath $zipPath -DestinationPath $extractPath -Force
    Get-ChildItem -LiteralPath $extractPath -Force | ForEach-Object {
      Copy-Item -LiteralPath $_.FullName -Destination $pythonRoot -Recurse -Force
    }
  } finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
  }

  if (-not (Test-Path -LiteralPath $pythonExe)) {
    throw "No se pudo preparar Python portable."
  }

  return @($pythonExe)
}

$rootPath = [System.IO.Path]::GetFullPath($Root)
$eventBusPath = Join-Path $rootPath "runtime\event_bus.py"
if (-not (Test-Path -LiteralPath $eventBusPath)) {
  throw "Falta runtime/event_bus.py"
}

$python = Get-SystemPython
if (-not $python) {
  $python = Ensure-PortablePython $rootPath
}

Set-Location $rootPath
Write-Host "Event bus usando Python: $($python -join ' ')" -ForegroundColor Green
& $python[0] @($python | Select-Object -Skip 1) $eventBusPath
