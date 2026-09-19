package kr.overbreak.classes.shade;

import kr.overbreak.core.tick.Ticks;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** 셰이드 한 명의 진행 상태. */
public final class ShadeState {
	/** 내가 찍은 그림자 낙인: 대상 → 남은 틱. */
	final Map<LivingEntity, Integer> marks = new IdentityHashMap<>();
	/** 진행 중인 그림자 가르기 (없으면 null). */
	@Nullable ShadowRend rend;
	/** 잔영 회피 남은 틱 (이 동안 받는 피해 무효 · 첫 공격자에게 반격). */
	int evadeT;
	/** 반격 · 궁극기 직후 잠깐 피해를 받지 않는 틱. */
	int graceT;
	/** 그림자 표창이 꽂힌 대상 — stepT 동안 F 를 다시 누르면 등 뒤로 이동. */
	@Nullable LivingEntity stepTarget;
	int stepT;
	/** 날아가고 있는 그림자 걸음 (없으면 null). */
	@Nullable ShadowStep step;
	/** 진행 중인 천검난무 (없으면 null). */
	@Nullable BladeStorm storm;

	/** 시험용: 이 대상에 내 낙인이 있는가. */
	public boolean marked(LivingEntity e) {
		return marks.containsKey(e);
	}

	/** 남은 회피 시간 (1/20초 단위). */
	public int evadeTicks() {
		return kr.overbreak.core.tick.Ticks.toTime(evadeT);
	}

	public boolean stepping() {
		return step != null;
	}

	/** 그림자 걸음 대기 남은 시간 (1/20초 단위). */
	public int stepTicks() {
		return kr.overbreak.core.tick.Ticks.toTime(stepT);
	}

	public boolean storming() {
		return storm != null;
	}
}
