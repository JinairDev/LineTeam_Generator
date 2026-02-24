#!/usr/bin/env bash
# Cursor에서 실행 시 "session ended" 나오면: Run 버튼 말고, 터미널 패널에서
#   cd /Users/sungjinpark/crew-line-team
#   ./run.sh
# 처럼 직접 입력해서 실행하세요.
set -e
cd "$(dirname "$0")"

JAR="backend/target/line-team-assignment-0.0.1-SNAPSHOT.jar"
BUILD_SCRIPT="build-package.sh"

err() {
  echo ""
  echo "[오류] $*" >&2
  if [ -t 0 ]; then
    read -r -p "엔터 키를 누르면 종료합니다..."
  fi
  exit 1
}

if [[ ! -f "$JAR" ]]; then
  echo "JAR이 없습니다. 먼저 패키지를 빌드합니다."
  if [[ ! -f "$BUILD_SCRIPT" ]]; then
    err "빌드 스크립트가 없습니다: $BUILD_SCRIPT"
  fi
  if [[ ! -x "$BUILD_SCRIPT" ]]; then
    echo "실행 권한이 없어서 권한을 추가한 뒤 다시 시도합니다."
    chmod +x "$BUILD_SCRIPT" 2>/dev/null || err "실행 권한 추가 실패. 터미널에서 실행: chmod +x $BUILD_SCRIPT"
  fi
  ./build-package.sh || err "빌드에 실패했습니다. 위 메시지를 확인하세요. (Node.js, Maven, Java 설치 여부 확인)"
fi

if [[ ! -f "$JAR" ]]; then
  err "JAR 파일이 생성되지 않았습니다: $JAR"
fi

if ! command -v java &>/dev/null; then
  err "java를 찾을 수 없습니다. Java 21 이상을 설치하고 PATH에 추가하세요. (확인: java -version)"
fi

echo ""
echo "라인팀 편성 서버 시작 중... (종료: Ctrl+C)"
echo "브라우저가 자동으로 열리면 http://localhost:8080 에서 사용하세요."
echo ""
java -jar "$JAR" || {
  echo ""
  echo "[오류] 서버가 종료되었거나 오류가 발생했습니다. 위 메시지를 확인하세요."
  if [ -t 0 ]; then
    read -r -p "엔터 키를 누르면 종료합니다..."
  fi
  exit 1
}
