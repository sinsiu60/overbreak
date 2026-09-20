package kr.overbreak.client.lobby;

import java.util.List;

/**
 * 패치노트 — 버전별로 바뀐 것. 새 버전은 맨 앞에 넣습니다.
 *
 * 각 줄의 꼬리표: {@link Tag#BUFF} (버프, 파랑) · {@link Tag#NERF} (너프, 빨강) · {@link Tag#TWEAK} (조정, 회색)
 */
final class PatchNotes {
	enum Tag {
		BUFF("버프", 0xFF5AB4FF),
		NERF("너프", 0xFFE8555B),
		TWEAK("조정", 0xFF9AA0AC);

		final String label;
		final int color;

		Tag(String label, int color) {
			this.label = label;
			this.color = color;
		}
	}

	record Line(String text, Tag tag) {}

	record Section(String title, int color, List<Line> lines) {}

	record Version(String version, String date, String headline, List<Section> sections) {}

	private PatchNotes() {}

	private static Line buff(String text) {
		return new Line(text, Tag.BUFF);
	}

	private static Line nerf(String text) {
		return new Line(text, Tag.NERF);
	}

	private static Line tweak(String text) {
		return new Line(text, Tag.TWEAK);
	}

	static final List<Version> VERSIONS = List.of(new Version("0.2a", "", "손맛과 시야", List.of(
			new Section("근접 공통", 0xFFFF7A4A, List.of(
					tweak("휘두를 때 화면이 베는 쪽으로 살짝 기울었다가 돌아옵니다 — 힘을 싣는 느낌"),
					tweak("큰 스킬(살육 · 분쇄 · 강타 · 로켓 펀치)은 더 크게 기웁니다"),
					tweak("기를 모으는 스킬(살육 · 분쇄 · 강타)은 누른 순간이 아니라 후려치는 순간에 흔들립니다"),
					tweak("조준점이 가리키는 곳은 그대로입니다 — 맞는 자리에는 영향이 없습니다"))),
			new Section("기절", 0xFFE8555B, List.of(
					nerf("기절당하면 그동안 화면을 돌릴 수 없습니다 — 굳어 있는 느낌이 제대로 납니다"))),
			new Section("워리어", 0xFFFF5A4A, List.of(
					buff("피의 사슬에 걸린 적은 사슬이 끝날 때까지 끌어당기는 워리어를 바라봅니다"),
					tweak("스킬로 흡혈할 때 화면 가장자리가 초록으로 번집니다 — 이제야 들어왔습니다"))),
			new Section("파쇄권", 0xFFFFC24A, List.of(
					nerf("파멸의 일격을 확정한 뒤 곧바로 떨어지지 않고 1초를 법니다 — 아래에서 피할 틈"))),
			new Section("투귀", 0xFFFFA24A, List.of(
					tweak("1인칭 평타가 손을 흔드는 모양에서 크게 베는 궤적으로 바뀌었습니다"),
					tweak("돌개바람 1인칭에서 칼이 마지막에 덜컹이던 것 수정 — 끝까지 같은 속도로 돕니다"),
					tweak("돌개바람 3인칭에서 몸통만 돌고 머리 · 다리가 남던 것 수정 — 몸 전체가 돕니다"),
					tweak("전열 재정비가 「숨 고르기」 에서 「강화 포션 들이켜기」 로 — 왼손의 병을 마시고 발밑에 내던져 깨뜨립니다"),
					tweak("마시는 1초 동안 화면 가장자리에 초록 회복 표시"))),
			new Section("보안관", 0xFFE8B04A, List.of(
					nerf("피스키퍼 넉백 제거"),
					nerf("이동속도 기본 → -8%"))))),
			new Version("0.2", "", "조작 정리", List.of(
			new Section("조작", 0xFF3FA2FF, List.of(
					tweak("액티브3 이 F 에서 E 로 옮겨 왔습니다 — 모든 규격에 함께 적용됩니다"),
					tweak("전장에서는 E 를 눌러도 인벤토리가 열리지 않습니다 (로비 · 관리자 자유 이동에서는 그대로 열립니다)"),
					tweak("인벤토리 키를 다른 것으로 바꿔 두었다면 그 키가 그대로 액티브3 이 됩니다"))),
			new Section("스킬 설명", 0xFF9AA0AC, List.of(
					tweak("기본 공격 표기를 「좌클릭」 에서 「LMB」 로 바꿔 RMB 와 나란히 맞췄습니다"),
					tweak("튜토리얼에 남아 있던 옛 수치를 지금 값으로 고쳤습니다 — 살육 80 · 사슬 15칸 · 포효 이동 30% / 공격력 15%"))))),
			new Version("0.1f", "", "규격 조정", List.of(
			new Section("워리어", 0xFFFF5A4A, List.of(
					nerf("광란의 포효 이동속도 +50% → +30%"),
					tweak("포효의 「공격력 +」 와 「스킬 피해 +」 를 공격력 증가 하나로 합쳤습니다 (평타 · 스킬 모두 적용)"),
					nerf("포효 공격력 증가 30% → 15%"),
					nerf("궁극기 충전 피해 7당 2% → 16당 2%"))),
			new Section("보안관", 0xFFE8B04A, List.of(
					nerf("피스키퍼 피해 80 → 70"),
					nerf("거리 감소가 10~20칸에 걸쳐 끝납니다 (이전 10~30칸)"))),
			new Section("셰이드", 0xFF9B6BFF, List.of(
					tweak("그림자 걸음 · 궁극기 뒤에 평타 → 우클릭을 이어 쓰면 우클릭 피해가 들어가지 않던 문제 수정"),
					buff("잔영 회피로 반격에 성공하면 그 쿨타임을 절반 돌려받습니다"))),
			new Section("투귀", 0xFFFFA24A, List.of(
					buff("전열 재정비로 숨을 고르는 1초 동안 받는 피해 40% 감소"))),
			new Section("배포", 0xFF3FA2FF, List.of(
					tweak("전용 런처가 생겼습니다 — 모드가 바뀌면 알아서 지우고 최신판을 받습니다"),
					tweak("런처가 Fabric 0.19.5 · Fabric API 설치까지 함께 해 줍니다"))))),
			new Version("0.1e", "", "투귀 동작 · 돌개바람", List.of(
			new Section("투귀", 0xFFFFA24A, List.of(
					tweak("1인칭 · 3인칭 동작이 들어왔습니다 — 평타(두 방향 대각선 베기) · 강타 · 돌개바람 · 전열 재정비 · 무쌍"),
					buff("돌개바람 이동속도 -30% → +30% — 휘돌며 파고들 수 있습니다"),
					tweak("본인 화면을 가리던 발밑 불씨 · 폭발 입자를 시전자에게서 뺐습니다"))))),
			new Version("0.1d", "", "신규 규격 「투귀」", List.of(
			new Section("투귀 (신규)", 0xFFFFA24A, List.of(
					tweak("붙어서 오래 싸울수록 강해지는 근접 브루저가 들어왔습니다"),
					tweak("체력 240 · 공격력 45 · 공격속도 1.2 · 이동속도 +3% · 평타 넉백 없음"),
					tweak("투기(패시브): 때리거나 맞을 때마다 1스택 (최대 10 · 5초) — 스택당 받는 피해 -2%, 이동속도 +1%"),
					tweak("강타(우클릭): 0.3초 뒤 앞 4칸에 70 + 투기당 4 · 0.6초 기절 · 맞히면 투기 3스택"),
					tweak("돌개바람(웅크리기): 1초간 주위 3.5칸을 0.2초마다 15 · 받는 피해 -30% · 저지불가"),
					tweak("전열 재정비(F): 1초 채널링으로 체력 60 회복 + 투기 5스택"),
					tweak("무쌍(궁극기): 6초간 투기 최대 고정 · 받는 피해 -40% · 이동속도 +25% · 평타 범위 2배 · 피해 +50% · 벨 때마다 15 회복"))),
			new Section("워리어", 0xFFFF5A4A, List.of(
					buff("기본 공격 36 → 55"),
					buff("살육 피해 50 → 80"),
					buff("피의 사슬 피해 20 → 25"))))),
			new Version("0.1c", "", "팀 격전 · 피격 감각", List.of(
			new Section("팀 격전", 0xFF3FA2FF, List.of(
					tweak("「3대3 팀전」 이 「팀 격전」 으로 바뀌고 2대2 매치가 생겼습니다"),
					tweak("편 짜기 화면 추가 — 청팀 · 홍팀을 플레이어가 직접 고릅니다"),
					tweak("15초 안에 고르지 않으면 빈자리에 자동으로 들어갑니다 · 양쪽이 다 차면 바로 시작"))),
			new Section("전투 감각", 0xFFE8363C, List.of(
					buff("넉백 없는 공격에 연달아 맞으면 이동속도가 뚝뚝 끊기던 문제 수정"),
					tweak("피격 시 화면이 덜컹거리던 것을 방향 표시로 교체 — 맞은 쪽에 붉은 호가 뜹니다"),
					tweak("같은 방향에서 연사에 맞아도 표시가 하나로 뭉쳐 조준이 흔들리지 않습니다"),
					tweak("경기에 들어갈 때도 튜토리얼처럼 가상세계 부팅 연출이 돕니다"))),
			new Section("대난투", 0xFFFFC94A, List.of(
					tweak("승리 조건이 사람 수에 맞춰집니다 — 1명당 10킬 (3명 30킬 · 5명 50킬)"),
					tweak("시작 대기 5초 → 10초"),
					buff("쓰러져 있는 동안 규격을 바꿀 수 있습니다"),
					buff("경기 중에도 자리가 남아 있으면 난입할 수 있습니다"),
					nerf("다시 일어나기까지 1.5초 → 3초"))),
			new Section("워리어", 0xFFFF5A4A, List.of(
					buff("피의 사슬 준비 시간 0.7초 → 0.4초"),
					buff("피의 사슬 사거리 7칸 → 15칸"),
					buff("피의 사슬 끌어오는 속도 2배 (초당 11.7칸 → 23.4칸)"),
					buff("살육 범위 3.5칸 → 4.5칸"))),
			new Section("파쇄권", 0xFF5AB4FF, List.of(
					nerf("체력 250 → 200"))),
			new Section("보안관", 0xFFE8B04A, List.of(
					buff("피스키퍼 사거리 24칸 → 60칸"),
					nerf("거리 감소가 10~30칸에 걸쳐 최대 -70% (이전 10~24칸 -40%)"),
					buff("전술 구르기 재사용 대기시간 8초 → 6초"))),
			new Section("뇌신", 0xFF5AD8FF, List.of(
					nerf("궁극기 충전 피해 10당 2% → 14당 2%"))))),
			new Version("0.1b", "", "3대3 팀전", List.of(
			new Section("3대3 팀전", 0xFF3FA2FF, List.of(
					tweak("새 게임 종류 — 세 명씩 두 편으로 나뉘어 같은 전장에서 겨룹니다"),
					tweak("한 팀을 전멸시키면 1점, 먼저 3점을 내면 승리 (제거전)"),
					tweak("쓰러지면 그 라운드 동안 관전, 다음 라운드에 모두 다시 살아납니다"),
					tweak("여섯 명이 대기열에 모이면 바로 시작합니다"))),
			new Section("팀 표시", 0xFFE8363C, List.of(
					tweak("우리 편은 파란 테두리, 상대는 빨간 테두리로 보입니다"),
					tweak("우리 편 테두리는 벽 너머로도 보이고, 상대 테두리는 눈에 보일 때만 뜹니다"),
					tweak("점수판이 [ 우리 팀 ] 파랑 : 빨강 [ 상대 팀 ] 으로 바뀌고 라운드 · 생존 수가 함께 뜹니다"))),
			new Section("아군 피해 없음", 0xFFFFC23A, List.of(
					tweak("같은 편은 피해 · 군중 제어를 전혀 받지 않습니다"),
					tweak("총알 · 표창 · 사슬이 아군을 막지 않고 그냥 지나갑니다"),
					tweak("범위 스킬도 아군을 아예 대상에서 뺍니다"))))),
			new Version("0.1a", "", "대난투 · 규격 재조정", List.of(
			new Section("공통", 0xFFE8363C, List.of(
					tweak("새 게임 종류 「대난투」 — 최대 5명이 한 전장에서, 먼저 50킬을 하면 승리"),
					tweak("경기 점수가 액션바 대신 화면 위쪽에 표시됩니다"),
					tweak("승부가 갈리는 순간 서버가 꺼지던 문제 수정"),
					tweak("밀착 사격이 늘 머리에 맞은 것으로 잡히던 문제 수정 (히트스캔 전 규격)"),
					tweak("규격 선택 화면에 초상화와 픽 연출 추가"),
					tweak("훈련용 더미가 주민에서 마네킹으로 바뀌었습니다"),
					tweak("관리자 모드 추가 — 게임마스터는 전장 · 훈련장을 나가 맵을 돌아다닐 수 있습니다"))),
			new Section("워리어", 0xFFFF5A4A, List.of(
					buff("이동속도 -7% → +7%"),
					buff("피의 갈망 폭발 회복 40 → 50"),
					buff("살육 적중 1명당 회복 20 → 35"),
					tweak("광란의 포효: 처음 맞은 적만 끝까지 둔화되던 것을 고쳐, 지속시간 동안 주위 3칸의 적을 계속 둔화"),
					buff("피의 사슬 끌어오는 속도 +30% (초당 9칸 → 11.7칸)"))),
			new Section("햄머나이트", 0xFFFFC23A, List.of(
					buff("이동속도 -10% → +7%"),
					nerf("지면 분쇄 범위 5.5칸 → 4칸"),
					nerf("돌진 충격 기 모으기 0.2초 → 0.3초"))),
			new Section("파쇄권", 0xFF5AB4FF, List.of(
					nerf("체력 275 → 250"),
					tweak("파워 펀치 사거리 기본 대비 +50% → +30% (날아가는 시간은 그대로 = 더 빠르게)"),
					tweak("궁극기 파멸의 일격을 원작 방식으로 교체 — 이동 키로 착탄 원을 끌고 다닙니다"),
					tweak("파멸의 일격 반경 6칸 · 중심 150 에서 가장자리 15 로 줄어드는 피해 (기절 삭제)"),
					nerf("궁극기 충전 피해 7당 2% → 15당 2%"))),
			new Section("발키리", 0xFFFFD24A, List.of(
					nerf("체력 220 → 200"),
					nerf("연사 피해 발당 8 → 7"),
					nerf("연사 머리 치명타 2배 → 1.5배 (약한 치명타)"),
					nerf("차원 도약 이동거리 -30%"),
					nerf("차원 도약 느린 낙하 5초 → 3초"),
					nerf("차원 도약 공격속도 1.5배 → +25%"),
					tweak("공격속도가 올라간 동안 화면 가장자리가 금색으로 빛납니다"),
					nerf("궁극기 충전 피해 10당 2% → 12당 2%"))),
			new Section("보안관", 0xFFE8B04A, List.of(
					tweak("헤드샷 판정 수정 — 밀착 사격이 늘 머리로 잡히던 문제"),
					nerf("피해 감소 시작 15칸 → 10칸"),
					nerf("궁극기 충전 피해 8당 2% → 20당 2%"))),
			new Section("셰이드", 0xFF9B6BFF, List.of(
					buff("이동속도 +8% → +15%"),
					buff("그림자 표창 피격 범위 +30%"),
					buff("그림자 표창 재사용 대기시간 12초 → 8초"))),
			new Section("뇌신", 0xFF5AD8FF, List.of(
					buff("체력 190 → 225"))))),
			new Version("0.1", "", "규격 전면 조정", List.of(
			new Section("공통", 0xFFE8363C, List.of(
					nerf("워리어 체력 300 → 275"),
					nerf("햄머나이트 체력 350 → 300"),
					tweak("워리어 · 햄머나이트의 평타 넉백 삭제"),
					buff("워리어 평타 뒤 무적 시간 삭제 — 평타에서 스킬로 바로 이어집니다"),
					tweak("「그림자 검객」 이름을 「셰이드」로 변경"),
					tweak("F8 규격 정보: 이동기 봉인 안내 대신 모든 스킬에 「분류」 표시"))),
			new Section("워리어", 0xFFFF5A4A, List.of(
					buff("광란의 포효 이동속도 +30% → +50%"),
					tweak("광란의 포효 주위 둔화 50% → 40%, 대신 지속시간 내내 유지"),
					buff("피의 사슬: 끌려오는 동안 기절 유지, 도착하면 2초간 50% 둔화"),
					buff("궁극기 충전 피해 10당 2% → 7당 2%"))),
			new Section("햄머나이트", 0xFFFFC23A, List.of(
					buff("지면 분쇄 사거리 3칸 → 5.5칸"),
					buff("돌진 충격 기 모으기 0.3초 → 0.2초"),
					buff("돌진 충격 거리 4.4칸 → 7.6칸 (0.4초 → 0.55초)"),
					buff("중력 파쇄 피해 20 → 30"),
					buff("중력 파쇄 끌어오는 범위 4칸 → 6칸"),
					buff("균열 지대 범위 3칸 → 5칸 · 지속 4초 → 5초 · 상시 10% 둔화"))),
			new Section("파쇄권", 0xFF5AB4FF, List.of(
					buff("체력 150 → 275"),
					buff("로켓 펀치 충전 1.2초 → 0.9초"),
					buff("로켓 펀치가 30% 빠르게 날아감 (거리는 그대로)"),
					buff("파워 블록 정면 피해 70% 감소 → 전부 막음"),
					buff("파워 블록으로 강화에 성공하면 로켓 펀치 쿨타임 초기화"),
					buff("지진 강타 피해 15~26.2 → 40~60"),
					buff("궁극기 충전 피해 10당 1.8% → 7당 2%"))),
			new Section("발키리", 0xFFFFD24A, List.of(
					buff("연사 사거리 12칸 → 20칸"),
					buff("연사 속도 초당 5발 → 8발 (차원 도약 중 1.5배는 그대로)"),
					tweak("연사 피해 9 → 8"),
					nerf("전술 로켓 에어본 삭제 · 1초 30% 둔화로 변경"))),
			new Section("보안관", 0xFFE8B04A, List.of(
					buff("피스키퍼 사거리 16칸 → 24칸"),
					buff("피스키퍼 피해 80 (15칸부터 줄어 24칸에서 -40%)"),
					buff("리볼버 난사 피해 발당 15 → 30"),
					tweak("리볼버 난사 쿨타임 삭제 — 남은 탄창만큼 나갑니다"),
					tweak("전술 구르기: 난사 쿨타임 초기화 → 탄창 전부 회복"),
					buff("섬광 수류탄 범위 2.5칸 → 3.5칸"),
					buff("섬광 수류탄 둔화 50% · 1.2초 → 80% · 1.6초"))),
			new Section("셰이드", 0xFF9B6BFF, List.of(
					buff("그림자 낙인 추가 피해 25 → 30"),
					buff("그림자 표창 피해 20 → 30 · 머리에 맞으면 2배"),
					buff("잔영 회피 지속 0.6초 → 0.7초"),
					buff("그림자 가르기 거리 6칸 → 7.5칸"),
					buff("그림자 가르기 반경 1.5칸 → 1.75칸"))),
			new Section("뇌신", 0xFF5AD8FF, List.of(
					buff("뇌격 피해 18 → 20"),
					nerf("섬전에 0.2초 기 모으기 추가"),
					buff("뇌운 사거리 16칸 → 20칸"),
					buff("뇌운 범위 3.5칸 → 4.5칸"),
					nerf("뇌운 둔화 30% → 15%"),
					nerf("낙뢰 예고 0.6초 → 0.7초"),
					nerf("낙뢰 띄우기 0.6초 → 0.4초"))))));
}
