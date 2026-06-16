@echo off
chcp 65001 >nul
title Minecraft Live Arena - Preparar cliente

set "ROOT=%~dp0"

echo Preparando cliente Minecraft aislado...
echo.
echo Este comando NO abre Minecraft, NO abre Microsoft Store y NO abre la web.
echo Solo crea/sincroniza:
echo - instancia %APPDATA%\.minecraft\instances\TikTokMinecraftLive
echo - mods del paquete
echo - config inicial del cliente
echo - Forge si falta
echo NO toca perfiles ni instalaciones del Minecraft Launcher
echo.

call "%ROOT%HERRAMIENTAS_DEV\INSTALAR_Y_ABRIR_TIKTOK_MINECRAFT.cmd" --no-launcher
if errorlevel 1 goto error

echo.
echo Cliente preparado.
echo Luego abre Minecraft Launcher y crea/usa una instalacion manual con:
echo Version: 1.20.1-forge-47.4.10
echo Directorio del juego:
echo %APPDATA%\.minecraft\instances\TikTokMinecraftLive
echo Si Forge no existia, este comando intento instalarlo automaticamente.
echo.
pause
exit /b 0

:error
echo.
echo Fallo preparando cliente Minecraft.
pause
exit /b 1
