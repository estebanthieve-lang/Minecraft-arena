@echo off
chcp 65001 >nul
title Hacer OP - Minecraft Live Arena

set /p PLAYER=Nombre de Minecraft para OP: 
if "%PLAYER%"=="" (
  echo Falta nombre.
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\hacer_op.ps1" -Root "%~dp0.." -PlayerName "%PLAYER%"
echo.
pause
