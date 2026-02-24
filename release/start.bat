@echo off
chcp 65001 >nul
cd /d "%~dp0"

set "JAR=LineTeam_Generator.jar"
if not exist "%JAR%" (
  echo [오류] %JAR% 파일이 없습니다.
  echo 이 폴더에 JAR 파일이 있어야 합니다. 배포용 ZIP을 다시 받거나, 프로젝트에서 build-release.bat 을 실행하세요.
  pause
  exit /b 1
)

if not defined JAVA_HOME (
  where java >nul 2>nul
  if errorlevel 1 (
    echo [오류] Java를 찾을 수 없습니다.
    echo Java 21 이상을 설치한 뒤 다시 실행하세요. https://adoptium.net/
    pause
    exit /b 1
  )
)

echo.
echo 라인팀 편성 프로그램을 시작합니다.
echo 브라우저가 자동으로 열리면 http://localhost:8080 에서 사용하세요.
echo 종료하려면 이 창을 닫거나 Ctrl+C 를 누르세요.
echo.
java -jar "%JAR%"
if errorlevel 1 (
  echo.
  echo 프로그램이 종료되었거나 오류가 발생했습니다.
  pause
)
exit /b 0
