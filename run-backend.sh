#!/usr/bin/env bash
# Mac/Linux: Backend만 실행 (프로젝트 루트에서 실행)
# 회사망 PKIX 오류 시: cp build/maven-corp-pkix.env.example build/maven-corp-pkix.env 후 주석 해제
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$SCRIPT_DIR/mvn-backend.sh" spring-boot:run "$@"
