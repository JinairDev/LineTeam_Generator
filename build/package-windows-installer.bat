@echo off
chcp 65001 >nul
REM WiX Toolset 3.0+ 설치 후에만 동작합니다. JDK jpackage --type exe 요구.
REM 먼저 package-windows.bat 으로 JAR까지 만든 뒤, 이 스크립트로 설치 마법사형 exe를 시도합니다.

cd /d "%~dp0\.."
set "ROOT=%cd%"
set "JAR=line-team-assignment-0.0.1-SNAPSHOT.jar"
set "INPUT=%ROOT%\build\jpackage-input"
set "DEST=%ROOT%\build\dist-windows-installer"

if not exist "%ROOT%\backend\target\%JAR%" (
  echo 먼저 build\package-windows.bat 을 실행하거나 mvn package -Pbundle-frontend 하세요.
  exit /b 1
)

if not exist "%INPUT%\%JAR%" (
  mkdir "%INPUT%" 2>nul
  copy /Y "%ROOT%\backend\target\%JAR%" "%INPUT%\"
)

if exist "%DEST%" rmdir /s /q "%DEST%"
mkdir "%DEST%" 2>nul

jpackage ^
  --type exe ^
  --name LineTeamGenerator ^
  --input "%INPUT%" ^
  --main-jar "%JAR%" ^
  --main-class org.springframework.boot.loader.launch.JarLauncher ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --dest "%DEST%" ^
  --app-version 1.0.0 ^
  --win-console

if errorlevel 1 (
  echo jpackage --type exe 실패 시 WiX 설치 여부를 확인하세요.
  exit /b 1
)
echo 출력: %DEST%
