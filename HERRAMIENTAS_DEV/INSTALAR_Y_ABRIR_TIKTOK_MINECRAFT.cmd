@echo off
chcp 65001 >nul
title TikTok Minecraft Live

for %%I in ("%~dp0..") do set "PACK=%%~fI"
set "MC=%APPDATA%\.minecraft"
set "INSTANCE=%MC%\instances\TikTokMinecraftLive"
set "PROFILE_ID=tiktok-minecraft-live-forge-1201"
set "PROFILE_NAME=TikTok Minecraft Live"
set "FORGE_VERSION=1.20.1-forge-47.4.10"
set "NO_PAUSE=0"
set "NO_LAUNCHER=0"

for %%A in (%*) do (
  if /I "%%~A"=="--no-pause" set "NO_PAUSE=1"
  if /I "%%~A"=="--no-launcher" set "NO_LAUNCHER=1"
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

echo Copiando mods del paquete...
robocopy "%PACK%mods" "%INSTANCE%\mods" *.jar /E /NFL /NDL /NJH /NJS /NP >nul

echo Copiando configuracion inicial sin pisar cambios locales...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$src='%PACK%config'; $dst='%INSTANCE%\config';" ^
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

echo Creando perfil del Minecraft Launcher...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$profilesPath=Join-Path '%MC%' 'launcher_profiles.json';" ^
  "if (-not (Test-Path -LiteralPath $profilesPath)) { '{\"profiles\":{}}' | Set-Content -LiteralPath $profilesPath -Encoding UTF8 }" ^
  "$json=Get-Content -Raw -LiteralPath $profilesPath | ConvertFrom-Json;" ^
  "if (-not $json.profiles) { $json | Add-Member -MemberType NoteProperty -Name profiles -Value ([pscustomobject]@{}) }" ^
  "$profile=[pscustomobject]@{ name='%PROFILE_NAME%'; type='custom'; created=(Get-Date).ToUniversalTime().ToString('o'); lastUsed=(Get-Date).ToUniversalTime().ToString('o'); lastVersionId='%FORGE_VERSION%'; gameDir='%INSTANCE%'; icon='Crafting_Table' };" ^
  "$json.profiles | Add-Member -MemberType NoteProperty -Name '%PROFILE_ID%' -Value $profile -Force;" ^
  "$json | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $profilesPath -Encoding UTF8"

echo.
echo Listo.
echo Instancia: "%INSTANCE%"
echo Perfil: %PROFILE_NAME%
echo.
echo Abre Minecraft Launcher y elige "%PROFILE_NAME%".
echo Si Forge %FORGE_VERSION% no existe, instalalo una vez y vuelve a ejecutar este .cmd.
echo.
if "%NO_LAUNCHER%"=="0" start "" "minecraft://"
if "%NO_PAUSE%"=="0" pause
