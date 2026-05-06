@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================
echo   비개발자 전달용 EXE 만들기 (jpackage)
echo ============================================
echo.
if "%1"=="/f" goto needjar
if "%1"=="rebuild" goto needjar
if "%1"=="-f" goto needjar

echo [1/2] JAR prepare...
if not exist backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar goto needjar
echo JAR exists. Skip build. To use latest code run: build-exe.bat rebuild
goto jarready
:needjar
echo JAR not found. Running build-package.bat...
call build-package.bat
if errorlevel 1 goto buildfail
goto jarready
:buildfail
echo Build failed. Check Node.js, Maven, JDK 21.
exit /b 1
:jarready

set "JPACKAGE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
if not defined JPACKAGE if exist "D:\plugins\bin\jpackage.exe" set "JPACKAGE=D:\plugins\bin\jpackage.exe"
if not defined JPACKAGE if exist "C:\Program Files\Java\latest\bin\jpackage.exe" set "JPACKAGE=C:\Program Files\Java\latest\bin\jpackage.exe"
if not defined JPACKAGE if exist "C:\Program Files\Java\latest\jdk-21\bin\jpackage.exe" set "JPACKAGE=C:\Program Files\Java\latest\jdk-21\bin\jpackage.exe"
if not defined JPACKAGE if exist "C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot\bin\jpackage.exe" set "JPACKAGE=C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot\bin\jpackage.exe"
if not defined JPACKAGE if exist "C:\Program Files\Eclipse Adoptium\jdk-21\bin\jpackage.exe" set "JPACKAGE=C:\Program Files\Eclipse Adoptium\jdk-21\bin\jpackage.exe"
if not defined JPACKAGE if exist "C:\Program Files\Java\jdk-21\bin\jpackage.exe" set "JPACKAGE=C:\Program Files\Java\jdk-21\bin\jpackage.exe"

if defined JPACKAGE goto havejpackage
where jpackage.exe >nul 2>&1
if errorlevel 1 goto nojpackage
set "JPACKAGE=jpackage.exe"
goto havejpackage
:nojpackage
echo.
echo [ERROR] jpackage not found. JDK 21 needed - JRE is not enough.
echo Set JAVA_HOME to JDK folder that has bin\jpackage.exe
echo Example: set JAVA_HOME=D:\plugins
echo Then run build-exe.bat again in a NEW cmd window.
exit /b 1
:havejpackage

echo.
echo [2/2] Creating app-image with jpackage - no WiX required...
if not exist dist mkdir dist
if exist "dist\승무원라인팀편성" rmdir /s /q "dist\승무원라인팀편성"

rem app-image = folder with exe launcher. exe/msi need WiX Toolset.
rem -Xmx: PC 메모리/페이지파일이 빡빡할 때 기본 힥(~수 GB) 예약으로 mmap 실패 방지
"%JPACKAGE%" ^
  --type app-image ^
  --name "승무원라인팀편성" ^
  --app-version 1.0 ^
  --input backend\target ^
  --main-jar line-team-assignment-0.0.1-SNAPSHOT.jar ^
  --dest dist ^
  --win-console ^
  --java-options "-Dserver.port=8080 -Xms128m -Xmx512m"

if errorlevel 1 goto jpackagefail
goto jpackagedone
:jpackagefail
echo.
echo [ERROR] jpackage failed. Use JDK 21.
exit /b 1
:jpackagedone

if exist "사용방법-EXE설치후.txt" copy /Y "사용방법-EXE설치후.txt" "dist\승무원라인팀편성\사용방법-EXE설치후.txt" >nul 2>&1

echo.
echo ============================================
echo   Done
echo ============================================
echo.
echo   Output: dist\승무원라인팀편성\
echo     - Run: 승무원라인팀편성.exe
echo.
echo   To share: zip dist\승무원라인팀편성 folder, send zip.
echo   User: unzip, run 승무원라인팀편성.exe, open http://localhost:8080
echo   Java not required on user PC.
echo.
pause
exit /b 0
