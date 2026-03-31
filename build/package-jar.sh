#!/usr/bin/env bash
# Mac/Linux: UI 포함 fat JAR만 만듭니다. (exe는 Windows에서 build/package-windows.bat)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/backend"
mvn package -Pbundle-frontend -DskipTests
JAR="$ROOT/backend/target/line-team-assignment-0.0.1-SNAPSHOT.jar"
echo "완료: $JAR"
echo "실행: java -jar \"$JAR\"  →  브라우저 http://localhost:8765"
