@echo off
chcp 65001 >nul
title Preparar servidor Minecraft Live Arena

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\preparar_servidor.ps1" -Root "%~dp0"
echo.
pause
