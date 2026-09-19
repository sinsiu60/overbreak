# 내보내기 — 릴리스와 런처

모드를 고칠 때마다 사람들에게 jar 를 넘기지 않아도 되게 만든 길입니다.

```
태그 밀기  →  GitHub Actions 가 빌드  →  릴리스에 jar + update.json 올림  →  런처가 알아서 받음
```

## 한 번만 해 두는 일

1. **GitHub 저장소를 만들고 코드를 올립니다.**

   ```bash
   cd overbreak-fabric
   git init
   git add .
   git commit -m "OVERBREAK"
   git branch -M main
   git remote add origin https://github.com/<내계정>/<내저장소>.git
   git push -u origin main
   ```

2. **런처가 볼 저장소를 맞춥니다.**
   릴리스 워크플로는 `-Prepo=<owner>/<repo>` 를 알아서 넘기므로 CI 로 만든 런처는 이미 제 저장소를 봅니다.
   손으로 빌드한 런처까지 맞추려면 [launcher/src/main/java/kr/overbreak/launcher/Config.java](../launcher/src/main/java/kr/overbreak/launcher/Config.java) 의
   `DEFAULT_REPO` 를 바꾸거나, 런처 jar 옆에 이런 `launcher.properties` 를 두면 됩니다:

   ```properties
   repo=내계정/내저장소
   # 저장소 대신 주소를 통째로 지정할 수도 있습니다
   # manifest=https://example.com/update.json
   ```

## 새 판 내보내기

1. `gradle.properties` 의 `version` 을 올립니다 (예: `0.1.6` → `0.1.7`).
2. 패치 노트를 씁니다 — [client/lobby/PatchNotes.java](../src/client/java/kr/overbreak/client/lobby/PatchNotes.java) 맨 위에 새 항목.
3. 커밋하고, **버전과 똑같은** 태그를 밉니다.

   ```bash
   git commit -am "0.1.7"
   git tag v0.1.7
   git push origin main --tags
   ```

태그 이름(`v0.1.7`)과 `gradle.properties` 의 `version`(`0.1.7`)이 다르면 워크플로가 일부러 실패합니다.
다르면 런처가 영영 "새 버전이 있습니다" 를 띄우게 되기 때문입니다.

[.github/workflows/release.yml](../.github/workflows/release.yml) 이 하는 일:

- 모드와 런처를 빌드합니다 (서버 게임테스트도 돌립니다. 화면이 필요한 클라이언트 테스트는 건너뜁니다)
- Fabric API jar 를 maven 에서 받아 sha256 을 잽니다
- `update.json` 을 만듭니다 (`./gradlew updateManifest`)
- 릴리스에 `overbreak-<버전>.jar` · `overbreak-launcher-<버전>.jar` · `update.json` 을 올립니다

## update.json

런처가 보는 유일한 파일입니다. 늘 최신 릴리스를 가리키는 주소가 있어서 태그를 몰라도 됩니다:

```
https://github.com/<owner>/<repo>/releases/latest/download/update.json
```

```json
{
  "version": "0.1.6",
  "minecraft": "26.2",
  "fabricLoader": "0.19.5",
  "mod": {
    "file": "overbreak-0.1.6.jar",
    "url": "https://github.com/<owner>/<repo>/releases/download/v0.1.6/overbreak-0.1.6.jar",
    "sha256": "…"
  },
  "fabricApi": {
    "file": "fabric-api-0.160.0+26.2.jar",
    "url": "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.160.0%2B26.2/fabric-api-0.160.0%2B26.2.jar",
    "sha256": "…"
  }
}
```

- `file` 은 `mods` 폴더에 저장할 이름, `url` 은 받아 올 곳입니다 — 둘이 달라도 됩니다.
- `sha256` 이 비어 있으면 검사하지 않고, 파일이 이미 있으면 다시 받지 않습니다.
- Fabric API 는 릴리스에 올리지 않고 Fabric 공식 maven 에서 바로 받습니다.

손으로 만들어 보려면:

```bash
./gradlew updateManifest -PreleaseTag=v0.1.6 -Prepo=내계정/내저장소
cat build/update.json
```

## 런처가 하는 일

[launcher/](../launcher) — JDK 만으로 도는 작은 Swing 프로그램입니다 (바깥 라이브러리 없음).

| 파일 | 하는 일 |
| --- | --- |
| `Main.java` | 창 · 단추 세 개 (업데이트 확인 · 설치 / 게임 실행 / 모드 폴더) |
| `Installer.java` | .minecraft 찾기, Fabric 설치, 프로필 쓰기, 모드 맞추기 |
| `Net.java` | 내려받기 · 진행률 · sha256 |
| `Json.java` | 작은 JSON 읽기 · 쓰기 |
| `Config.java` | 어느 저장소를 볼지 |

**업데이트 확인 · 설치** 를 누르면:

1. `update.json` 을 읽습니다
2. `versions/fabric-loader-0.19.5-26.2/` 가 없으면 [meta.fabricmc.net](https://meta.fabricmc.net) 에서 받아 만듭니다
3. `launcher_profiles.json` 에 **OVERBREAK** 프로필을 넣습니다 — 게임 폴더는 `.minecraft/overbreak`
4. Fabric API 와 OVERBREAK 를 `overbreak/mods/` 에 맞춥니다. sha256 이 같으면 넘어가고, 같은 이름표의 옛 판은 지웁니다

**게임 실행** 은 공식 마인크래프트 런처를 띄웁니다. 로그인은 공식 런처가 하던 대로 하고,
프로필 목록에서 **OVERBREAK** 를 고르면 됩니다.

전용 게임 폴더를 쓰기 때문에 원래 세계 · 다른 모드 · 설정은 그대로 남습니다.

## 자주 걸리는 것

| 증상 | 까닭 |
| --- | --- |
| 런처가 "최신 정보를 가져오지 못했습니다" | 저장소가 비공개이거나 아직 릴리스가 없습니다 |
| 늘 "새 버전이 있습니다" | 태그와 `gradle.properties` 의 `version` 이 다릅니다 |
| "체크섬 불일치" | 릴리스 자산이 `update.json` 을 만든 뒤에 바뀌었습니다. 태그를 다시 미세요 |
| 마인크래프트 런처를 못 찾음 | 직접 켜고 **OVERBREAK** 프로필을 고르면 됩니다 |
