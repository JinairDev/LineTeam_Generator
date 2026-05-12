@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo [1/4] Frontend npm install...
cd frontend
call npm install
if errorlevel 1 goto npmfail
goto npmok
:npmfail
echo [FAIL] npm install failed. Install Node.js 20+ and add to PATH.
pause
exit /b 1
:npmok

echo [2/4] Frontend build...
call npm run build
if errorlevel 1 goto buildfail
goto buildok
:buildfail
echo [FAIL] Frontend build failed.
pause
exit /b 1
:buildok

echo [3/4] Copy frontend to backend static...
cd ..
if exist backend\src\main\resources\static rmdir /s /q backend\src\main\resources\static
xcopy /E /I /Y frontend\dist backend\src\main\resources\static

echo [4/4] Backend JAR build...
cd backend

if exist "D:\jdk-21.0.10.7-hotspot\bin\java.exe" set JAVA_HOME=D:\jdk-21.0.10.7-hotspot
if not defined JAVA_HOME if exist "C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe" set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
if not defined JAVA_HOME if exist "C:\Program Files\Java\jdk-21\bin\java.exe" set JAVA_HOME=C:\Program Files\Java\jdk-21
if not defined JAVA_HOME if exist "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe" set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17
if not defined JAVA_HOME if exist "C:\Program Files\Java\jdk-17\bin\java.exe" set JAVA_HOME=C:\Program Files\Java\jdk-17
if not defined JAVA_HOME if exist "D:\plugins\bin\java.exe" set JAVA_HOME=D:\plugins
if not defined JAVA_HOME if exist "C:\Program Files\Java\latest\bin\java.exe" set JAVA_HOME=C:\Program Files\Java\latest
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set JAVA_HOME=

if not defined JAVA_HOME goto nojava
goto havejava
:nojava
echo [FAIL] JAVA_HOME not set. Install JDK 21 and set JAVA_HOME.
pause
exit /b 1
:havejava

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME: %JAVA_HOME%

set MAVEN_CMD=
set MAVEN_HOME=D:\apache-maven-3.9.12-bin\apache-maven-3.9.12
if exist "%MAVEN_HOME%\bin\mvn.cmd" set MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd
if not defined MAVEN_CMD set MAVEN_CMD=mvn
if "%MAVEN_CMD%"=="mvn" where mvn >nul 2>&1
if errorlevel 1 if "%MAVEN_CMD%"=="mvn" goto nomaven

if "%MAVEN_CMD:\=%"=="%MAVEN_CMD%" (
  call %MAVEN_CMD% package -DskipTests -q
) else (
  call "%MAVEN_CMD%" package -DskipTests -q
)
if errorlevel 1 goto mavenfail
goto mavenok
:nomaven
echo [FAIL] Maven not found. Install Maven and set PATH or MAVEN_HOME.
pause
exit /b 1
:mavenfail
echo [FAIL] Backend build failed.
pause
exit /b 1
:mavenok

cd ..
echo.
echo [DONE] JAR: backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar
echo Run: java -jar backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar
echo Then open http://localhost:8080
echo.
if "%NO_PAUSE%"=="1" exit /b 0
pause
exit /b 0

