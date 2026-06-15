@echo off
chcp 65001 >nul
title Actualizar TikTok Minecraft Live

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\actualizar_juego.ps1" -InstallRoot "%~dp0"
echo.
pause
