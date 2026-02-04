# 승무원 라인팀 편성 시스템 (프로토타입)

승무원 재직 현황 엑셀을 올리면 라인팀을 자동 편성하고, 결과를 조회·수정·엑셀 추출할 수 있는 웹 앱입니다.

**이 README는** 개발 환경 세팅이 처음이거나, 이 프로젝트를 처음 받아서 실행해 보는 분을 위한 가이드입니다. 아래 순서대로 진행하면 됩니다.

---

## 목차

1. [사전 요구사항](#사전-요구사항)
2. [프로젝트 받기](#프로젝트-받기)
3. [Backend 실행](#backend-실행)
4. [Frontend 실행](#frontend-실행)
5. [실행 순서 요약](#실행-순서-요약)
6. [웹에서 사용 순서](#웹에서-사용-순서)
7. [문제 해결 (FAQ)](#문제-해결-faq)
8. [단일 JAR / EXE로 실행하기](#단일-jar--exe로-실행하기)

---

## 사전 요구사항

아래 세 가지가 **컴퓨터에 설치되어 있어야** 합니다. 하나라도 없으면 실행이 되지 않습니다.

| 항목 | 필요 버전 | 용도 |
|------|-----------|------|
| **Node.js** | 20 이상 | Frontend 실행 (npm 포함) |
| **Java** | 21 이상 | Backend 실행 |
| **Maven** | 3.x | Backend 빌드·실행 |

### Node.js 설치

1. [https://nodejs.org](https://nodejs.org) 접속
2. **LTS** 버전 다운로드 후 설치
3. 터미널(또는 명령 프롬프트)을 열고 다음 입력:
   ```bash
   node -v
   npm -v
   ```
   - `v20.x.x`, `v22.x.x` 같은 숫자와 `10.x.x` 같은 숫자가 나오면 성공
   - `command not found`(Mac/Linux) 또는 `'node'은(는) 인식되지 않습니다`(Windows)가 나오면 PATH 설정을 확인하거나 설치를 다시 진행

### Java 설치

1. **OpenJDK 21** 중 하나 선택:
   - [Adoptium (Eclipse Temurin)](https://adoptium.net/) → 21 LTS 다운로드
   - [Oracle JDK 21](https://www.oracle.com/java/technologies/downloads/#java21) (라이선스 확인 후 사용)
2. 설치 후 터미널에서 확인:
   ```bash
   java -v
   javac -v
   ```
   - `version "21.x.x"` 같은 문구가 나오면 성공
   - Mac: `brew install openjdk@21` 후 PATH 설정도 가능

### Maven 설치

1. [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi) 에서 **Binary zip** 다운로드
2. 압축 해제 후 폴더를 원하는 위치로 이동 (예: `C:\Program Files\apache-maven-3.9.x` 또는 `/opt/maven`)
3. **환경 변수** 설정:
   - `M2_HOME` (또는 `MAVEN_HOME`): Maven 폴더 경로
   - `PATH`에 `%M2_HOME%\bin`(Windows) 또는 `$M2_HOME/bin`(Mac/Linux) 추가
4. 터미널에서 확인:
   ```bash
   mvn -v
   ```
   - `Apache Maven 3.x.x` 가 나오면 성공
   - **Mac**: `brew install maven` 으로 설치할 수도 있습니다.

---

## 프로젝트 받기

**아래 모든 명령은 터미널(또는 명령 프롬프트)에서 실행합니다.**  
Windows는 `cmd` 또는 PowerShell, Mac/Linux는 터미널을 사용하면 됩니다.

### Git으로 받는 경우

```bash
git clone <저장소 URL>
cd crew-line-team
```

`crew-line-team` 이 현재 폴더가 된 상태에서 다음 단계로 진행합니다.

### ZIP으로 받는 경우

1. 저장소에서 **Code → Download ZIP** 으로 다운로드
2. ZIP 압축 해제
3. 터미널에서 압축 푼 폴더로 이동:
   ```bash
   cd /경로/crew-line-team
   ```
   (예: `cd ~/Downloads/crew-line-team-main`)

---

## Backend 실행

Backend(Spring Boot)가 먼저 떠 있어야 Frontend에서 API를 사용할 수 있습니다.

### 1단계: backend 폴더로 이동

**반드시 프로젝트 루트(`crew-line-team`)에서** 아래를 실행하세요.

```bash
cd backend
```

### 2단계: Backend 서버 실행

```bash
mvn spring-boot:run
```

- **처음 실행 시** Maven이 의존성을 다운로드하므로 1~3분 정도 걸릴 수 있습니다.
- 아래와 비슷한 로그가 나오면 정상입니다:
  ```text
  Started CrewLineTeamApplication in ... seconds
  ```

### 3단계: 동작 확인

- 브라우저에서 [http://localhost:8080](http://localhost:8080) 접속
- 에러 페이지(404 등)가 나와도 **서버가 응답했다면** Backend는 정상 동작 중입니다. (API 경로는 `/api/...` 이므로 루트만 보면 404가 나올 수 있음)
- **이 터미널은 종료하지 마세요.** Backend가 계속 켜져 있어야 합니다.

---

## Frontend 실행

Backend가 이미 실행 중인 상태에서, **새 터미널 창**을 열어 진행합니다.

### 1단계: 프로젝트 루트로 이동

새 터미널에서:

```bash
cd /경로/crew-line-team
```

(앞에서 `git clone` 이나 ZIP 압축 해제한 `crew-line-team` 폴더 경로로 이동)

### 2단계: frontend 폴더로 이동

```bash
cd frontend
```

### 3단계: 의존성 설치 (최초 1회만)

```bash
npm install
```

- `package.json`에 적힌 라이브러리를 다운로드합니다. **처음 한 번만** 하면 됩니다.
- `node_modules` 폴더가 생기고 끝나면 다음 단계로 갑니다.

### 4단계: 개발 서버 실행

```bash
npm run dev
```

- 터미널에 예를 들어 다음과 같이 나옵니다:
  ```text
  VITE v5.x.x  ready in xxx ms
  ➜  Local:   http://localhost:5173/
  ```
- 이 주소(**http://localhost:5173**)를 **브라우저 주소창에 그대로 입력**해서 엽니다.

### 5단계: 화면 확인

- "승무원 라인팀 편성" 관련 화면이 보이면 **Frontend까지 정상 실행**된 것입니다.
- API 요청은 자동으로 Backend(`http://localhost:8080/api`)로 전달됩니다.

---

## 실행 순서 요약

한 번에 복사해서 쓰기 어렵다면, 아래 순서만 기억하면 됩니다.

1. **Backend**  
   터미널 1:
   ```bash
   cd crew-line-team/backend
   mvn spring-boot:run
   ```
   → 로그에 `Started ...` 나올 때까지 대기

2. **Frontend**  
   터미널 2 (새 창):
   ```bash
   cd crew-line-team/frontend
   npm install   # 최초 1회만
   npm run dev
   ```

3. **브라우저**  
   [http://localhost:5173](http://localhost:5173) 접속

**권장:** Backend를 먼저 실행한 뒤 Frontend를 실행하세요.

---

## 웹에서 사용 순서

실행까지 완료했다면, 아래 순서대로 **웹 화면**에서 사용하면 됩니다.

1. **재직 현황 불러오기**: 엑셀 파일 선택 → 업로드 후 인원 수 확인  
2. **편성 실행**: "편성 실행" 버튼 클릭  
3. **결과 확인**: 팀별 카드에서 멤버 확인, 필요 시 드래그로 다른 팀으로 이동  
4. **엑셀 추출**: "엑셀 추출" 버튼으로 `line-teams.xlsx` 다운로드  

---

## 단일 JAR / EXE로 실행하기

프론트와 백엔드를 **하나의 프로그램**으로 묶어서, **localhost:8080** 에서만 동작하는 실행 파일로 만들 수 있습니다.

### 1. 단일 JAR 만들기 (한 번에 빌드)

프론트를 빌드한 뒤 백엔드 JAR에 넣어서, **JAR 하나**로 서버+화면을 모두 띄웁니다.

**Windows**
```bat
build-package.bat
```

**Mac / Linux**
```bash
chmod +x build-package.sh
./build-package.sh
```

- 프론트 빌드 → `backend/src/main/resources/static` 으로 복사 → 백엔드 `mvn package` 까지 자동으로 진행됩니다.
- 완료 후 실행:
  ```bash
  java -jar backend/target/line-team-assignment-0.0.1-SNAPSHOT.jar
  ```
- 브라우저에서 **http://localhost:8080** 접속하면 됩니다. (실행 시 브라우저가 자동으로 열리도록 설정되어 있습니다.)

### 2. EXE 파일 만들기 (Windows)

**JDK 14 이상**에 포함된 `jpackage` 로 Windows용 설치형/실행 파일을 만들 수 있습니다.

**순서**
1. **명령 프롬프트(cmd)** 또는 **PowerShell**을 **관리자 권한 없이** 연다.
2. 프로젝트 폴더로 이동: `cd 경로\LineTeam_Generator`
3. 한 번에 실행:
   ```bat
   build-exe.bat
   ```
   (JAR가 없으면 `build-package.bat` 이 자동으로 먼저 실행된다.)

- 생성 결과는 **dist** 폴더에 들어갑니다 (`.exe` 또는 설치 프로그램).
- 설치/실행 후 **localhost:8080** 에서 서비스되며, 브라우저가 자동으로 열립니다.

**필수**
- **JDK**가 필요합니다. `java -version`만 되고 `jpackage`가 없다면 JDK가 아닌 JRE만 설치된 상태일 수 있습니다. [Adoptium JDK 21](https://adoptium.net/) 등을 설치한 뒤 **JAVA_HOME**을 JDK 설치 경로로 설정하세요.
  ```bat
  set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.1
  ```
- **Node.js 20 이상**, **Maven**도 필요합니다. (`build-package.bat` 에서 사용)

**EXE 빌드가 안 될 때**
- `jpackage를 찾을 수 없습니다` → JDK를 설치하고, 위처럼 `JAVA_HOME` 설정 후 **새 명령 프롬프트**에서 다시 실행.
- `npm install 실패` / `Frontend 빌드 실패` → Node.js 설치 및 `node -v`, `npm -v` 확인.
- `Backend 빌드 실패` → Maven 설치 및 `mvn -v` 확인.
- 그래도 안 되면 **GitHub Actions**로 받기: 저장소 **Actions** 탭 → **Build Windows EXE** → **Run workflow** → 완료 후 **Artifacts**에서 다운로드. (Windows에서 직접 빌드하지 않아도 됨)

### 3. 요약

| 목표 | 방법 |
|------|------|
| 개발 없이 JAR로만 실행 | `build-package.bat`(또는 .sh) → `java -jar backend/target/line-team-assignment-0.0.1-SNAPSHOT.jar` |
| Windows에서 EXE로 배포 | `build-exe.bat` → dist 폴더의 설치 프로그램 사용 |

---

## 문제 해결 (FAQ)

### `mvn: command not found` (또는 `mvn`을 찾을 수 없습니다)

- Maven이 설치되지 않았거나, 터미널에서 찾지 못하는 상태입니다.
- [사전 요구사항 - Maven 설치](#maven-설치)를 다시 진행하고, **PATH**에 `bin` 경로가 들어갔는지 확인하세요.
- 터미널을 **다시 연 뒤** `mvn -v` 로 확인합니다.

### `java: command not found` 또는 Java 버전이 21 미만

- Java 21 이상을 설치하고, `java -v` 로 버전을 확인하세요.
- 여러 Java가 설치된 경우, `JAVA_HOME`이 21을 가리키는지 확인합니다.

### `node: command not found` 또는 Node 버전이 20 미만

- Node.js 20 이상 LTS를 설치한 뒤, `node -v` 로 확인하세요.
- Mac/Linux에서 버전 여러 개 쓰려면 `nvm`(Node Version Manager)을 쓰는 방법도 있습니다.

### 포트 8080(또는 5173)이 이미 사용 중입니다

- 다른 프로그램이 8080 또는 5173을 쓰고 있을 수 있습니다.
- **Mac/Linux** 에서 사용 중인 프로세스 확인:
  ```bash
  lsof -i :8080
  lsof -i :5173
  ```
- 해당 프로그램을 종료하거나, Backend/Frontend의 포트를 설정에서 다른 번호로 바꿀 수 있습니다. (Backend: `application.properties` 의 `server.port`, Frontend: `vite.config.ts` 의 `server.port`)

### Frontend에서 "엑셀 업로드 실패", "편성 실패" 등 API 오류

- **Backend가 켜져 있는지** 확인하세요. Backend 터미널에 `mvn spring-boot:run` 이 그대로 실행 중이어야 합니다.
- 브라우저에서 [http://localhost:8080](http://localhost:8080) 이 열리는지 확인합니다. (404라도 응답이 오면 서버는 동작 중)

### `npm install` 시 권한 오류 또는 네트워크 오류

- 터미널을 **관리자 권한**으로 열 필요는 없습니다. 보통은 프로젝트 폴더 권한이나 네트워크(방화벽, 프록시) 문제일 수 있습니다.
- 회사 네트워크라면 프록시 설정이 필요할 수 있습니다. (`npm config set proxy ...` 등)

### Windows에서 EXE 빌드가 안 됩니다

- **jpackage를 찾을 수 없습니다** → JDK(Java Development Kit)를 설치하세요. JRE만 있으면 안 됩니다. [Adoptium JDK 21](https://adoptium.net/) 설치 후 `set JAVA_HOME=JDK설치경로` 로 설정하고, **새 cmd** 창에서 `build-exe.bat` 다시 실행.
- **npm / mvn 오류** → [단일 JAR / EXE로 실행하기](#단일-jar--exe로-실행하기)의 "필수" 항목대로 Node.js, Maven 설치 및 PATH 확인.
- **직접 빌드 없이 EXE만 받기** → GitHub 저장소 **Actions** 탭 → **Build Windows EXE** → **Run workflow** → 완료 후 **Artifacts**에서 `LineTeam_Generator-Windows` 다운로드.

---

## 스펙

### Frontend
- **Runtime**: Node.js 20 이상
- **Core**: React ^18.3.1, TypeScript ^5.6.3
- **Build**: Vite 5

### Backend
- **Runtime**: Java 21 이상
- **Framework**: Spring Boot 3.5 이상

---

## 3단계 흐름

1. **재직 현황 불러오기**  
   엑셀 파일(.xlsx) 업로드 → 사번, 이름, 직급, 성별, Rank, 근거지 파싱

2. **편성 조건 (자동)**  
   - 조건1: 라인팀 수 = TP(팀장) 수 (지역별 TP 수만큼 팀 생성, 예: SEL 61명 + PUS 6명 → 67개 팀)  
   - 조건2: 팀당 TS 최소 1명  
   - 조건3: 팀당 11~15명 (TS, TP 포함)  
   - 조건4: 사번 다양하게 배정  
   - 조건5: 직급(SP/PS/AP/SS/ID/IS) 균등 배분  
   - 조건6: 남성 승무원 균등 배분  
   - 조건7: Rank(S/A/B/YY) 균등 배분  

3. **결과 조회 및 수정**  
   - 화면에서 팀별 편성 결과 확인  
   - **드래그 앤 드롭**으로 멤버를 다른 팀으로 수동 이동  
   - **엑셀 추출** 버튼으로 결과를 `line-teams.xlsx`로 다운로드  

---

## 엑셀 양식 (재직 현황)

첫 번째 시트, **첫 행은 헤더**로 사용합니다. 아래 컬럼명 중 하나와 일치하면 인식합니다.

**참조 파일**: `승무원 리스트 Test.xlsx` 구조 기준

| 의미 | 헤더(엑셀) | 설명 |
|------|------------|------|
| 사번 | 사번 | |
| 이름 | 이름 | |
| 성별 | 성별 | M/F |
| 근거지 | BASE | SEL, PUS 등 |
| 팀장/선임 구분 | Rank | TP, TS 등 (팀 수·TS 배정에 사용) |
| 라인 | Line | A411 등 |
| 직급 | 직급 | 객실3급 등 |
| 구분 | 구분 | 재직 등 |
| 방송자격 | 자격 | S/A/B/YY |

데이터는 2행부터 입력합니다.

- `sample/재직현황_샘플.csv` 에 예시 데이터가 있습니다. Excel에서 열어 **다른 이름으로 저장 → .xlsx** 로 저장한 뒤 업로드하면 됩니다.

---

## API 개요

| Method | Path | 설명 |
|--------|------|------|
| POST | /api/upload | 엑셀 파일 업로드 → 승무원 목록 반환 |
| POST | /api/assign | 승무원 목록으로 편성 → 라인팀 목록 반환 |
| PATCH | /api/teams/move | 멤버를 다른 팀으로 이동 |
| POST | /api/export | 편성 결과를 엑셀 파일로 반환 |

조건은 추후 확장 가능하도록 서비스/도메인 레이어에 구현되어 있습니다.
