@echo off
chcp 65001 >nul
title TikTok Minecraft Live

for %%I in ("%~dp0..") do set "PACK=%%~fI"
set "MC=%APPDATA%\.minecraft"
set "INSTANCE=%MC%\instances\TikTokMinecraftLive"
set "FORGE_VERSION=1.20.1-forge-47.4.10"
set "FORGE_FULL=1.20.1-47.4.10"
set "FORGE_INSTALLER=%PACK%\server\forge-%FORGE_FULL%-installer.jar"
set "FORGE_VERSION_JSON=%MC%\versions\%FORGE_VERSION%\%FORGE_VERSION%.json"
set "NO_PAUSE=0"

for %%A in (%*) do (
  if /I "%%~A"=="--no-pause" set "NO_PAUSE=1"
)

echo Preparando instancia de Minecraft Live...
echo.

if not exist "%MC%" mkdir "%MC%"
if not exist "%INSTANCE%" mkdir "%INSTANCE%"
if not exist "%INSTANCE%\mods" mkdir "%INSTANCE%\mods"
if not exist "%INSTANCE%\config" mkdir "%INSTANCE%\config"
if not exist "%INSTANCE%\saves" mkdir "%INSTANCE%\saves"
if not exist "%INSTANCE%\logs" mkdir "%INSTANCE%\logs"
if not exist "%INSTANCE%\backups" mkdir "%INSTANCE%\backups"
if not exist "%INSTANCE%\resourcepacks" mkdir "%INSTANCE%\resourcepacks"
if not exist "%INSTANCE%\shaderpacks" mkdir "%INSTANCE%\shaderpacks"

echo Sincronizando mods gestionados del paquete...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$src='%PACK%\mods'; $dst='%INSTANCE%\mods';" ^
  "if (-not (Test-Path -LiteralPath $src)) { throw 'No existe la carpeta mods del paquete.' };" ^
  "New-Item -ItemType Directory -Force -Path $dst | Out-Null;" ^
  "Get-ChildItem -LiteralPath $dst -Filter 'minecraft_live_arena-*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force;" ^
  "Get-ChildItem -LiteralPath $src -Filter '*.jar' -File | ForEach-Object {" ^
  "  Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $dst $_.Name) -Force;" ^
  "};" ^
  "if (-not (Get-ChildItem -LiteralPath $dst -Filter 'minecraft_live_arena-*.jar' -File -ErrorAction SilentlyContinue)) { throw 'No se pudo sincronizar el mod de arena.' }"
if errorlevel 1 (
  echo No se pudieron sincronizar los mods del cliente.
  echo Cierra Minecraft si esta abierto y vuelve a ejecutar este comando.
  exit /b 1
)

echo Copiando configuracion inicial sin pisar cambios locales...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$src='%PACK%\config'; $dst='%INSTANCE%\config';" ^
  "if (Test-Path -LiteralPath $src) {" ^
  "  Get-ChildItem -LiteralPath $src -Recurse | ForEach-Object {" ^
  "    $rel=$_.FullName.Substring($src.Length).TrimStart('\');" ^
  "    $target=Join-Path $dst $rel;" ^
  "    if ($_.PSIsContainer) { New-Item -ItemType Directory -Force -Path $target | Out-Null }" ^
  "    elseif (-not (Test-Path -LiteralPath $target)) {" ^
  "      New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null;" ^
  "      Copy-Item -LiteralPath $_.FullName -Destination $target -Force;" ^
  "    }" ^
  "  }" ^
  "}"

if not exist "%FORGE_VERSION_JSON%" (
  echo Instalando Forge %FORGE_VERSION% en Minecraft Launcher...
  if not exist "%FORGE_INSTALLER%" (
    echo No se encontro el instalador de Forge en:
    echo "%FORGE_INSTALLER%"
    echo Ejecuta INICIAR_JUEGO.cmd una vez o descarga el paquete completo actualizado.
    exit /b 1
  )
  java -jar "%FORGE_INSTALLER%" --installClient
  if errorlevel 1 (
    echo No se pudo instalar Forge automaticamente.
    echo Revisa que Java este disponible o instala Forge %FORGE_VERSION% manualmente.
    exit /b 1
  )
)

echo.
echo Listo.
echo Instancia: "%INSTANCE%"
echo No se tocaron perfiles ni instalaciones del Minecraft Launcher.
echo.
echo En Minecraft Launcher crea/usa una instalacion manual con:
echo Version: %FORGE_VERSION%
echo Directorio del juego: "%INSTANCE%"
echo.
if "%NO_PAUSE%"=="0" pause
