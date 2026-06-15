@echo off
chcp 65001 >nul
title Minecraft Live Arena - Servidor local

set "ROOT=%~dp0"

call "%ROOT%INSTALAR_Y_ABRIR_TIKTOK_MINECRAFT.cmd" --no-pause --no-launcher

echo.
echo Preparando servidor Forge local...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\preparar_servidor.ps1" -Root "%ROOT%"
if errorlevel 1 goto error

echo.
echo Sincronizando mod de arena si existe...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\sincronizar_mod_arena.ps1" -Root "%ROOT%"
if errorlevel 1 echo Aun no hay mod compilado. El servidor puede iniciar, pero Minecraft no ejecutara la arena.

echo.
echo Iniciando event bus...
start "Minecraft Live Arena Event Bus" cmd /k "cd /d "%ROOT%" && python runtime\event_bus.py"

echo Iniciando servidor Forge...
start "Minecraft Live Arena Server" cmd /k "cd /d "%ROOT%server" && run.bat"

echo Abriendo base y launcher...
start "" "https://panel-juegos.pages.dev/index.html?manifest=http%%3A%%2F%%2F127.0.0.1%%3A9010%%2Fmanifest"
start "" "minecraft://"

echo.
echo En Minecraft entra al servidor local: 127.0.0.1
echo La arena esta definida en config\arena_server.json.
echo.
pause
exit /b 0

:error
echo.
echo Fallo preparando el servidor.
pause
exit /b 1
