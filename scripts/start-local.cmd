@echo off
setlocal
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -NoExit -File "%~dp0start-local.ps1" %*
endlocal
