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

[.github/workflows/release.yml](../.github/workflows/release.yml) 이 하는 일 (윈도우 러너에서 돕니다 — exe 를 만들어야 하므로):

- 모드를 빌드합니다 (서버 게임테스트도 돌립니다. 화면이 필요한 클라이언트 테스트는 건너뜁니다)
- 런처를 exe 로 묶습니다 (자바 런타임 포함)
- Fabric API jar 를 maven 에서 받아 sha256 을 잽니다
- `update.json` 을 만듭니다 (`./gradlew updateManifest`)
- 릴리스에 이것들을 올립니다:

| 파일 | 누구를 위한 것 |
| --- | --- |
| `OVERBREAK-Launcher-<버전>.exe` | 윈도우 — 받아서 설치하면 끝 (자바 필요 없음) |
| `overbreak-launcher-<버전>-windows.zip` | 윈도우 — 설치가 싫은 사람. 풀고 `OVERBREAK.exe` |
| `overbreak-launcher-<버전>.jar` | 맥 · 리눅스 (자바 21 이상) |
| `overbreak-<버전>.jar` | 손으로 `mods` 에 넣을 사람 |
| `update.json` | 런처가 보는 파일 |

설치용 exe 는 WiX 3 이 있어야 나옵니다. 러너에 없으면 그 단계만 조용히 건너뛰고 zip 으로 나갑니다.

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
| `Settings.java` | 사람이 직접 골라 준 마인크래프트 런처 경로를 기억 |

exe 로 묶는 것은 [launcher/build.gradle](../launcher/build.gradle) 아래쪽에 있습니다:

```bash
./gradlew :launcher:jar               # jar 만 (맥 · 리눅스용)
./gradlew :launcher:launcherZip       # OVERBREAK.exe + 자바 런타임 → zip
./gradlew :launcher:launcherInstaller # 설치용 exe 한 개 (WiX 3 필요)
```

`jlink` 로 쓰는 모듈만 담은 작은 자바 런타임(약 45MB)을 만들고, `jpackage` 로 `OVERBREAK.exe` 를 붙입니다.
그래서 받는 사람 컴퓨터에 자바가 없어도 그냥 켜집니다. 아이콘은 [launcher/packaging/overbreak.ico](../launcher/packaging/overbreak.ico).

**업데이트 확인 · 설치** 를 누르면:

1. `update.json` 을 읽습니다
2. `versions/fabric-loader-0.19.5-26.2/` 가 없으면 [meta.fabricmc.net](https://meta.fabricmc.net) 에서 받아 만듭니다
3. `launcher_profiles.json` 에 **OVERBREAK** 프로필을 넣습니다 — 게임 폴더는 `.minecraft/overbreak`
4. Fabric API 와 OVERBREAK 를 `overbreak/mods/` 에 맞춥니다. sha256 이 같으면 넘어가고, 같은 이름표의 옛 판은 지웁니다

**게임 실행** 은 공식 마인크래프트 런처를 띄웁니다. 이 차례로 찾습니다:

1. 지난번에 사람이 직접 골라 준 경로 (`~/.overbreak-launcher.properties`)
2. 흔한 설치 위치 — `Program Files (x86)\Minecraft Launcher\MinecraftLauncher.exe` 등
3. `minecraft:` 주소를 맡은 프로그램 (레지스트리 `HKCU`/`HKCR`)
4. 마이크로소프트 스토어판 — `explorer.exe shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft`

그래도 못 찾으면 실행 파일을 직접 고르게 하고, 그 경로를 기억해 둡니다.
로그인은 공식 런처가 하던 대로 하고, 프로필 목록에서 **OVERBREAK** 를 고르면 됩니다.

전용 게임 폴더를 쓰기 때문에 원래 세계 · 다른 모드 · 설정은 그대로 남습니다.

## 자주 걸리는 것

| 증상 | 까닭 |
| --- | --- |
| 런처가 "최신 정보를 가져오지 못했습니다" | 저장소가 비공개이거나 아직 릴리스가 없습니다 |
| 늘 "새 버전이 있습니다" | 태그와 `gradle.properties` 의 `version` 이 다릅니다 |
| "체크섬 불일치" | 릴리스 자산이 `update.json` 을 만든 뒤에 바뀌었습니다. 태그를 다시 미세요 |
| 마인크래프트 런처를 못 찾음 | 직접 켜고 **OVERBREAK** 프로필을 고르면 됩니다 |
