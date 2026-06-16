@echo off
chcp 65001 >nul
title TikTok Minecraft Live

for %%I in ("%~dp0..") do set "PACK=%%~fI"
set "MC=%APPDATA%\.minecraft"
set "INSTANCE=%MC%\instances\TikTokMinecraftLive"
set "PROFILE_ID=tiktok-minecraft-live-forge-1201"
set "PROFILE_NAME=TikTok Minecraft Live"
set "FORGE_VERSION=1.20.1-forge-47.4.10"
set "FORGE_FULL=1.20.1-47.4.10"
set "FORGE_INSTALLER=%PACK%\server\forge-%FORGE_FULL%-installer.jar"
set "FORGE_VERSION_JSON=%MC%\versions\%FORGE_VERSION%\%FORGE_VERSION%.json"
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

echo Creando perfil del Minecraft Launcher...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$profilesPath=Join-Path '%MC%' 'launcher_profiles.json';" ^
  "$backupPath=$profilesPath + '.bak_minecraft_live_arena';" ^
  "if (-not (Test-Path -LiteralPath $profilesPath)) { '{\"profiles\":{}}' | Set-Content -LiteralPath $profilesPath -Encoding UTF8 }" ^
  "Copy-Item -LiteralPath $profilesPath -Destination $backupPath -Force;" ^
  "$json=Get-Content -Raw -LiteralPath $profilesPath | ConvertFrom-Json;" ^
  "if (-not $json.profiles) { $json | Add-Member -MemberType NoteProperty -Name profiles -Value ([pscustomobject]@{}) }" ^
  "$existing=$null;" ^
  "foreach($prop in $json.profiles.PSObject.Properties){ if([string]$prop.Value.gameDir -eq '%INSTANCE%'){ $existing=$prop; break } }" ^
  "if($existing){" ^
  "  if(-not $existing.Value.lastVersionId){ $existing.Value | Add-Member -MemberType NoteProperty -Name lastVersionId -Value '%FORGE_VERSION%' -Force } else { $existing.Value.lastVersionId='%FORGE_VERSION%' };" ^
  "  if(-not $existing.Value.gameDir){ $existing.Value | Add-Member -MemberType NoteProperty -Name gameDir -Value '%INSTANCE%' -Force } else { $existing.Value.gameDir='%INSTANCE%' };" ^
  "} else {" ^
  "  $profile=[pscustomobject]@{ name='%PROFILE_NAME%'; type='custom'; created=(Get-Date).ToUniversalTime().ToString('o'); lastUsed=(Get-Date).ToUniversalTime().ToString('o'); lastVersionId='%FORGE_VERSION%'; gameDir='%INSTANCE%'; icon='Crafting_Table' };" ^
  "  $json.profiles | Add-Member -MemberType NoteProperty -Name '%PROFILE_ID%' -Value $profile -Force;" ^
  "}" ^
  "$json | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $profilesPath -Encoding UTF8"

echo.
echo Listo.
echo Instancia: "%INSTANCE%"
echo Perfil: usa el perfil del Launcher que apunte a esa instancia.
echo.
echo Abre Minecraft Launcher y elige el perfil de esta instancia.
echo Si Forge %FORGE_VERSION% no existe, instalalo una vez y vuelve a ejecutar este .cmd.
echo.
if "%NO_LAUNCHER%"=="0" start "" "minecraft://"
if "%NO_PAUSE%"=="0" pause
