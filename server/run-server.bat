@echo off
setlocal
set "JAR=%~dp0onewpipe-server-all.jar"
if not exist "%JAR%" (
  echo Missing onewpipe-server-all.jar next to this launcher.
  echo Download it from the ONewPipe GitHub release or build :server:fatJar first.
  exit /b 1
)
if "%JWT_SECRET%"=="" set "JWT_SECRET=change-this-secret-before-production"
if "%PORT%"=="" set "PORT=8080"
if "%DATA_DIR%"=="" set "DATA_DIR=%~dp0data"
java -jar "%JAR%"
endlocal
