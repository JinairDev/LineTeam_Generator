@echo off
chcp 65001 >nul
setlocal
echo [1/3] Frontend 빌드 중...
cd /d "%~dp0frontend"
call npm run build
if errorlevel 1 (
  echo Frontend 빌드 실패.
  exit /b 1
)

echo [2/3] 빌드 결과를 backend static으로 복사 중...
cd /d "%~dp0"
if exist backend\src\main\resources\static rmdir /s /q backend\src\main\resources\static
xcopy /E /I /Y frontend\dist backend\src\main\resources\static

echo [3/3] Backend JAR 빌드 중...
cd backend
call mvn package -DskipTests -q
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
