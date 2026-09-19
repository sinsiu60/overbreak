# OVERBREAK

마인크래프트 26.2 · Fabric 용 PVP 아레나 모드. 직업을 고르고 전장에 들어가 싸웁니다.

- **모드** — 직업 7 + 1 (워리어 · 해머나이트 · 파쇄권 · 발키리 · 보안관 · 셰이드 · 뇌신 · 투귀), 1대1 · 팀 격전 · 대난투 · 훈련장
- **런처** — 받는 사람이 모드를 직접 넣지 않아도 되게, 최신 판을 알아서 깔아 주는 작은 프로그램

## 게임 하기

[최신 릴리스](../../releases/latest)에서 `overbreak-launcher-<버전>.jar` 을 받아 두 번 누르면 됩니다.
런처가 Fabric 모드로더 0.19.5 · Fabric API · OVERBREAK 를 깔고, 공식 마인크래프트 런처에 **OVERBREAK** 프로필을 만들어 둡니다.
다음부터는 런처를 켜서 **업데이트 확인 · 설치** 만 누르면 최신으로 맞춰집니다 (옛 판은 자동으로 지워집니다).

원래 쓰던 세계 · 모드는 건드리지 않습니다 — OVERBREAK 는 `.minecraft/overbreak` 라는 전용 폴더에만 들어갑니다.
런처를 쓰지 않겠다면 릴리스의 `overbreak-<버전>.jar` 을 직접 `mods` 폴더에 넣어도 됩니다 (Fabric API 필요).

## 만들기

```bash
./gradlew build                 # 모드 빌드 + 게임테스트
./gradlew :launcher:jar         # 런처 jar
./gradlew runClient             # 클라이언트로 켜 보기
./gradlew runGameTest           # 서버 게임테스트만
```

빌드 결과는 `build/libs/overbreak-<버전>.jar`, 런처는 `launcher/build/libs/overbreak-launcher-<버전>.jar` 에 나옵니다.

## 내보내기

태그 하나면 릴리스가 만들어지고 런처가 그것을 봅니다. [docs/RELEASE.md](docs/RELEASE.md) 를 보세요.

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/RELEASE.md](docs/RELEASE.md) | 버전 올리기 · 릴리스 · 런처 |
| [docs/LOBBY.md](docs/LOBBY.md) | 로비 · 모드 선택 · 게임 흐름 |
| [docs/ANIMATION.md](docs/ANIMATION.md) | 플레이어 애니메이션 (Blockbench) |
| [docs/PORTING.md](docs/PORTING.md) | 데이터팩에서 옮겨 온 내역 |
| [docs/PHASE1.md](docs/PHASE1.md) | 초기 설계 메모 |
