param(
  [string]$OutputDir = "",
  [switch]$KeepStaging
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = [System.IO.Path]::GetFullPath((Join-Path $scriptDir ".."))
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
  $OutputDir = Join-Path $root "downloads\release"
}
$OutputDir = [System.IO.Path]::GetFullPath($OutputDir)

if (-not $OutputDir.StartsWith([System.IO.Path]::GetFullPath($root))) {
  throw "OutputDir debe estar dentro de la fuente del juego."
}

$manifestPath = Join-Path $root "game-manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath)) {
  throw "Falta game-manifest.json"
}
$manifestBytes = [System.IO.File]::ReadAllBytes($manifestPath)
if ($manifestBytes.Length -ge 3 -and $manifestBytes[0] -eq 0xEF -and $manifestBytes[1] -eq 0xBB -and $manifestBytes[2] -eq 0xBF) {
  throw "game-manifest.json tiene UTF-8 BOM. Guardalo como UTF-8 sin BOM antes de crear release."
}
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
$version = [string]$manifest.version
if ([string]::IsNullOrWhiteSpace($version)) {
  throw "Manifest sin version"
}

$requiredPaths = @(
  "ACTUALIZAR_JUEGO.cmd",
  "config",
  "data",
  "dist",
  "game.config.json",
  "game-manifest.json",
  "HERRAMIENTAS_DEV",
  "INICIAR_JUEGO.cmd",
  "logs",
  "mods",
  "PREPARAR_CLIENTE.cmd",
  "runtime",
  "saves",
  "scripts",
  "server",
  "user-data",
  "assets",
  "tools\python-embed",
  "tools\java"
)

foreach ($rel in $requiredPaths) {
  $path = Join-Path $root $rel
  if (-not (Test-Path -LiteralPath $path)) {
    throw "Falta ruta requerida para release: $rel"
  }
}

$missingAssets = @()
foreach ($action in $manifest.actions) {
  $assetPath = Join-Path $root ("assets\acciones_png\{0}.png" -f $action.id)
  if (-not (Test-Path -LiteralPath $assetPath)) {
    $missingAssets += [string]$action.id
  }
}
if ($missingAssets.Count -gt 0) {
  throw "Faltan PNG de acciones: $($missingAssets -join ', ')"
}

foreach ($jar in @(
  "mods\minecraft_live_arena-0.1.0.jar",
  "dist\mods\minecraft_live_arena-0.1.0.jar"
)) {
  if (-not (Test-Path -LiteralPath (Join-Path $root $jar))) {
    throw "Falta jar requerido: $jar"
  }
}

