@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo JAR가 없으면 먼저 build-package.bat 을 실행하세요.
if not exist backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar (
  call build-package.bat
  if errorlevel 1 exit /b 1
)

echo EXE 생성 중 (jpackage 사용, JDK 14+ 필요)...
cd backend
jpackage ^
  --type exe ^
  --name "라인팀편성" ^
  --input target ^
  --main-jar line-team-assignment-0.0.1-SNAPSHOT.jar ^
  --main-class com.crew.lineteam.CrewLineTeamApplication ^
  --dest ..\dist ^
  --win-console ^
  --java-options "-Dcrew.lineteam.openBrowser=true"

cd ..
echo.
echo 완료. EXE: dist\라인팀편성-1.0.exe (또는 dist 폴더 내 설치 프로그램)
echo 설치 후 실행하면 localhost:8080 에서 서비스되며 브라우저가 자동으로 열립니다.
exit /b 0
