@echo off
chcp 65001 >nul
cd /d "%~dp0"
REM 회사망 PKIX: set LINE_TEAM_MAVEN_OPTS=-Daether.connector.https.securityMode=insecure
if defined LINE_TEAM_MAVEN_OPTS (
  set "MAVEN_OPTS=%MAVEN_OPTS% %LINE_TEAM_MAVEN_OPTS%"
)
call mvn -f "%~dp0backend\pom.xml" %*
