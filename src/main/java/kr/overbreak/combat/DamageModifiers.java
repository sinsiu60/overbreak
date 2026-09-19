package kr.overbreak.combat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 받는 피해 수정 — 데이터팩 플러그인 EntityDamageByEntityEvent#setDamage 대응 (파워 블록 정면 감소 등).
 *
 * LivingEntity#hurtServer 맨 앞에서 불립니다 (mixin/LivingEntityDamageMixin). 기본 피해를 바꾸는 것이라
 * 뒤쪽의 무적 시간 · 흡수 체력 계산은 바뀐 값 위에서 그대로 이어집니다.
 */
public final class DamageModifiers {
	public interface Modifier {
		float modify(LivingEntity target, DamageSource source, float damage);
	}

	private static final List<Modifier> LIST = new ArrayList<>();

	private DamageModifiers() {}

	public static void register(Modifier m) {
		LIST.add(m);
	}

	public static float apply(LivingEntity target, DamageSource source, float damage) {
		for (Modifier m : LIST) {
			damage = m.modify(target, source, damage);
		}
		return damage;
	}
}
