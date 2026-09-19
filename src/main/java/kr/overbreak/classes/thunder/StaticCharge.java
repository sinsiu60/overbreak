package kr.overbreak.classes.thunder;

import kr.overbreak.core.tick.Ticks;
import java.util.Iterator;
import java.util.Map;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Tracer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * [패시브] 정전기.
 *
 *   내 번개(뇌격 · 섬전 · 뇌운 · 낙뢰 · 뇌신강림)에 맞을 때마다 정전기 1스택 (최대 3 · 마지막으로 쌓인 뒤 4초 유지)
 *   3스택이 되는 순간 감전: 하늘에서 번개가 내리꽂혀 25 추가 피해 (넉백 없음) + 0.5초 기절 → 스택 초기화
 *   같은 적은 감전 기절 뒤 5초 동안 다시 기절하지 않음 (피해는 들어감)
 */
final class StaticCharge {
	static final int MAX = 3;
	static final int DURATION = 80;
	static final int DISCHARGE_10 = 250;
	static final int STUN = 10;
	static final int STUN_IMMUNE = 100;
	static final int CYAN = Fx.rgb(0.35, 0.85, 1.00);

	private StaticCharge() {}

	/** 스택 하나. @return 감전했으면 true */
	static boolean add(ServerPlayer p, ThunderState st, LivingEntity e) {
		if (!e.isAlive()) {
			return false;
		}
		int[] c = st.charges.computeIfAbsent(e, k -> new int[2]);
		c[0]++;
		c[1] = Ticks.of(DURATION);
		ServerLevel level = p.level();
		if (c[0] < MAX) {
			Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 6 * c[0], 0.2, 0.1, 0.2, 0.05);
			return false;
		}
		st.charges.remove(e);
		SkillDamage.deal(e, p, DISCHARGE_10, SkillDamage.Kind.MULTI_NO_KB);
		if (e.isAlive() && !st.stunImmune.containsKey(e)) {
			CrowdControl.stun(e, STUN);
			st.stunImmune.put(e, Ticks.of(STUN_IMMUNE));
		}
		Vec3 top = e.position().add(0.0, e.getBbHeight() + 3.5, 0.0);
		Tracer.spawn(level, p, top, e.position().add(0.0, e.getBbHeight() * 0.5, 0.0), Tracer.LIGHTNING);
		Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1.0, e.getZ(), 40, 0.4, 0.6, 0.4, 0.4);
		Fx.particle(level, Fx.dust(CYAN, 1.4F), e.getX(), e.getY() + 1.0, e.getZ(), 20, 0.35, 0.6, 0.35, 0);
		Fx.sound(e, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.5F, 1.8F);
		Fx.sound(e, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 2.0F);
		return true;
	}

	static void tick(ThunderState st) {
		Iterator<Map.Entry<LivingEntity, int[]>> it = st.charges.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, int[]> m = it.next();
			LivingEntity e = m.getKey();
			int[] c = m.getValue();
			if (--c[1] <= 0 || !e.isAlive() || e.isRemoved()) {
				it.remove();
				continue;
			}
			if (Ticks.every(c[1], 5) && e.level() instanceof ServerLevel level) {
				Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), c[0] * 2, 0.2, 0.1, 0.2, 0.02);
			}
		}
		st.stunImmune.replaceAll((e, t) -> t - 1);
		st.stunImmune.values().removeIf(t -> t <= 0);
	}
}
