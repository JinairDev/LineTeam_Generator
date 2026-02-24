#!/usr/bin/env bash
cd "$(dirname "$0")"

JAR="LineTeam_Generator.jar"
if [[ ! -f "$JAR" ]]; then
  echo "[오류] $JAR 파일이 없습니다."
  echo "이 폴더에 JAR 파일이 있어야 합니다. 배포용 ZIP을 다시 받거나, 프로젝트에서 build-release.sh 를 실행하세요."
  exit 1
fi

if ! command -v java &>/dev/null; then
  echo "[오류] Java를 찾을 수 없습니다."
  echo "Java 21 이상을 설치한 뒤 다시 실행하세요. https://adoptium.net/"
  exit 1
fi

echo ""
echo "라인팀 편성 프로그램을 시작합니다."
echo "브라우저가 자동으로 열리면 http://localhost:8080 에서 사용하세요."
echo "종료하려면 이 창에서 Ctrl+C 를 누르세요."
echo ""
java -jar "$JAR"
