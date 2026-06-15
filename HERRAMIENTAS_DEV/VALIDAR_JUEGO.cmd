@echo off
chcp 65001 >nul
title Validar TikTok Minecraft Live

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\validar_juego.ps1" -Root "%~dp0"
echo.
pause
