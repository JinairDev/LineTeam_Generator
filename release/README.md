# Windows 실행 파일 (EXE)

## 받는 방법

### 1) GitHub Actions에서 빌드 후 다운로드 (권장)

1. 저장소 **Actions** 탭 이동: [LineTeam_Generator Actions](https://github.com/davidpiao/LineTeam_Generator/actions)
2. 왼쪽에서 **"Build Windows EXE"** 워크플로 선택
3. **"Run workflow"** → **"Run workflow"** 클릭
4. 빌드가 끝나면(약 5~10분) 해당 실행(run) 클릭
5. 아래 **Artifacts** 에서 **LineTeam_Generator-Windows** 다운로드
6. ZIP 압축을 풀면 **.exe** 파일이 있습니다. 실행하면 localhost:8080 에서 서비스됩니다.

### 2) 태그를 붙여 푸시하면 자동으로 Release 생성

```bash
git tag v1.0.0
git push origin v1.0.0
```

푸시 후 **Releases** 탭에 `v1.0.0` 이 생성되고, 여기서 Windows 실행 파일을 받을 수 있습니다.

---

**직접 빌드**하려면 Windows PC에서:

```bat
build-package.bat
build-exe.bat
```

실행 파일은 `dist` 폴더에 생성됩니다.
