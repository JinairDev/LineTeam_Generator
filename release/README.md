# 비개발자 전달용 패키지

이 폴더는 **한 번 빌드한 뒤** 그대로 ZIP으로 압축해 비개발자에게 전달할 때 쓰는 폴더입니다.

## 폴더에 들어가는 것

| 파일 | 설명 |
|------|------|
| **LineTeam_Generator.jar** | 프론트+백엔드가 합쳐진 실행 파일 하나 (build-release 실행 후 생성됨) |
| **start.bat** | Windows: 더블클릭하면 프로그램 실행 → localhost:8080 자동 오픈 |
| **start.sh** | Mac: 더블클릭 또는 터미널에서 `./start.sh` → 동일 |
| **사용방법.txt** | 받는 사람용 한글 안내 |

## 개발자: 패키지 만드는 방법

프로젝트 루트에서 **한 번만** 실행:

- **Windows**: `build-release.bat`
- **Mac/Linux**: `./build-release.sh`

→ `release` 폴더에 JAR가 채워지고, 이 폴더 전체를 ZIP으로 압축해 전달하면 됩니다.

## 수신자: 사용 방법

1. ZIP 압축 해제
2. **Windows**: `start.bat` 더블클릭  
   **Mac**: `start.sh` 더블클릭 (또는 터미널에서 `./start.sh`)
3. 브라우저가 자동으로 열리면 http://localhost:8080 에서 사용

**필요:** Java 21 이상 설치 ([Adoptium](https://adoptium.net/) 등)

---

## Windows EXE로 배포 (Java 설치 불필요)

수신자 PC에 Java를 설치하지 않게 하려면, Windows에서 **실행 파일(.exe)** 로 만들어 배포할 수 있습니다.

- **만드는 방법**: 프로젝트 루트에서 `build-exe.bat` 실행 → `dist` 폴더에 EXE 생성
- **받는 방법**: GitHub **Actions** 탭 → Build Windows EXE 워크플로 실행 → Artifacts에서 다운로드

자세한 내용은 프로젝트 루트의 **README.md** → "단일 JAR / EXE로 실행하기" 를 참고하세요.
