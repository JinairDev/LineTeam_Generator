#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

err() {
  echo "" >&2
  echo "[오류] $*" >&2
  exit 1
}

[[ -f build-package.sh ]] || err "build-package.sh 가 없습니다."
[[ -x build-package.sh ]] || { chmod +x build-package.sh || err "chmod +x build-package.sh 실패"; }

echo "============================================"
echo "  비개발자 전달용 패키지 생성 (한 번에 빌드)"
echo "============================================"
echo ""

echo "[1/2] 프론트+백엔드 한 번에 빌드 중..."
./build-package.sh || err "빌드 실패. 위 메시지를 확인하세요."

SRC_JAR="backend/target/line-team-assignment-0.0.1-SNAPSHOT.jar"
[[ -f "$SRC_JAR" ]] || err "JAR가 없습니다: $SRC_JAR"

echo ""
echo "[2/2] release 폴더에 실행 패키지 만들기..."
mkdir -p release
cp -f "$SRC_JAR" release/LineTeam_Generator.jar || err "JAR 복사 실패"
[[ -f release/start.sh ]] && chmod +x release/start.sh
[[ -f release/LineTeam_Generator.jar ]] || err "release/LineTeam_Generator.jar 생성 실패"

echo ""
echo "============================================"
echo "  완료"
echo "============================================"
echo ""
echo "  release 폴더 안에 다음 파일이 있습니다:"
echo "    - LineTeam_Generator.jar  (실행 파일 하나)"
echo "    - start.bat               (Windows: 더블클릭으로 실행)"
echo "    - start.sh                (Mac: 더블클릭 또는 ./start.sh)"
echo "    - 사용방법.txt"
echo ""
echo "  비개발자에게 전달하는 방법:"
echo "    1. release 폴더 전체를 ZIP으로 압축"
echo "    2. ZIP을 전달"
echo "    3. 받은 사람은 압축 풀고 start.bat(Windows) 또는 start.sh(Mac)만 실행"
echo "       -> 컴파일/빌드 없이 localhost:8080 이 바로 열림"
echo ""
echo "  (수신자 PC에는 Java 21 이상만 설치되어 있으면 됩니다.)"
echo ""
