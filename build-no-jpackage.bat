@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ========================================
echo Building without jpackage
echo ========================================
echo.

REM Check JAR
if not exist backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar (
  echo JAR not found. Building...
  call build-package.bat
  if errorlevel 1 exit /b 1
)

REM Set JAVA_HOME explicitly (user provided path)
if exist "D:\jdk-21.0.10.7-hotspot\bin\java.exe" (
  set JAVA_HOME=D:\jdk-21.0.10.7-hotspot
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe" (
  set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
) else if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
  set JAVA_HOME=C:\Program Files\Java\jdk-21
) else if defined JAVA_HOME (
  REM Use existing JAVA_HOME if valid
  if not exist "%JAVA_HOME%\bin\java.exe" (
    set JAVA_HOME=
  )
) else (
  set JAVA_HOME=
)

if not defined JAVA_HOME (
  echo [ERROR] Java not found. Please install JDK 21 or set JAVA_HOME.
  exit /b 1
)

echo Using Java: %JAVA_HOME%
echo.

REM Create output directory
if exist dist\LineTeam_Generator (
  echo Removing existing folder...
  cmd /c "rmdir /s /q dist\LineTeam_Generator" 2>nul
  timeout /t 2 >nul
)

if not exist dist mkdir dist
if not exist dist\LineTeam_Generator mkdir dist\LineTeam_Generator
if not exist dist\LineTeam_Generator\app mkdir dist\LineTeam_Generator\app

REM Copy JAR
echo Copying JAR file...
copy backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar dist\LineTeam_Generator\app\ >nul

REM Create minimal JRE using jlink (no jpackage needed)
echo Creating minimal JRE...
set RUNTIME_DIR=%TEMP%\line-team-runtime-%RANDOM%
if exist "%RUNTIME_DIR%" rmdir /s /q "%RUNTIME_DIR%"

"%JAVA_HOME%\bin\jlink.exe" --add-modules java.base,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.security.jgss,java.sql,java.xml,jdk.unsupported --strip-debug --no-header-files --no-man-pages --compress=2 --output "%RUNTIME_DIR%"

if errorlevel 1 (
  echo [WARNING] jlink failed. Copying full JRE instead...
  REM 전체 JRE 복사 (jlink 실패 시 대안)
  if exist "%JAVA_HOME%\jre" (
    echo Copying full JRE from %JAVA_HOME%\jre...
    xcopy /E /I /Y "%JAVA_HOME%\jre" dist\LineTeam_Generator\runtime >nul
  ) else (
    echo Copying JDK runtime components...
    REM JDK에서 필요한 부분만 복사
    if not exist dist\LineTeam_Generator\runtime mkdir dist\LineTeam_Generator\runtime
    xcopy /E /I /Y "%JAVA_HOME%\bin" dist\LineTeam_Generator\runtime\bin >nul
    xcopy /E /I /Y "%JAVA_HOME%\lib" dist\LineTeam_Generator\runtime\lib >nul
    if exist "%JAVA_HOME%\conf" xcopy /E /I /Y "%JAVA_HOME%\conf" dist\LineTeam_Generator\runtime\conf >nul
    if exist "%JAVA_HOME%\legal" xcopy /E /I /Y "%JAVA_HOME%\legal" dist\LineTeam_Generator\runtime\legal >nul
    if exist "%JAVA_HOME%\release" copy "%JAVA_HOME%\release" dist\LineTeam_Generator\runtime\release >nul
  )
) else (
  echo Copying minimal JRE...
  xcopy /E /I /Y "%RUNTIME_DIR%" dist\LineTeam_Generator\runtime >nul
  rmdir /s /q "%RUNTIME_DIR%"
  
  REM jlink로 생성한 JRE에 jvm.cfg 파일 추가
  if not exist dist\LineTeam_Generator\runtime\lib\jvm.cfg (
    echo Creating jvm.cfg for jlink JRE...
    if not exist dist\LineTeam_Generator\runtime\lib mkdir dist\LineTeam_Generator\runtime\lib
    echo -server KNOWN > dist\LineTeam_Generator\runtime\lib\jvm.cfg
    echo -client IGNORE >> dist\LineTeam_Generator\runtime\lib\jvm.cfg
  )
)

