package kr.overbreak.sound;

import kr.overbreak.Overbreak;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * 모드 소리 이벤트 — 이름만 모드 것이고, 처음에는 assets/overbreak/sounds.json 에서 바닐라 소리 파일로 이어 둡니다.
 * 나중에 전용 녹음으로 바꿀 때 코드는 손대지 않고 sounds.json 만 고치면 됩니다.
 */
public final class OverbreakSounds {
	/** 궤적의 깃털 돌진 난사 — 공이치기 (기 모으기). */
	public static final Holder<SoundEvent> SCATTER_WINDUP = register("skill.dash_scatter.windup");
	/** 돌진 — 바람 가르는 소리. */
	public static final Holder<SoundEvent> SCATTER_DASH = register("skill.dash_scatter.dash");
	/** 제동 — 미끄러지는 소리. */
	public static final Holder<SoundEvent> SCATTER_BRAKE = register("skill.dash_scatter.brake");
	/** 난사 한 발 — 권총 발사음 (클라이언트가 동시 재생 수를 잘라 틉니다). */
	public static final Holder<SoundEvent> SCATTER_SHOT = register("skill.dash_scatter.shot");
	/** 마무리 — 총 돌리기 금속음. */
	public static final Holder<SoundEvent> SCATTER_SPIN = register("skill.dash_scatter.spin");

	private OverbreakSounds() {}

	/** 클래스를 불러 정적 필드를 등록시키는 용도. */
	public static void init() {}

	private static Holder<SoundEvent> register(String path) {
		Identifier id = Overbreak.id(path);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
