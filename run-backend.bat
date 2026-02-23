@echo off
chcp 65001 >nul
cd /d "%~dp0"

REM Java 경로 설정 (build-package.bat과 동일)
if exist "D:\jdk-21.0.10.7-hotspot\bin\java.exe" (
  set JAVA_HOME=D:\jdk-21.0.10.7-hotspot
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe" (
  set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
) else if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
  set JAVA_HOME=C:\Program Files\Java\jdk-21
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe" (
  set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17
) else if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
  set JAVA_HOME=C:\Program Files\Java\jdk-17
) else if defined JAVA_HOME (
  if not exist "%JAVA_HOME%\bin\java.exe" set JAVA_HOME=
)

if not defined JAVA_HOME (
  echo [오류] Java를 찾을 수 없습니다. JDK 17/21을 설치하세요.
  pause
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using Java: %JAVA_HOME%

REM Maven 경로 (아래 중 설치된 경로가 있으면 사용, 없으면 PATH의 mvn 사용)
set "MAVEN_CMD="
if exist "D:\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd" (
  set "MAVEN_CMD=D:\apache-maven-3.9.12-bin\apache-maven-3.9.12\bin\mvn.cmd"
) else if exist "C:\Program Files\apache-maven-3.9.6\bin\mvn.cmd" (
  set "MAVEN_CMD=C:\Program Files\apache-maven-3.9.6\bin\mvn.cmd"
) else if exist "C:\apache-maven-3.9.6\bin\mvn.cmd" (
  set "MAVEN_CMD=C:\apache-maven-3.9.6\bin\mvn.cmd"
) else (
  where mvn.cmd >nul 2>&1
  if errorlevel 1 (
    where mvn >nul 2>&1
    if errorlevel 1 (
      echo [오류] Maven을 찾을 수 없습니다.
      echo.
      echo 해결 방법 1: Maven 설치 후 PATH에 추가
      echo   - https://maven.apache.org/download.cgi 에서 다운로드
      echo   - 압축 해제 후 bin 폴더를 시스템 PATH에 추가
      echo.
      echo 해결 방법 2: 이 배치 파일 수정
      echo   - 40번 줄 근처에 set "MAVEN_CMD=본인Maven경로\bin\mvn.cmd" 추가
      echo   - 예: set "MAVEN_CMD=D:\tools\apache-maven-3.9.6\bin\mvn.cmd"
      pause
      exit /b 1
    )
    set "MAVEN_CMD=mvn"
  ) else (
    set "MAVEN_CMD=mvn.cmd"
  )
)

echo Backend 시작 중 (backend 폴더에서 mvn spring-boot:run)...
echo 종료하려면 이 창에서 Ctrl+C 누르세요.
echo.
cd backend
if "%MAVEN_CMD:\=%"=="%MAVEN_CMD%" (
  call %MAVEN_CMD% spring-boot:run
) else (
  call "%MAVEN_CMD%" spring-boot:run
)
pause
