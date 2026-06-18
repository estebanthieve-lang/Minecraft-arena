@echo off
chcp 65001 >nul
title Minecraft Live Arena - Preparar cliente

set "ROOT=%~dp0"
set "ROOT_ARG=%~dp0."

echo Preparando cliente Minecraft aislado...
echo.
echo Este comando NO abre Minecraft, NO abre Microsoft Store y NO abre la web.
echo Solo crea/sincroniza:
echo - instancia %APPDATA%\.minecraft\instances\TikTokMinecraftLive
echo - mods del paquete
echo - config inicial del cliente
echo - perfil del Minecraft Launcher
echo - version Forge para launchers que leen .minecraft\versions
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\preparar_cliente.ps1" -Root "%ROOT_ARG%"
if errorlevel 1 goto error

echo.
echo Cliente preparado.
echo Luego abre tu launcher y elige:
echo - Perfil oficial: TikTok Minecraft Live
echo - Version/launchers alternativos: 1.20.1-forge-47.4.10
echo.
exit /b 0

:error
echo.
echo Fallo preparando cliente Minecraft.
exit /b 1
