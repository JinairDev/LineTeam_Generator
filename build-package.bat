@echo off
chcp 65001 >nul
cd /d "%~dp0"
REM setlocal을 제거하여 JAVA_HOME이 하위 프로세스에 전달되도록 함

echo [1/4] Frontend 의존성 설치 중...
cd frontend
call npm install
if errorlevel 1 (
  echo npm install 실패. Node.js 20 이상 설치 및 PATH 확인하세요.
  exit /b 1
)

echo [2/4] Frontend 빌드 중...
call npm run build
if errorlevel 1 (
  echo Frontend 빌드 실패.
  exit /b 1
)

echo [3/4] 빌드 결과를 backend static으로 복사 중...
cd ..
if exist backend\src\main\resources\static rmdir /s /q backend\src\main\resources\static
xcopy /E /I /Y frontend\dist backend\src\main\resources\static

echo [4/4] Backend JAR 빌드 중...
cd backend

REM 사용자 지정 Java 경로 우선 사용 (기존 JAVA_HOME 무시)
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
  REM 기존 JAVA_HOME 사용 (하지만 경로 확인)
  if not exist "%JAVA_HOME%\bin\java.exe" (
    set JAVA_HOME=
  )
)

if not defined JAVA_HOME (
  echo [오류] JAVA_HOME을 찾을 수 없습니다. Java가 설치되어 있는지 확인하세요.
  exit /b 1
)

echo Using JAVA_HOME: %JAVA_HOME%

REM JAVA_HOME이 설정되었으면 PATH에 추가
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM Maven 경로 확인 및 설정
set MAVEN_HOME=D:\apache-maven-3.9.12-bin\apache-maven-3.9.12
if exist "%MAVEN_HOME%\bin\mvn.cmd" (
  REM JAVA_HOME을 명시적으로 설정하고 Maven 실행
  set "JAVA_HOME=%JAVA_HOME%"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
  echo JAVA_HOME=%JAVA_HOME%
  "%JAVA_HOME%\bin\java.exe" -version
  call "%MAVEN_HOME%\bin\mvn.cmd" package -DskipTests -q
) else (
  REM PATH에서 Maven 찾기 시도
  where mvn >nul 2>&1
  if errorlevel 1 (
    echo [오류] Maven을 찾을 수 없습니다. MAVEN_HOME을 설정하거나 PATH에 추가하세요.
    exit /b 1
  )
  set "JAVA_HOME=%JAVA_HOME%"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
  call mvn package -DskipTests -q
)

if errorlevel 1 (
  echo Backend 빌드 실패.
  exit /b 1
)

cd ..
echo.
echo 완료. JAR: backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar
echo 실행: java -jar backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar
echo       그 다음 브라우저에서 http://localhost:8080 접속
exit /b 0

