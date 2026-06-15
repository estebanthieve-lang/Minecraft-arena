param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

if (-not $Root -or [string]::IsNullOrWhiteSpace($Root)) {
  $Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}
$rootPath = [System.IO.Path]::GetFullPath($Root)
$config = Get-Content -Raw -LiteralPath (Join-Path $rootPath "config\arena_server.json") | ConvertFrom-Json
$serverRoot = [System.IO.Path]::GetFullPath((Join-Path $rootPath ([string]$config.server.serverRoot)))
$mcVersion = [string]$config.server.minecraftVersion
$forgeVersion = [string]$config.server.forgeVersion
$forgeFull = "$mcVersion-$forgeVersion"
$installerName = "forge-$forgeFull-installer.jar"
$installerPath = Join-Path $serverRoot $installerName
$installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$forgeFull/$installerName"

New-Item -ItemType Directory -Force -Path $serverRoot,(Join-Path $serverRoot "mods"),(Join-Path $serverRoot "config") | Out-Null

$serverPropertiesSource = Join-Path $rootPath "server\server.properties"
$serverPropertiesTarget = Join-Path $serverRoot "server.properties"
$eulaSource = Join-Path $rootPath "server\eula.txt"
$eulaTarget = Join-Path $serverRoot "eula.txt"
if ([System.IO.Path]::GetFullPath($serverPropertiesSource) -ne [System.IO.Path]::GetFullPath($serverPropertiesTarget)) {
  Copy-Item -LiteralPath $serverPropertiesSource -Destination $serverPropertiesTarget -Force
}
if ([System.IO.Path]::GetFullPath($eulaSource) -ne [System.IO.Path]::GetFullPath($eulaTarget)) {
  Copy-Item -LiteralPath $eulaSource -Destination $eulaTarget -Force
}

if (-not (Test-Path -LiteralPath $installerPath)) {
  Write-Host "Descargando Forge Server Installer:" -ForegroundColor Cyan
  Write-Host "  $installerUrl"
  Invoke-WebRequest -Uri $installerUrl -OutFile $installerPath
}

$runScript = Join-Path $serverRoot "run.bat"
if (-not (Test-Path -LiteralPath $runScript)) {
  Write-Host "Instalando servidor Forge..." -ForegroundColor Cyan
  Push-Location $serverRoot
  try {
    & java -jar $installerPath --installServer
  } finally {
    Pop-Location
  }
}

Write-Host "Servidor preparado en: $serverRoot" -ForegroundColor Green
Write-Host "Si hay un mod nuevo, ejecuta SINCRONIZAR_MOD_ARENA.cmd antes de iniciar."