REM Create run.bat
echo Creating run.bat...
(
echo @echo off
echo chcp 65001 ^>nul
echo cd /d "%%~dp0"
echo.
echo REM Try bundled JRE first, then system Java
echo if exist "runtime\bin\java.exe" ^(
echo   echo Using bundled JRE...
echo   set "JAVA_HOME=%%~dp0runtime"
echo   set "PATH=%%~dp0runtime\bin;%%PATH%%"
echo   runtime\bin\java.exe -Dcrew.lineteam.openBrowser=true -Dserver.port=8765 -jar app\line-team-assignment-0.0.1-SNAPSHOT.jar
echo   if errorlevel 1 ^(
echo     echo Bundled JRE failed, trying system Java...
echo     goto :use_system_java
echo   ^)
echo   goto :end
echo ^)
echo.
echo :use_system_java
echo if exist "D:\jdk-21.0.10.7-hotspot\bin\java.exe" ^(
echo   echo Using system Java from D:\jdk-21.0.10.7-hotspot...
echo   "D:\jdk-21.0.10.7-hotspot\bin\java.exe" -Dcrew.lineteam.openBrowser=true -Dserver.port=8765 -jar app\line-team-assignment-0.0.1-SNAPSHOT.jar
echo ^) else if defined JAVA_HOME ^(
echo   echo Using system Java from JAVA_HOME...
echo   "%%JAVA_HOME%%\bin\java.exe" -Dcrew.lineteam.openBrowser=true -Dserver.port=8765 -jar app\line-team-assignment-0.0.1-SNAPSHOT.jar
echo ^) else ^(
echo   echo Searching for Java in PATH...
echo   java -Dcrew.lineteam.openBrowser=true -Dserver.port=8765 -jar app\line-team-assignment-0.0.1-SNAPSHOT.jar
echo ^)
echo.
echo :end
echo pause
) > dist\LineTeam_Generator\run.bat

REM Create VBScript launcher (EXE-like)
echo Creating VBScript launcher...
(
echo Set WshShell = CreateObject("WScript.Shell"^)
echo Set fso = CreateObject("Scripting.FileSystemObject"^)
echo.
echo scriptDir = fso.GetParentFolderName(WScript.ScriptFullName^)
echo WshShell.CurrentDirectory = scriptDir
echo.
echo WshShell.Run "cmd /c run.bat", 1, False
) > dist\LineTeam_Generator\LineTeam_Generator.vbs

REM Create README
echo Creating README...
(
echo ========================================
echo LineTeam Generator - How to Run
echo ========================================
echo.
echo 1. Double-click LineTeam_Generator.vbs
echo    ^(or double-click run.bat^)
echo.
echo 2. Terminal window will open and browser will open automatically
echo    URL: http://localhost:8765
echo.
echo 3. Copy this entire folder to another computer - it will work
echo    ^(JRE is included, no Java installation needed^)
echo.
echo ========================================
) > dist\LineTeam_Generator\README.txt

echo.
echo ========================================
echo Complete!
echo ========================================
echo.
echo Location: dist\LineTeam_Generator\
echo.
echo Files created:
echo - LineTeam_Generator.vbs ^(double-click this^)
echo - run.bat ^(alternative launcher^)
echo - app\line-team-assignment-0.0.1-SNAPSHOT.jar
if exist dist\LineTeam_Generator\runtime (
  echo - runtime\ ^(bundled JRE^)
)
echo.
echo This works WITHOUT jpackage!
echo Just copy the entire folder and run LineTeam_Generator.vbs
echo.
exit /b 0

