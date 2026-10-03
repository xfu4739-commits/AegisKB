@echo off
setlocal
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "JAR=%ROOT%\aegis-kb-business\aegis-kb-business-chat\target\aegis-kb-business-chat-0.0.1-SNAPSHOT.jar"
if not exist "%JAR%" (
  echo [dev-java] Missing jar: %JAR%
  echo [dev-java] Run start-local.ps1 again - it builds before start.
  exit /b 1
)
cd /d "%ROOT%"
java -jar "%JAR%"
exit /b %ERRORLEVEL%