if (-not (Test-Path -LiteralPath (Join-Path $root "runtime\event_bus.py"))) {
  throw "Falta runtime\event_bus.py"
}
if (-not (Test-Path -LiteralPath (Join-Path $root "tools\python-embed\python.exe"))) {
  throw "Falta tools\python-embed\python.exe"
}
if (-not (Test-Path -LiteralPath (Join-Path $root "tools\java\bin\java.exe"))) {
  throw "Falta tools\java\bin\java.exe"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$staging = Join-Path $OutputDir ("_staging_{0}_{1}" -f $version, (Get-Date -Format "yyyyMMdd-HHmmss"))
if (Test-Path -LiteralPath $staging) {
  Remove-Item -LiteralPath $staging -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $staging | Out-Null

function Copy-ReleasePath {
  param([string]$RelativePath)
  $source = Join-Path $root $RelativePath
  $destination = Join-Path $staging $RelativePath
  $destinationParent = Split-Path -Parent $destination
  if (-not [string]::IsNullOrWhiteSpace($destinationParent)) {
    New-Item -ItemType Directory -Force -Path $destinationParent | Out-Null
  }
  $sourceItem = Get-Item -LiteralPath $source
  if ($sourceItem.PSIsContainer) {
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    & robocopy $source $destination /E /NFL /NDL /NJH /NJS /NP /XF session.lock | Out-Null
    if ($LASTEXITCODE -gt 7) {
      throw "No pude copiar $RelativePath hacia staging. Robocopy=$LASTEXITCODE"
    }
    return
  }
  Copy-Item -LiteralPath $source -Destination $destination -Force
}

foreach ($rel in $requiredPaths) {
  Copy-ReleasePath $rel
}

$serverMods = Join-Path $staging "server\mods"
New-Item -ItemType Directory -Force -Path $serverMods | Out-Null
Copy-Item `
  -LiteralPath (Join-Path $root "mods\minecraft_live_arena-0.1.0.jar") `
  -Destination (Join-Path $serverMods "minecraft_live_arena-0.1.0.jar") `
  -Force

$removePatterns = @(
  "runtime\__pycache__",
  "server\logs",
  "server\crash-reports",
  "server\config",
  "server\defaultconfigs",
  "server\easy_npc",
  "logs",
  "saves",
  "user-data",
  "data"
)
foreach ($rel in $removePatterns) {
  $path = Join-Path $staging $rel
  if (Test-Path -LiteralPath $path) {
    Remove-Item -LiteralPath $path -Recurse -Force
  }
}

$arenaConfigDir = Join-Path $staging "server\config\minecraft_live_arena"
New-Item -ItemType Directory -Force -Path $arenaConfigDir | Out-Null
foreach ($configFile in @(
  "arena_settings.json",
  "podium_settings.json"
)) {
  $sourceConfig = Join-Path $root ("server\config\minecraft_live_arena\{0}" -f $configFile)
  if (-not (Test-Path -LiteralPath $sourceConfig)) {
    throw "Falta config limpia requerida para release: server\config\minecraft_live_arena\$configFile"
  }
  Copy-Item -LiteralPath $sourceConfig -Destination (Join-Path $arenaConfigDir $configFile) -Force
}

$worldLock = Join-Path $staging "server\world\session.lock"
if (Test-Path -LiteralPath $worldLock) {
  Remove-Item -LiteralPath $worldLock -Force
}

foreach ($rel in @(
  "server\world\advancements",
  "server\world\playerdata",
  "server\world\stats",
  "server\banned-ips.json",
  "server\banned-players.json",
  "server\ops.json",
  "server\usercache.json",
  "server\usernamecache.json",
  "server\whitelist.json",
  "server\installer.log",
  "server\forge-1.20.1-47.4.10-installer.jar"
)) {
  $path = Join-Path $staging $rel
  if (Test-Path -LiteralPath $path) {
    Remove-Item -LiteralPath $path -Recurse -Force
  }
}

Get-ChildItem -LiteralPath $staging -Recurse -File -Filter "*.pyc" | Remove-Item -Force
Get-ChildItem -LiteralPath (Join-Path $staging "logs") -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force

$versionedZip = Join-Path $OutputDir ("minecraft-live-arena-full-{0}.zip" -f $version)
$genericZip = Join-Path $OutputDir "minecraft-live-arena-full.zip"
if (Test-Path -LiteralPath $versionedZip) { Remove-Item -LiteralPath $versionedZip -Force }
if (Test-Path -LiteralPath $genericZip) { Remove-Item -LiteralPath $genericZip -Force }

Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $versionedZip -CompressionLevel Optimal -Force
Copy-Item -LiteralPath $versionedZip -Destination $genericZip -Force

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($versionedZip)
try {
  $entries = $zip.Entries | ForEach-Object { $_.FullName.Replace("/", "\") }
  foreach ($action in $manifest.actions) {
    $assetEntry = "assets\acciones_png\{0}.png" -f $action.id
    if (-not ($entries -contains $assetEntry)) {
      throw "ZIP invalido: falta $assetEntry"
    }
  }
  foreach ($jar in @(
    "mods\minecraft_live_arena-0.1.0.jar",
    "dist\mods\minecraft_live_arena-0.1.0.jar",
    "server\mods\minecraft_live_arena-0.1.0.jar",
    "server\config\minecraft_live_arena\arena_settings.json",
    "server\config\minecraft_live_arena\podium_settings.json",
    "server\world\level.dat",
    "runtime\event_bus.py",
    "tools\python-embed\python.exe",
    "tools\java\bin\java.exe",
    "game-manifest.json"
  )) {
    if (-not ($entries -contains $jar)) {
      throw "ZIP invalido: falta $jar"
    }
  }
  $manifestEntry = $zip.GetEntry("game-manifest.json")
  if (-not $manifestEntry) {
    throw "ZIP invalido: falta game-manifest.json"
  }
  $stream = $manifestEntry.Open()
  try {
    $buffer = New-Object byte[] 3
    $read = $stream.Read($buffer, 0, 3)
    if ($read -eq 3 -and $buffer[0] -eq 0xEF -and $buffer[1] -eq 0xBB -and $buffer[2] -eq 0xBF) {
      throw "ZIP invalido: game-manifest.json quedo con UTF-8 BOM."
    }
  }
  finally {
    $stream.Dispose()
  }
}
finally {
  $zip.Dispose()
}

if (-not $KeepStaging) {
  Remove-Item -LiteralPath $staging -Recurse -Force
}

$hash = Get-FileHash -LiteralPath $versionedZip -Algorithm SHA256
Write-Output "Release creado:"
Write-Output "  $versionedZip"
Write-Output "  $genericZip"
Write-Output "SHA256:"
Write-Output "  $($hash.Hash)"
