package kr.overbreak.classes.thunder;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** 뇌신 한 명의 진행 상태. */
public final class ThunderState {
	/** 내가 쌓은 정전기: 대상 → {스택, 남은 틱}. */
	final Map<LivingEntity, int[]> charges = new IdentityHashMap<>();
	/** 감전 기절을 다시 못 받는 대상 → 남은 틱. */
	final Map<LivingEntity, Integer> stunImmune = new IdentityHashMap<>();
	/** 뇌격 사격 간격 남은 틱. */
	int shotCd;
	/** 진행 중인 섬전 (없으면 null) — 이 동안 피해를 받지 않음. */
	@Nullable LightningStep step;
	/** 진행 중인 뇌신강림 (없으면 null). */
	@Nullable Descent descent;
	/** 강림이 끝난 뒤 떨어지는 동안 낙하 피해를 없애는 틱. */
	int fallSafeT;

	/** 시험용. */
	public int charge(LivingEntity e) {
		int[] c = charges.get(e);
		return c == null ? 0 : c[0];
	}

	public boolean stepping() {
		return step != null;
	}

	public boolean descending() {
		return descent != null;
	}
}
