# 플레이어 애니메이션 — Blockbench 로 고치기

모드는 Blockbench 에서 내보낸 **Bedrock 애니메이션 파일(.animation.json)** 을 읽어 3인칭 몸 동작과 1인칭 총 움직임에 씁니다.
지금은 발키리 동작이 전부 파일로 되어 있고, 다른 직업도 같은 이름의 애니메이션을 만들면 코드 동작 대신 파일 동작을 씁니다.

## 파일 위치

| 파일 | 용도 |
|---|---|
| `tools/blockbench/overbreak_player.geo.json` | Blockbench 에서 여는 **틀** (게임 플레이어와 같은 뼈대 · 축, 참고용 연사 포탑, 1인칭 총 뼈대) |
| `tools/blockbench/valkyrie.animation.json` | 지금 발키리 동작 원본 |
| `src/main/resources/assets/overbreak/player_animations/*.animation.json` | 모드에 들어가는 기본값 |
| `run/overbreak/animations/*.json` | **개발 클라이언트(runClient)에서 저장하면 1초 안에 바로 반영** (같은 이름이면 기본값보다 우선) |

실제 게임(.minecraft) 에서도 `.minecraft/overbreak/animations/` 폴더를 만들고 넣으면 똑같이 동작합니다 (내 화면에서만 보임).

## 순서

1. Blockbench → **File → Open Model** → `tools/blockbench/overbreak_player.geo.json`
2. 오른쪽 위 **Animate** 탭 → 애니메이션 목록 위 **Import Animations**(폴더 아이콘) → `run/overbreak/animations/valkyrie.animation.json`
3. 목록에서 동작을 골라 키프레임 수정 (타임라인 · 뼈대 선택 → rotation / position / scale)
4. 애니메이션 목록 위 **Save All Animations** (또는 동작 오른쪽 클릭 → Save) 로 **같은 파일에 저장**
5. `./gradlew runClient` 로 켜 둔 게임이면 채팅에 `[OVERBREAK] 애니메이션 다시 읽음` 이 뜨고 바로 바뀝니다.
   오류가 있으면 빨간 글씨로 어느 동작 · 뼈대 · 채널이 틀렸는지 알려 줍니다.
6. 마음에 들면 그 파일을 `src/main/resources/assets/overbreak/player_animations/` 에 복사해야 모드(jar)에 들어갑니다.

## 이름 규칙

파일 안 애니메이션 이름은 `animation.overbreak.<직업>.<동작>` 입니다. 파일 이름은 자유.

| 이름 | 언제 | 게임 길이 |
|---|---|---|
| `valkyrie.ready` | 연사 포탑을 든 동안 늘 (반복, 스킬 동작 밑에 깔림) | 반복 |
| `valkyrie.shot` | 연사 한 발 | 0.4초 |
| `valkyrie.rocket` | 전술 로켓 | 0.5초 |
| `valkyrie.float` | 차원 도약 (착지하면 끝) | 최대 10초 |
| `valkyrie.overheat` | 과열 분사 | 1.1초 |
| `valkyrie.barrage` | 탄막 포격 (기 모으기 0.7초 + 사격) | 4.7초 |
| `valkyrie.reload` | 재장전 (R 키 또는 탄창을 다 씀) — 탄창 빼기 · 새 탄창 · 끼움 · 장전 손잡이 | 1.5초 |
| `sheriff.shot` · `fan` (한 발마다) · `reload` · `roll` · `flash` · `deadeye` · `deadeye_fire` | 보안관 | 0.45 · 0.15 · 2 · 0.4 · 0.5 · 2 · 0.5초 |
| `shade.rend` · `evade` · `kunai` · `step` (그림자 걸음 날아가는 동안) · `strike` (순간이동해 벨 때마다) | 셰이드 | 0.4 · 0.6 · 0.4 · 0.2 · 0.25초 |
| `thunder.cast` (뇌격 · 강림 벼락마다) · `dash` · `field` · `smite` · `ult` | 뇌신 | 0.3 · 0.2 · 0.5 · 0.7 · 3.2초 |
| `ironfist.shot` · `charge` · `punch` · `block` · `slam_air` · `slam_hit` · `ult_rise` · `ult_drop` | 파쇄권 (지금은 코드 동작) | |
| `hammerknight.smash` · `slam` · `charge` · `ult` | 햄머나이트 (코드 동작) | |
| `warrior.slay` · `fury` · `chain` · `ult` · `basic` · `basic_back` | 전사 (코드 동작) | |
| `brute.basic` · `basic_back` (평타 두 방향) · `blow` · `whirl` · `regroup` · `ult` | 투귀 | 0.35 · 0.35 · 0.7 · 1 · 1 · 1.1초 |

게임 길이는 서버가 정합니다. 애니메이션이 더 짧으면 마지막 자세로 멈춰 있고, 끝나면 0.2초 동안 원래 자세로 돌아갑니다.
시작할 때도 0.1초 동안 섞여 들어갑니다 (`shot` · `rocket` 은 바로).

## 뼈대

