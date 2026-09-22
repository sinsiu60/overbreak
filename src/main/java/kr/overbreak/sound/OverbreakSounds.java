package kr.overbreak.sound;

import kr.overbreak.Overbreak;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * 모드 소리 이벤트 — sounds.json 에서 전용 녹음(sounds/) 또는 바닐라 소리 파일로 이어 둡니다.
 * 나중에 전용 녹음으로 바꿀 때 코드는 손대지 않고 sounds.json 만 고치면 됩니다.
 */
public final class OverbreakSounds {
	// 궤적의 깃털 돌진 난사 — 전용 녹음 (sounds/skill/dash_scatter). 전부 클라이언트가 틉니다 (client/fx/ScatterSounds)
	/** 난사 한 발 — 4변형 중 무작위, 분신 총구 자리에서. */
	public static final Holder<SoundEvent> SCATTER_SHOT = register("skill.dash_scatter.shot");
	/** 적중 — 시전자 본인에게만 (2변형). */
	public static final Holder<SoundEvent> SCATTER_HIT = register("skill.dash_scatter.hit");
	/** 난사 종료 여운. */
	public static final Holder<SoundEvent> SCATTER_TAIL = register("skill.dash_scatter.tail");
	/** 기 모으기 — 장전 철컥. */
	public static final Holder<SoundEvent> SCATTER_COCK = register("skill.dash_scatter.cock");
	/** 돌진 — 휘익. */
	public static final Holder<SoundEvent> SCATTER_WHOOSH = register("skill.dash_scatter.whoosh");
	/** 탄피 떨어짐. */
	public static final Holder<SoundEvent> SCATTER_CASINGS = register("skill.dash_scatter.casings");
	// 참철 (스펙 PART 19) — 전부 클라이언트가 동작 시각에 맞춰 틉니다 (client/fx/IronFx). 지금은 바닐라 소리에 이어 둠 (sounds.json)
	/** 평타 선딜 시작. */
	public static final Holder<SoundEvent> IRON_SWING_WINDUP = register("ironcleaver.swing.windup");
	/** 평타 판정 — 바람 가름. */
	public static final Holder<SoundEvent> IRON_SWING_WHOOSH = register("ironcleaver.swing.whoosh");
	/** 평타 · 모아 베기 적중 — 시전자에게 (역경직과 같은 순간). */
	public static final Holder<SoundEvent> IRON_SWING_HIT = register("ironcleaver.swing.hit");
	/** 모으기 단계 도달 (1단 0.8 · 2단 1.0 · 3단 1.3). */
	public static final Holder<SoundEvent> IRON_CHARGE_STAGE = register("ironcleaver.charge.stage");
	/** 모으는 중 (반복 · 단계마다 피치 +0.15 · 끝나면 0.1초 페이드아웃). */
	public static final Holder<SoundEvent> IRON_CHARGE_LOOP = register("ironcleaver.charge.loop");
	/** 진 참 창 시작. */
	public static final Holder<SoundEvent> IRON_CHARGE_PERFECT = register("ironcleaver.charge.perfect");
	/** 모아 베기 발동 — 휘두름 + 폭음 두 겹. */
	public static final Holder<SoundEvent> IRON_CHARGE_RELEASE = register("ironcleaver.charge.release");
	public static final Holder<SoundEvent> IRON_CHARGE_RELEASE_BOOM = register("ironcleaver.charge.release.boom");
	/** 어깨 박치기 적중. */
	public static final Holder<SoundEvent> IRON_BASH_HIT = register("ironcleaver.bash.hit");
	/** 검막 막기 성공 — 방패 + 쇠 울림 두 겹. */
	public static final Holder<SoundEvent> IRON_GUARD_BLOCK = register("ironcleaver.guard.block");
	public static final Holder<SoundEvent> IRON_GUARD_BLOCK_CLANG = register("ironcleaver.guard.block.clang");
	/** 대지 가르기 — 땅 긁기 · 칼날 질주 (반복). */
	public static final Holder<SoundEvent> IRON_REND_SCRAPE = register("ironcleaver.rend.scrape");
	public static final Holder<SoundEvent> IRON_REND_WAVE = register("ironcleaver.rend.wave");
	/** 천참 모으기 · 베기 (폭음 + 천둥 두 겹). */
	public static final Holder<SoundEvent> IRON_ULT_CHARGE = register("ironcleaver.ult.charge");
	public static final Holder<SoundEvent> IRON_ULT_CLEAVE = register("ironcleaver.ult.cleave");
	public static final Holder<SoundEvent> IRON_ULT_CLEAVE_THUNDER = register("ironcleaver.ult.cleave.thunder");
	/** 경기 막판 배경 음악 (매치 포인트 · 대난투 막판) — Before the Impact, 23초부터. */
	public static final Holder<SoundEvent> MATCH_POINT = register("music.match_point");

	private OverbreakSounds() {}

	/** 클래스를 불러 정적 필드를 등록시키는 용도. */
	public static void init() {}

	private static Holder<SoundEvent> register(String path) {
		Identifier id = Overbreak.id(path);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
