param(
  [Parameter(Mandatory = $true)]
  [string]$Root
)

$ErrorActionPreference = "Stop"
$rootPath = [System.IO.Path]::GetFullPath($Root)
$configPath = Join-Path $rootPath "game.config.json"
$manifestPath = Join-Path $rootPath "game-manifest.json"
$adapterPath = Join-Path $rootPath "runtime\game_adapter.py"
$eventBusPath = Join-Path $rootPath "runtime\event_bus.py"
$prepareClientPath = Join-Path $rootPath "PREPARAR_CLIENTE.cmd"
$neatConfigPath = Join-Path $rootPath "config\neat-client.toml"

$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

function Get-JarNames([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) { return $null }
  return @(Get-ChildItem -LiteralPath $path -File -Filter "*.jar" | Sort-Object Name | ForEach-Object { $_.Name })
}

function Get-JarDirectoryDiff([string]$expectedPath, [string]$actualPath) {
  $expected = Get-JarNames $expectedPath
  $actual = Get-JarNames $actualPath
  if ($null -eq $expected -or $null -eq $actual) { return "carpeta faltante" }
  if ($expected.Count -eq 0) { return "paquete sin jars" }

  $missing = @($expected | Where-Object { $_ -notin $actual })
  $extra = @($actual | Where-Object { $_ -notin $expected })
  $messages = @()
  if ($missing.Count -gt 0) { $messages += "faltan: $($missing -join ', ')" }
  if ($extra.Count -gt 0) { $messages += "sobran: $($extra -join ', ')" }
  if ($messages.Count -eq 0) { return $null }
  return ($messages -join "; ")
}

if (-not (Test-Path -LiteralPath $configPath)) { $errors.Add("Falta game.config.json") }
if (-not (Test-Path -LiteralPath $manifestPath)) { $errors.Add("Falta game-manifest.json") }
if (-not (Test-Path -LiteralPath $adapterPath)) { $errors.Add("Falta runtime/game_adapter.py") }
if (-not (Test-Path -LiteralPath $eventBusPath)) { $errors.Add("Falta runtime/event_bus.py") }
if (-not (Test-Path -LiteralPath $prepareClientPath)) { $errors.Add("Falta PREPARAR_CLIENTE.cmd") }
if (-not (Test-Path -LiteralPath $neatConfigPath)) { $warnings.Add("Falta config/neat-client.toml; las barras de vida pueden verse distinto en otro PC") }

if ($errors.Count -eq 0) {
  try { $config = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json } catch { $errors.Add("game.config.json no es JSON valido: $($_.Exception.Message)") }
  try { $manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json } catch { $errors.Add("game-manifest.json no es JSON valido: $($_.Exception.Message)") }
}

if ($manifest) {
  $templateMode = ([string]$manifest.status -eq "template")
  if (-not $manifest.gameId) { $errors.Add("Falta gameId") }
  if (-not $manifest.name) { $errors.Add("Falta name") }
  if (-not $manifest.eventBus.host) { $errors.Add("Falta eventBus.host") }
  if (-not $manifest.eventBus.port) { $errors.Add("Falta eventBus.port") }
  if (-not $manifest.eventBus.path) { $errors.Add("Falta eventBus.path") }
  if ($null -eq $manifest.actions) { $errors.Add("Falta actions[]") }
  if (-not $templateMode -and $manifest.actions.Count -eq 0) { $errors.Add("Falta declarar acciones reales en actions[]") }
  if ($templateMode -and $manifest.actions.Count -eq 0) {
    $warnings.Add("Manifest en modo plantilla: actions[] esta vacio a proposito")
  }

  if (-not $manifest.updates -or -not $manifest.updates.githubRepo) {
    $warnings.Add("GitHub updates no configurado: falta updates.githubRepo")
  }
  if (-not $manifest.updates -or -not $manifest.updates.latestManifestUrl) {
    $warnings.Add("GitHub updates no configurado: falta updates.latestManifestUrl")
  }
  if (-not $manifest.updates -or -not $manifest.updates.downloadUrl) {
    $warnings.Add("GitHub updates no configurado: falta updates.downloadUrl del ZIP liviano")
  }

  $usedIds = @{}
  foreach ($action in $manifest.actions) {
    if (-not $action.id) { $errors.Add("Hay una accion sin id"); continue }
    if (-not $action.label) { $errors.Add("La accion '$($action.id)' no tiene label") }
    if ($usedIds.ContainsKey([string]$action.id)) { $errors.Add("ID de accion duplicado: $($action.id)") }
    $usedIds[[string]$action.id] = $true
  }

  $adapterText = if (Test-Path -LiteralPath $adapterPath) { Get-Content -Raw -LiteralPath $adapterPath } else { "" }
  foreach ($action in $manifest.actions) {
    if ($action.id -and -not $adapterText.Contains('"' + [string]$action.id + '"')) {
      $errors.Add("La accion '$($action.id)' esta en el manifest pero no aparece en runtime/game_adapter.py")
    }
  }
}

