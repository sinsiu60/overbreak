package kr.overbreak.classes.shade;

import kr.overbreak.core.tick.Ticks;
import java.util.Iterator;
import java.util.Map;

import kr.overbreak.combat.SkillDamage;
import kr.overbreak.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * [패시브] 그림자 낙인.
 *
 *   그림자 가르기 · 그림자 표창 · 잔영 반격 · 천검난무에 맞은 적에게 4초 동안 낙인
 *   낙인이 찍힌 적을 평타로 베면 낙인이 터져 25 추가 피해 (평타와 같은 순간이라 무적 시간 무시 · 넉백 없음)
 *   낙인은 적 한 명에 하나 (다시 찍으면 시간만 새로)
 */
final class ShadowMark {
	static final int DURATION = 80;
	static final int BURST_10 = 300;
	static final int PURPLE = Fx.rgb(0.62, 0.35, 1.00);

	private ShadowMark() {}

	static void apply(ShadeState st, LivingEntity e) {
		if (!e.isAlive()) {
			return;
		}
		st.marks.put(e, Ticks.of(DURATION));
		Fx.particle((ServerLevel) e.level(), Fx.dust(PURPLE, 1.4F), e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 8, 0.15, 0.1, 0.15, 0);
	}

	/** 평타 적중 순간 — 낙인이 있으면 터뜨립니다. @return 터졌으면 true */
	static boolean burst(ServerPlayer p, ShadeState st, LivingEntity target) {
		if (st.marks.remove(target) == null) {
			return false;
		}
		SkillDamage.deal(target, p, BURST_10, SkillDamage.Kind.MULTI_NO_KB);
		ServerLevel level = p.level();
		Fx.particle(level, Fx.dust(PURPLE, 1.2F), target.getX(), target.getY() + 1.0, target.getZ(), 24, 0.35, 0.5, 0.35, 0);
		Fx.particle(level, ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0, 0, 0, 0);
		Fx.sound(target, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);
		Fx.sound(target, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.3F);
		return true;
	}

	/** 매 틱 — 시간 줄이기 · 머리 위 표식. */
	static void tick(ShadeState st) {
		Iterator<Map.Entry<LivingEntity, Integer>> it = st.marks.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, Integer> m = it.next();
			LivingEntity e = m.getKey();
			int left = m.getValue() - 1;
			if (left <= 0 || !e.isAlive() || e.isRemoved()) {
				it.remove();
				continue;
			}
			m.setValue(left);
			if (Ticks.every(left, 4) && e.level() instanceof ServerLevel level) {
				Fx.particle(level, Fx.dust(PURPLE, 1.1F), e.getX(), e.getY() + e.getBbHeight() + 0.35, e.getZ(), 3, 0.12, 0.05, 0.12, 0);
			}
		}
	}
}
