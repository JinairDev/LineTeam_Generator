@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

cd /d "%~dp0\.."
set "ROOT=%cd%"
set "JAR=line-team-assignment-0.0.1-SNAPSHOT.jar"

REM PKIX(회사망) 시 예: set LINE_TEAM_MAVEN_OPTS=-Daether.connector.https.securityMode=insecure
if defined LINE_TEAM_MAVEN_OPTS (
  if defined MAVEN_OPTS (
    set "MAVEN_OPTS=%MAVEN_OPTS% %LINE_TEAM_MAVEN_OPTS%"
  ) else (
    set "MAVEN_OPTS=%LINE_TEAM_MAVEN_OPTS%"
  )
)

where npm >nul 2>&1
if errorlevel 1 (
  echo [오류] npm을 찾을 수 없습니다. Node.js 20 이상 설치 후 PATH에 등록하세요.
  exit /b 1
)
where mvn >nul 2>&1
if errorlevel 1 (
  echo [오류] mvn을 찾을 수 없습니다. Maven 설치 후 PATH에 등록하세요.
  exit /b 1
)

echo.
echo [1/3] frontend: npm ci ^&^& npm run build ...
pushd "%ROOT%\frontend"
call npm ci
if errorlevel 1 popd & exit /b 1
call npm run build
if errorlevel 1 popd & exit /b 1
popd
if not exist "%ROOT%\frontend\dist\index.html" (
  echo [오류] frontend\dist 가 없습니다.
  exit /b 1
)

echo.
echo [2/3] Maven package ^(-Pbundle-frontend: dist를 JAR에 포함^)...
call mvn -f "%ROOT%\backend\pom.xml" package -Pbundle-frontend
if errorlevel 1 exit /b 1

if not exist "%ROOT%\backend\target\%JAR%" (
  echo [오류] JAR가 없습니다: backend\target\%JAR%
  exit /b 1
)

where jpackage >nul 2>&1
if errorlevel 1 (
  echo.
  echo [안내] jpackage를 찾을 수 없습니다 ^(JDK 17+의 bin이 PATH에 있어야 합니다^).
  echo   수동 실행: java -jar "backend\target\%JAR%"
  echo   브라우저에서 http://localhost:8765 로 접속하세요.
  exit /b 0
)

set "INPUT=%ROOT%\build\jpackage-input"
set "DEST=%ROOT%\build\dist-windows"
if exist "%INPUT%" rmdir /s /q "%INPUT%"
mkdir "%INPUT%"
copy /Y "%ROOT%\backend\target\%JAR%" "%INPUT%\" >nul

if exist "%DEST%" rmdir /s /q "%DEST%"
mkdir "%DEST%" 2>nul

echo.
echo [3/3] jpackage ^(app-image: 설치형 .exe 폴더 생성, WiX 불필요^)...

jpackage ^
  --type app-image ^
  --name LineTeamGenerator ^
  --input "%INPUT%" ^
  --main-jar "%JAR%" ^
  --main-class org.springframework.boot.loader.launch.JarLauncher ^
  --java-options "-Dfile.encoding=UTF-8 -Xms128m -Xmx512m" ^
  --dest "%DEST%" ^
  --app-version 1.0.0 ^
  --win-console

if errorlevel 1 (
  echo [오류] jpackage 실패. JDK 버전^/경로를 확인하세요.
  exit /b 1
)

echo.
echo 완료.
echo   실행 파일: %DEST%\LineTeamGenerator\LineTeamGenerator.exe
echo   ^(콘솔 창이 뜨면 서버 기동 로그입니다. 브라우저에서 http://localhost:8765^)
echo.
echo 설치형 단일 EXE 설치 마법사가 필요하면 WiX Toolset 설치 후
echo   build\package-windows-installer.bat 을 참고하세요.
endlocal
exit /b 0