| 뼈대 | 게임 |
|---|---|
| `head` | 머리. 키프레임이 없으면 시선을 그대로 따라감 |
| `body` | 몸통 (목이 축) |
| `right_arm` · `left_arm` | 팔. **몸통의 자식** — 몸통을 돌리면 팔도 따라 돔 (Blockbench 와 같음) |
| `right_leg` · `left_leg` | 다리. 몸통과 따로 — 몸통을 숙이면 엉덩이가 떨어져 보이니 다리 position z 를 같이 옮기세요 |
| `firstperson_item` | **1인칭** 손에 든 아이템. position(픽셀) · rotation(도) · scale 이 기본 손 자리에서 움직이는 양 |
| `firstperson_item_spin` | **1인칭** 리볼버를 방아쇠 손가락(오른손 끝)을 축으로 돌리는 rotation(도, x + = 총구가 위로). position(픽셀, -x = 손바닥 쪽)은 도는 자리를 옮김. 팔은 이 회전을 따라가지 않아 총만 돕니다 — 보안관 재장전 총 돌리기 · 피스키퍼 반동 |
| `firstperson_left_hand` | **1인칭** 받치는 손 (두 손 총). position(픽셀, **총 기준** — -z 총구 쪽, -y 아래) 이 총 몸통 밑 손잡이 자리에서 움직이는 양. 팔은 화면 왼쪽 아래에서 손을 향해 저절로 뻗습니다 |
| `firstperson_magazine` | **1인칭** 손에 든 탄창 기울기 rotation(도). 탄창은 손에 붙어 다닙니다 |
| `firstperson_left_item` | **1인칭** 왼손에 든 물건(스피드로더 · 섬광 수류탄) 기울기 rotation(도) — 보안관 |
| `firstperson_cylinder` | **1인칭** 리볼버 탄창: rotation x = 크레인 축으로 젖히는 각도, z = 탄창을 굴리는 각도 — 보안관 재장전 |
| `rifle_preview` | 참고용 총 모양. 게임에서 쓰지 않음 |

- 키프레임이 없는 뼈대 · 채널은 게임 기본 동작(걷기 · 시선 등) 그대로입니다.
- 걷는 중에는 다리 **회전** 키프레임을 무시하고 걷기 동작을 씁니다.
- 각도: 팔 x 음수 = 앞/위로 들기 (-90 = 정면), 오른팔 z 양수 · 왼팔 z 음수 = 옆으로 벌리기.
- 보간: Linear, Catmull-Rom (Smooth), 끊어지는 키프레임(Pre/Post 값 분리) 지원. Step 은 Blockbench 가 Pre/Post 로 저장합니다.

## 키프레임에 식 쓰기

숫자 칸에 식을 쓸 수 있습니다.

| 쓸 수 있는 것 | 뜻 |
|---|---|
| `q.head_x_rotation` | 머리 위아래 각도 (도, + = 아래를 봄). 총을 시선 쪽으로 겨눌 때: `-90 + q.head_x_rotation` |
| `q.head_y_rotation` | 머리 좌우 각도 (도, 몸통 기준) |
| `q.anim_time` | 동작 시작부터 지난 초. 떨림: `math.sin(q.anim_time * 1000) * 3` |
| `q.life_time` | 캐릭터가 존재한 초 (반복 동작용) |
| `math.sin` · `math.cos` (각도는 도) · `math.abs` · `math.sqrt` · `math.min` · `math.max` · `math.clamp(v, 최소, 최대)` · `math.lerp(a, b, t)` · `math.pi` | 함수 |
| `+ - * /` · 괄호 | 계산 |

Blockbench 미리보기에서는 `q.head_x_rotation` 등이 0 이라 정면을 보는 모습으로 나옵니다.
Animate 탭 왼쪽 아래 **Variable Placeholders** 에 `q.head_x_rotation = -30` 처럼 적으면 위를 볼 때 모습을 미리 볼 수 있습니다.

## 1인칭 총 (`firstperson_item`)

- position: 픽셀 (16 = 1블록). x + 오른쪽, y + 위, z + **뒤(카메라 쪽)** — 반동은 z 를 + 로
- rotation: 도. x + = 총구가 위로, y + = 왼쪽으로 돌림, z = 기울임
- scale: 크기 배율
- 모든 값 0 = 평소 손 자리. 이 뼈대가 없는 동작은 1인칭에서 총이 평소 자리에 가만히 있습니다.

## 재장전 탄창 (`timeline`)

Blockbench Animate 탭 타임라인 맨 위 **Effects → Instructions** 칸에 적는 문장으로 탄창이 총에서 빠지는 순간을 정합니다.

- `v.magazine_out = 1;` — 이 시각부터 총이 탄창 없는 모양이 되고, 탄창이 받치는 손에 붙습니다 (1인칭 · 3인칭 모두)
- `v.magazine_out = 0;` — 이 시각부터 탄창이 다시 총에 끼워진 모양

1인칭에서 손이 총에 끼운 탄창 밑면에 정확히 닿는 `firstperson_left_hand` 값은 **`0.46, -1.46, 1.57`** 입니다.
빼는 순간 · 끼우는 순간에 손을 이 자리에 두면 탄창이 튀지 않고 이어집니다.

재장전 소리는 서버가 같은 박자로 냅니다 (0.15초 걸쇠 · 0.3초 빠짐 · 0.65초 새 탄창 · 0.95초 끼움 · 1.2초 손잡이 당김 · 1.25초 놓음).
동작 시각을 크게 바꾸면 `Rifle.reloadSound` 의 틱도 맞춰 주세요.

## 보안관 왼손 · 탄창

- 보안관 `firstperson_left_hand` 는 발키리와 달리 **카메라 기준** 픽셀입니다: 0 = 화면 왼쪽 아래 밖에서 쉬는 자리, +x 오른쪽 · +y 위 · -z 앞. 이 뼈대가 있는 동작(난사 · 재장전 · 섬광 · 황야의 무법자) 중에만 왼손이 보입니다
- timeline: `v.cylinder_out` (탄창 젖혀짐) · `v.loader` (왼손에 스피드로더) · `v.flash_held` (왼손에 섬광 수류탄)
- 재장전 소리 박자는 `Peacekeeper.reloadSound` (틱) 와 맞춰 주세요

## 틀을 다시 만들 때

틀 · 발키리 파일은 처음에 스크립트로 만들었지만 이제 **Blockbench 에서 저장한 파일이 원본**입니다. 스크립트를 다시 돌리지 않습니다.
