@echo off
chcp 65001 >nul
title Minecraft Live Arena - Iniciar

set "ROOT=%~dp0"
set "ROOT_ARG=%~dp0."

echo Minecraft Live Arena
echo.
echo Este comando NO abre Minecraft, NO abre Microsoft Store y NO abre la web.
echo Solo prepara y abre:
echo - event bus local
echo - servidor Forge local
echo.

echo Preparando servidor Forge local...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\preparar_servidor.ps1" -Root "%ROOT_ARG%"
if errorlevel 1 goto error

echo.
echo Sincronizando ultimo mod de arena...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\sincronizar_mod_arena.ps1" -Root "%ROOT_ARG%"
if errorlevel 1 goto error

echo.
echo Iniciando event bus local en http://127.0.0.1:9010/manifest
netstat -ano | findstr ":9010" | findstr "LISTENING" >nul
if errorlevel 1 (
  start "Minecraft Live Arena Event Bus" cmd /k "cd /d ""%ROOT%"" && powershell -NoProfile -ExecutionPolicy Bypass -File ""%ROOT%scripts\iniciar_event_bus.ps1"" -Root ""%ROOT_ARG%"""
) else (
  echo Event bus ya esta corriendo en 9010. No abro otro.
)

echo Iniciando servidor Forge local en 127.0.0.1:25565
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":25565" ^| findstr "LISTENING"') do (
  echo Cerrando servidor anterior en 25565, PID %%P...
  taskkill /PID %%P /F >nul 2>nul
  timeout /t 3 /nobreak >nul
)
start "Minecraft Live Arena Server" cmd /k "cd /d ""%ROOT%server"" && call run.bat nogui"

echo.
echo Listo.
echo En la base usa el manifest: http://127.0.0.1:9010/manifest
echo En Minecraft entra manualmente al servidor: 127.0.0.1
echo.
pause
exit /b 0

:error
echo.
echo Fallo al iniciar Minecraft Live Arena.
echo Revisa la salida de arriba.
pause
exit /b 1
