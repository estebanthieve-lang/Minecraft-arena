@echo off
chcp 65001 >nul
title TikTok Minecraft Live - Preparar cliente

set "ROOT=%~dp0.."

rem Compatibilidad: versiones antiguas de la app llamaban este comando.
rem La fuente real del flujo ahora es PREPARAR_CLIENTE.cmd.
call "%ROOT%\PREPARAR_CLIENTE.cmd" %*
exit /b %ERRORLEVEL%
