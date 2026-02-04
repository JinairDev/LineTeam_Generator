#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo "[1/3] Frontend 빌드 중..."
cd frontend
npm run build
cd ..

echo "[2/3] 빌드 결과를 backend static으로 복사 중..."
rm -rf backend/src/main/resources/static
cp -R frontend/dist backend/src/main/resources/static

echo "[3/3] Backend JAR 빌드 중..."
cd backend
mvn package -DskipTests -q
cd ..

echo ""
echo "완료. JAR: backend/target/line-team-assignment-0.0.1-SNAPSHOT.jar"
echo "실행: java -jar backend/target/line-team-assignment-0.0.1-SNAPSHOT.jar"
echo "      그 다음 브라우저에서 http://localhost:8080 접속"
