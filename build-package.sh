#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

err() {
  echo "" >&2
  echo "[오류] $*" >&2
  exit 1
}

command -v node &>/dev/null || err "Node.js가 없습니다. node -v 로 확인 후 설치하세요. (https://nodejs.org)"
command -v npm &>/dev/null || err "npm이 없습니다. Node.js 설치 후 npm -v 로 확인하세요."
command -v mvn &>/dev/null || err "Maven이 없습니다. mvn -v 로 확인 후 설치하세요."

echo "[1/3] Frontend 의존성 설치 및 빌드 중..."
cd frontend
npm install || err "npm install 실패. 네트워크 또는 package.json을 확인하세요."
npm run build || err "Frontend 빌드 실패. 위 오류 메시지를 확인하세요."
cd ..
[[ -d frontend/dist ]] || err "Frontend 빌드 결과가 없습니다: frontend/dist"

echo "[2/3] 빌드 결과를 backend static으로 복사 중..."
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources
cp -R frontend/dist backend/src/main/resources/static || err "static 복사 실패"

echo "[3/3] Backend JAR 빌드 중..."
cd backend
mvn package -DskipTests -q || err "Backend JAR 빌드 실패. (Java 21, Maven 확인: java -version, mvn -v)"
cd ..

JAR="backend/target/line-team-assignment-0.0.1-SNAPSHOT.jar"
[[ -f "$JAR" ]] || err "JAR가 생성되지 않았습니다: $JAR"

echo ""
echo "완료. JAR: $JAR"
echo "실행: ./run.sh 또는 java -jar $JAR"
echo "      브라우저에서 http://localhost:8080 접속"
