@echo off
setlocal
cd /d "%~dp0."
set "REMOTE_UPDATER=https://raw.githubusercontent.com/estebanthieve-lang/Minecraft-arena/main/scripts/actualizar_juego.ps1"
set "TEMP_UPDATER=%TEMP%\minecraft_live_arena_actualizar_juego.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -UseBasicParsing -Uri '%REMOTE_UPDATER%' -OutFile '%TEMP_UPDATER%'; exit 0 } catch { exit 1 }"
if errorlevel 1 (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\actualizar_juego.ps1" -InstallRoot "%~dp0."
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%TEMP_UPDATER%" -InstallRoot "%~dp0."
)
pause
