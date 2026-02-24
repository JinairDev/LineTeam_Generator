@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================
echo   비개발자 전달용 EXE 만들기 (jpackage)
echo ============================================
echo.

echo [1/2] JAR 준비 중...
if not exist backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar (
  echo JAR가 없습니다. 먼저 전체 빌드를 실행합니다.
  call build-package.bat
  if errorlevel 1 (
    echo 빌드 실패. Node.js, Maven, JDK 21 설치 후 다시 시도하세요.
    exit /b 1
  )
) else (
  echo JAR 있음: backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar
)

set JPACKAGE=jpackage
if defined JAVA_HOME (
  set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
  if not exist "%JPACKAGE%" set JPACKAGE=jpackage
)

where %JPACKAGE% >nul 2>&1
if errorlevel 1 (
  echo.
  echo [오류] jpackage를 찾을 수 없습니다.
  echo JDK 14 이상(권장: JDK 21)을 설치한 뒤:
  echo   - JAVA_HOME 을 설정하거나
  echo   - PATH에 JDK\bin 을 추가하세요.
  echo 예: set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
  exit /b 1
)

echo.
echo [2/2] EXE 설치 프로그램 생성 중 (JRE 포함, 1~2분 소요)...
if not exist dist mkdir dist

rem JAR 매니페스트의 Main-Class(JarLauncher) 사용. --main-class 생략.
%JPACKAGE% ^
  --type exe ^
  --name "승무원라인팀편성" ^
  --app-version 1.0 ^
  --input backend\target ^
  --main-jar line-team-assignment-0.0.1-SNAPSHOT.jar ^
  --dest dist ^
  --win-console ^
  --java-options "-Dserver.port=8080"

if errorlevel 1 (
  echo.
  echo [오류] jpackage 실행 실패. JDK 21 권장.
  exit /b 1
)

if exist "사용방법-EXE설치후.txt" copy /Y "사용방법-EXE설치후.txt" "dist\사용방법-EXE설치후.txt" >nul

echo.
echo ============================================
echo   완료
echo ============================================
echo.
echo   dist\ 폴더에 다음 파일이 생성되었습니다:
echo     - 승무원라인팀편성-1.0.exe  (설치 프로그램)
echo.
echo   비개발자에게 전달하는 방법:
echo     1. dist\승무원라인팀편성-1.0.exe 만 전달 (또는 dist 폴더 전체 압축)
echo     2. 받은 사람은 EXE 더블클릭 → 설치 → 설치된 "승무원라인팀편성" 실행
echo     3. 실행 후 브라우저에서 http://localhost:8080 접속
echo        (자동으로 브라우저가 열리지 않으면 위 주소를 직접 입력)
echo.
echo   ※ 수신자 PC에는 Java 설치 불필요 (EXE에 JRE 포함됨)
echo.
pause
exit /b 0
