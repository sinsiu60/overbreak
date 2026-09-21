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
