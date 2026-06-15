@echo off
chcp 65001 >nul
title Compilar mod Minecraft Live Arena

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\compilar_mod_arena.ps1" -Root "%~dp0"
echo.
pause
