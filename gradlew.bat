@echo off
REM Bootstrap Gradle 9.2 (richiesto da Fabric Loom 1.14 per MC 1.21.11).
set GRADLE_VER=9.2.1
set BASE=%~dp0.gradle-bootstrap
set DIST=%BASE%\gradle-%GRADLE_VER%
if not exist "%DIST%\bin\gradle.bat" (
  echo Download Gradle %GRADLE_VER%...
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VER%-bin.zip' -OutFile '%BASE%\gradle.zip'"
  powershell -NoProfile -Command "Expand-Archive -Path '%BASE%\gradle.zip' -DestinationPath '%BASE%' -Force"
)
call "%DIST%\bin\gradle.bat" %*
