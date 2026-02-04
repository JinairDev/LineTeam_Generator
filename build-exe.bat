@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo JAR가 없으면 먼저 build-package.bat 을 실행합니다.
if not exist backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar (
  call build-package.bat
  if errorlevel 1 exit /b 1
)

set JPACKAGE=jpackage
if defined JAVA_HOME (
  set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
  if not exist "%JPACKAGE%" set JPACKAGE=jpackage
)

where %JPACKAGE% >nul 2>&1
if errorlevel 1 (
  echo [오류] jpackage를 찾을 수 없습니다.
  echo JDK 14 이상을 설치하고 JAVA_HOME을 설정하세요. 예: set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.1
  echo 또는 PATH에 JDK\bin 을 추가하세요.
  exit /b 1
)

echo EXE 생성 중 (jpackage, JDK 14+ 필요)...
if not exist dist mkdir dist
cd backend
%JPACKAGE% --type exe --name "LineTeam_Generator" --input target --main-jar line-team-assignment-0.0.1-SNAPSHOT.jar --main-class com.crew.lineteam.CrewLineTeamApplication --dest ..\dist --win-console --java-options "-Dcrew.lineteam.openBrowser=true"
if errorlevel 1 (
  echo [오류] jpackage 실행 실패.
  cd ..
  exit /b 1
)

cd ..
echo.
echo 완료. EXE 또는 설치 프로그램: dist\ 폴더 확인
echo 실행 후 localhost:8080 에서 서비스되며 브라우저가 자동으로 열립니다.
exit /b 0
