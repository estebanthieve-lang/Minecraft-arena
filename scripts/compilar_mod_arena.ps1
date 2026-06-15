param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

if (-not $Root -or [string]::IsNullOrWhiteSpace($Root)) {
  $Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

$rootPath = [System.IO.Path]::GetFullPath($Root)
$modRoot = Join-Path $rootPath "game\mod-src"
$toolsRoot = Join-Path $rootPath "tools"
$distMods = Join-Path $rootPath "dist\mods"
$gradleVersion = "8.8"
$gradleRoot = Join-Path $toolsRoot "gradle-$gradleVersion"
$gradleBat = Join-Path $gradleRoot "bin\gradle.bat"

if (-not (Test-Path -LiteralPath $modRoot)) {
  throw "Falta game/mod-src"
}

if (-not (Test-Path -LiteralPath $gradleBat)) {
  $zipPath = Join-Path $toolsRoot "gradle-$gradleVersion-bin.zip"
  $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
  New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
  if (-not (Test-Path -LiteralPath $zipPath)) {
    Write-Host "Descargando Gradle ${gradleVersion}:" -ForegroundColor Cyan
    Write-Host "  $url"
    Invoke-WebRequest -Uri $url -OutFile $zipPath
  }
  Write-Host "Extrayendo Gradle..." -ForegroundColor Cyan
  Expand-Archive -LiteralPath $zipPath -DestinationPath $toolsRoot -Force
}

Write-Host "Compilando mod Forge..." -ForegroundColor Cyan
Push-Location $modRoot
try {
  & $gradleBat --no-daemon clean jar
  if ($LASTEXITCODE -ne 0) {
    throw "Gradle fallo con codigo $LASTEXITCODE"
  }
} finally {
  Pop-Location
}

$jar = Get-ChildItem -LiteralPath (Join-Path $modRoot "build\libs") -Filter "minecraft_live_arena-*.jar" -File |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $jar) {
  throw "No se genero jar en game/mod-src/build/libs"
}

New-Item -ItemType Directory -Force -Path $distMods | Out-Null
Get-ChildItem -LiteralPath $distMods -Filter "minecraft_live_arena-*.jar" -File -ErrorAction SilentlyContinue |
  Remove-Item -Force
Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $distMods $jar.Name) -Force

Write-Host "Jar listo:" -ForegroundColor Green
Write-Host "  $(Join-Path $distMods $jar.Name)"
Write-Host ""
Write-Host "Ahora ejecuta SINCRONIZAR_MOD_ARENA.cmd para instalarlo en cliente y servidor."
