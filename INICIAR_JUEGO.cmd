@echo off
chcp 65001 >nul
title Minecraft Live Arena - Iniciar

set "ROOT=%~dp0"
set "ROOT_ARG=%~dp0."
set "JAVA_HOME=%ROOT%tools\java"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo ERROR: falta Java portable en "%JAVA_HOME%\bin\java.exe"
  exit /b 1
)

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
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":9010" ^| findstr "LISTENING"') do (
  echo Cerrando event bus anterior en 9010, PID %%P...
  taskkill /PID %%P /F >nul 2>nul
  timeout /t 2 /nobreak >nul
)
start "Minecraft Live Arena Event Bus" /MIN powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\iniciar_event_bus.ps1" -Root "%ROOT_ARG%"

echo Iniciando servidor Forge local en 127.0.0.1:25565
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":25565" ^| findstr "LISTENING"') do (
  echo Cerrando servidor anterior en 25565, PID %%P...
  taskkill /PID %%P /F >nul 2>nul
  timeout /t 3 /nobreak >nul
)
start "Minecraft Live Arena Server" /D "%ROOT%server" /MIN "%JAVA_HOME%\bin\java.exe" @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt nogui

echo.
echo Listo.
echo En la base usa el manifest: http://127.0.0.1:9010/manifest
echo En Minecraft entra manualmente al servidor: 127.0.0.1
echo.
timeout /t 8 /nobreak >nul
exit /b 0

:error
echo.
echo Fallo al iniciar Minecraft Live Arena.
echo Revisa la salida de arriba.
timeout /t 8 /nobreak >nul
exit /b 1
