#!/usr/bin/env bash
# 프로젝트 어디서든 아님 — 이 스크립트가 있는 루트 기준으로 backend/pom.xml 에 Maven 명령 실행
# 예: ./mvn-backend.sh spring-boot:run
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
if [[ -f "$ROOT/build/maven-corp-pkix.env" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT/build/maven-corp-pkix.env"
fi
if [[ -n "${LINE_TEAM_MAVEN_OPTS:-}" ]]; then
  export MAVEN_OPTS="${MAVEN_OPTS:-} ${LINE_TEAM_MAVEN_OPTS}"
fi
exec mvn -f "$ROOT/backend/pom.xml" "$@"