if ($config) {
  if (-not $config.baseUrl) { $errors.Add("Falta baseUrl en game.config.json") }
  if (-not $config.pythonCommand) { $errors.Add("Falta pythonCommand en game.config.json") }
  if (-not $config.protectedPaths -or $config.protectedPaths.Count -eq 0) { $errors.Add("Falta protectedPaths en game.config.json") }
  if (-not $config.updatablePaths -or $config.updatablePaths.Count -eq 0) { $errors.Add("Falta updatablePaths en game.config.json") }

  if ($config.minecraftInstancePath) {
    $instancePath = ([string]$config.minecraftInstancePath).Replace("%APPDATA%", $env:APPDATA)
    $modsPath = Join-Path $instancePath "mods"
    $packageModsPath = Join-Path $rootPath "mods"
    $arenaConfigPath = Join-Path $instancePath "config\arena_prototype.json"
    if (-not (Test-Path -LiteralPath $modsPath)) {
      $warnings.Add("Instancia Minecraft aun no creada: falta $modsPath. Ejecuta INSTALAR_Y_ABRIR_TIKTOK_MINECRAFT.cmd")
    } else {
      $modDiff = Get-JarDirectoryDiff $packageModsPath $modsPath
      if ($modDiff) {
        $errors.Add("Los mods del cliente no coinciden con el paquete ($modsPath): $modDiff. Ejecuta PREPARAR_CLIENTE.cmd")
      }
    }
    if (-not (Test-Path -LiteralPath $arenaConfigPath)) {
      $warnings.Add("Config de arena aun no creada: falta $arenaConfigPath. Ejecuta INSTALAR_Y_ABRIR_TIKTOK_MINECRAFT.cmd")
    }
  }

  if ($config.protectedPaths -and $config.updatablePaths) {
    $runtimeCreatedProtectedPaths = @{
      "saves" = $true
      "data" = $true
      "logs" = $true
      "user-data" = $true
      "server/world" = $true
      "server/logs" = $true
      "server/crash-reports" = $true
    }
    $protectedSet = @{}
    foreach ($protectedPath in $config.protectedPaths) {
      $protectedSet[[string]$protectedPath] = $true
      $fullProtectedPath = Join-Path $rootPath ([string]$protectedPath)
      if (-not (Test-Path -LiteralPath $fullProtectedPath)) {
        $normalizedProtectedPath = ([string]$protectedPath).Replace("\", "/")
        if ($runtimeCreatedProtectedPaths.ContainsKey($normalizedProtectedPath)) {
          $warnings.Add("Carpeta protegida ausente, se creara en runtime: $protectedPath")
        } else {
          $errors.Add("Falta carpeta protegida: $protectedPath")
        }
      }
    }

    foreach ($updatablePath in $config.updatablePaths) {
      if ($protectedSet.ContainsKey([string]$updatablePath)) {
        $errors.Add("La ruta '$updatablePath' no puede ser protegida y actualizable a la vez")
      }
    }
  }
}

$bridgeContractPath = Join-Path $rootPath "CONTRATO_MOD_MINECRAFT.md"
if (-not (Test-Path -LiteralPath $bridgeContractPath)) {
  $warnings.Add("Falta CONTRATO_MOD_MINECRAFT.md para documentar el puente final del mod")
}

$arenaServerConfigPath = Join-Path $rootPath "config\arena_server.json"
if (-not (Test-Path -LiteralPath $arenaServerConfigPath)) {
  $errors.Add("Falta config/arena_server.json")
} else {
  try {
    $arenaServerConfig = Get-Content -Raw -LiteralPath $arenaServerConfigPath | ConvertFrom-Json
    $serverRoot = Join-Path $rootPath ([string]$arenaServerConfig.server.serverRoot)
    $runBat = Join-Path $serverRoot "run.bat"
    if (-not (Test-Path -LiteralPath $runBat)) {
      $warnings.Add("Servidor Forge aun no preparado: falta server/run.bat. Ejecuta PREPARAR_SERVIDOR.cmd")
    }

    $jarPattern = [string]$arenaServerConfig.mod.jarPattern
    $modTargets = @(
      (Join-Path $rootPath "mods"),
      (Join-Path $rootPath "server\mods"),
      (Join-Path (([string]$config.minecraftInstancePath).Replace("%APPDATA%", $env:APPDATA)) "mods")
    )
    foreach ($modTarget in $modTargets) {
      $hasJar = Test-Path -LiteralPath $modTarget
      if ($hasJar) {
        $hasJar = [bool](Get-ChildItem -LiteralPath $modTarget -Filter $jarPattern -File -ErrorAction SilentlyContinue | Select-Object -First 1)
      }
      if (-not $hasJar) {
        $warnings.Add("Falta mod de arena en: $modTarget. Ejecuta COMPILAR_MOD_ARENA.cmd y SINCRONIZAR_MOD_ARENA.cmd")
      }
    }
  } catch {
    $warnings.Add("No se pudo validar arena_server.json: $($_.Exception.Message)")
  }
}

$liveEventsPath = if ($config -and $config.minecraftInstancePath) {
  Join-Path (([string]$config.minecraftInstancePath).Replace("%APPDATA%", $env:APPDATA)) "config\minecraft_live_arena\live_events.jsonl"
} else {
  $null
}
if ($liveEventsPath -and -not (Test-Path -LiteralPath $liveEventsPath)) {
  $warnings.Add("El archivo live_events.jsonl aun no existe; se creara cuando llegue la primera accion")
}

$eventBusStarter = Join-Path $rootPath "scripts\iniciar_event_bus.ps1"
$portablePython = Join-Path $rootPath "tools\python-embed\python.exe"
$python = if ($config.pythonCommand) { Get-Command ([string]$config.pythonCommand) -ErrorAction SilentlyContinue } else { $null }
if (-not $python -and -not (Test-Path -LiteralPath $portablePython) -and -not (Test-Path -LiteralPath $eventBusStarter)) {
  $errors.Add("No se encontro Python ni scripts/iniciar_event_bus.ps1 para preparar Python portable")
}

if ($errors.Count -gt 0) {
  Write-Host "JUEGO NO VALIDO" -ForegroundColor Red
  foreach ($errorMessage in $errors) {
    Write-Host "- $errorMessage" -ForegroundColor Red
  }
  exit 1
}

Write-Host "JUEGO VALIDO" -ForegroundColor Green
Write-Host "Nombre: $($manifest.name)"
Write-Host "Game ID: $($manifest.gameId)"
Write-Host "Puerto: $($manifest.eventBus.port)"
Write-Host "Acciones: $($manifest.actions.Count)"
Write-Host "Base: $($config.baseUrl)"
Write-Host "Protegidos: $([string]::Join(', ', @($config.protectedPaths)))"
Write-Host "Actualizables: $([string]::Join(', ', @($config.updatablePaths)))"

if ($warnings.Count -gt 0) {
  Write-Host ""
  Write-Host "AVISOS / PENDIENTES" -ForegroundColor Yellow
  foreach ($warningMessage in $warnings) {
    Write-Host "- $warningMessage" -ForegroundColor Yellow
  }
}
