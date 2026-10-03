@echo off
setlocal
cd /d "%~dp0..\vue"
call npm.cmd run dev
endlocal
