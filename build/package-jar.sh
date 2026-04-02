#!/usr/bin/env bash
# Mac/Linux: UI 포함 fat JAR만 만듭니다. (exe는 Windows에서 build/package-windows.bat)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# 선택: build/maven-corp-pkix.env (git 무시) — PKIX 시 Wagon SSL 우회 옵션
if [[ -f "$ROOT/build/maven-corp-pkix.env" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT/build/maven-corp-pkix.env"
fi
if [[ -n "${LINE_TEAM_MAVEN_OPTS:-}" ]]; then
  export MAVEN_OPTS="${MAVEN_OPTS:-} ${LINE_TEAM_MAVEN_OPTS}"
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "[오류] npm이 없습니다. Node.js 20+ 설치 후 다시 실행하세요." >&2
  exit 1
fi

echo "[1/2] frontend: npm ci && npm run build ..."
cd "$ROOT/frontend"
npm ci
npm run build
if [[ ! -d "$ROOT/frontend/dist" ]] || [[ -z "$(ls -A "$ROOT/frontend/dist" 2>/dev/null)" ]]; then
  echo "[오류] frontend/dist 가 없거나 비어 있습니다." >&2
  exit 1
fi

echo "[2/2] backend: mvn package -Pbundle-frontend ..."
cd "$ROOT/backend"
if [[ "${MAVEN_OFFLINE:-}" == "1" ]]; then
  mvn -o package -Pbundle-frontend
else
  mvn package -Pbundle-frontend
fi

JAR="$ROOT/backend/target/line-team-assignment-0.0.1-SNAPSHOT.jar"
echo "완료: $JAR"
echo "실행: java -jar \"$JAR\"  →  브라우저 http://localhost:8765"
