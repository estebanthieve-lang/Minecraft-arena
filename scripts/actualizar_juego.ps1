param(
  [Parameter(Mandatory = $true)]
  [string]$InstallRoot,

  [string]$UpdateRoot = ""
)

$ErrorActionPreference = "Stop"

function Normalize-InputPath([string]$path) {
  return ($path -replace '"', "").Trim()
}

function Resolve-LocalPath([string]$root, [string]$relative) {
  $clean = $relative -replace "/", "\"
  return [System.IO.Path]::GetFullPath((Join-Path (Normalize-InputPath $root) $clean))
}

function Read-JsonFile([string]$path) {
  return Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
}

function Backup-ExistingPath([string]$target, [string]$backupRoot, [string]$relative) {
  if (-not (Test-Path -LiteralPath $target)) { return }
  $backupTarget = Resolve-LocalPath $backupRoot $relative
  $backupParent = Split-Path $backupTarget -Parent
  if ($backupParent) { New-Item -ItemType Directory -Force -Path $backupParent | Out-Null }
  Copy-Item -LiteralPath $target -Destination $backupTarget -Recurse -Force
}

function Copy-UpdatePath([string]$source, [string]$target) {
  if ((Get-Item -LiteralPath $source).PSIsContainer) {
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Copy-Item -LiteralPath (Join-Path $source "*") -Destination $target -Recurse -Force
    return
  }
  $targetParent = Split-Path $target -Parent
  if ($targetParent) { New-Item -ItemType Directory -Force -Path $targetParent | Out-Null }
  Copy-Item -LiteralPath $source -Destination $target -Force
}

$installPath = [System.IO.Path]::GetFullPath((Normalize-InputPath $InstallRoot))
$configPath = Join-Path $installPath "game.config.json"
if (-not (Test-Path -LiteralPath $configPath)) {
  throw "Falta game.config.json en la instalacion actual."
}

$config = Read-JsonFile $configPath
$currentManifestPath = Join-Path $installPath "game-manifest.json"
$currentManifest = if (Test-Path -LiteralPath $currentManifestPath) { Read-JsonFile $currentManifestPath } else { $null }

if (-not $UpdateRoot) {
  Write-Host ""
  Write-Host "Pega la ruta de la carpeta de actualizacion."
  Write-Host "Debe ser una carpeta nueva del mismo juego, no un ZIP directo."
  $UpdateRoot = Read-Host "Ruta"
}

$updatePath = [System.IO.Path]::GetFullPath((Normalize-InputPath $UpdateRoot))
if (-not (Test-Path -LiteralPath $updatePath)) {
  throw "No existe la carpeta de actualizacion: $updatePath"
}

$incomingManifestPath = Join-Path $updatePath "game-manifest.json"
if (-not (Test-Path -LiteralPath $incomingManifestPath)) {
  throw "La actualizacion no trae game-manifest.json"
}

$incomingManifest = Read-JsonFile $incomingManifestPath
if ($currentManifest -and $currentManifest.gameId -and $incomingManifest.gameId -and $currentManifest.gameId -ne $incomingManifest.gameId) {
  throw "La actualizacion es de otro juego. Actual: $($currentManifest.gameId), update: $($incomingManifest.gameId)"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$updateBackupRoot = if ($config.updateBackupRoot) { Resolve-LocalPath $installPath $config.updateBackupRoot } else { Join-Path $installPath "backups\updates" }
$backupRoot = Join-Path $updateBackupRoot "update-$timestamp"
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null

$protected = @($config.protectedPaths) | Where-Object { $_ }
$updatable = @($config.updatablePaths) | Where-Object { $_ }

Write-Host ""
Write-Host "Instalacion: $installPath"
Write-Host "Actualizacion: $updatePath"
Write-Host "Backup: $backupRoot"
Write-Host ""

foreach ($relative in $updatable) {
  if ($protected -contains $relative) {
    Write-Host "Saltando ruta protegida: $relative" -ForegroundColor Yellow
    continue
  }

  $source = Resolve-LocalPath $updatePath $relative
  if (-not (Test-Path -LiteralPath $source)) {
    Write-Host "No viene en update, se deja igual: $relative"
    continue
  }

  $target = Resolve-LocalPath $installPath $relative
  Backup-ExistingPath $target $backupRoot $relative
  Copy-UpdatePath $source $target
  Write-Host "Actualizado: $relative" -ForegroundColor Green
}

Write-Host ""
Write-Host "Listo. No se tocaron estas carpetas protegidas:" -ForegroundColor Green
foreach ($relative in $protected) {
  Write-Host "- $relative"
}
Write-Host ""
Write-Host "Ahora ejecuta VALIDAR_JUEGO.cmd y luego INICIAR_JUEGO.cmd." -ForegroundColor Cyan
