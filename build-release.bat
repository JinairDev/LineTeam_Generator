@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================
echo   비개발자 전달용 패키지 생성 (한 번에 빌드)
echo ============================================
echo.

echo [1/2] 프론트+백엔드 한 번에 빌드 중...
call build-package.bat
if errorlevel 1 (
  echo 빌드 실패. 위 오류를 확인하세요.
  exit /b 1
)

echo.
echo [2/2] release 폴더에 실행 패키지 만들기...
if not exist release mkdir release
copy /Y "backend\target\line-team-assignment-0.0.1-SNAPSHOT.jar" "release\LineTeam_Generator.jar" >nul
if errorlevel 1 (
  echo JAR 복사 실패.
  exit /b 1
)

echo.
echo ============================================
echo   완료
echo ============================================
echo.
echo   release 폴더 안에 다음 파일이 있습니다:
echo     - LineTeam_Generator.jar  (실행 파일 하나)
echo     - start.bat               (Windows: 더블클릭으로 실행)
echo     - start.sh                (Mac: 더블클릭 또는 ./start.sh)
echo     - 사용방법.txt
echo.
echo   비개발자에게 전달하는 방법:
echo     1. release 폴더 전체를 ZIP으로 압축
echo     2. ZIP을 전달
echo     3. 받은 사람은 압축 풀고 start.bat(Windows) 또는 start.sh(Mac)만 실행
echo        -> 컴파일/빌드 없이 localhost:8080 이 바로 열림
echo.
echo   (수신자 PC에는 Java 21 이상만 설치되어 있으면 됩니다.)
echo.
pause
exit /b 0
