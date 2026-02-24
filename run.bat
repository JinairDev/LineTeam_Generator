@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

set "JAR=backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar"

if not exist "%JAR%" (
  echo JAR이 없습니다. 먼저 패키지를 빌드합니다.
  call build-package.bat
  if errorlevel 1 exit /b 1
)

echo.
echo 라인팀 편성 서버 시작 중... (종료: Ctrl+C)
echo 브라우저가 자동으로 열리면 http://localhost:8080 에서 사용하세요.
echo.
java -jar "%JAR%"
