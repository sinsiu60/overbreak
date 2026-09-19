package kr.overbreak.classes.thunder;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Hitscan;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Local;
import kr.overbreak.util.Targets;
import kr.overbreak.util.Tracer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * [좌클릭] 뇌격 — 창끝에서 조준 방향으로 번개 한 줄기.
 *
 *   14칸 히트스캔 · 18 피해 (넉백 없음) · 정전기 1스택 · 0.55초(11틱)에 한 번
 *   맞은 적에서 5칸 안의 가장 가까운 다른 적에게 번개가 튀어 9 피해 (벽 너머 제외, 스택은 안 쌓임)
 */
final class Bolt {
	static final double RANGE = 14.0;
	static final int DAMAGE_10 = 200;
	static final int CHAIN_10 = 90;
	static final double CHAIN_RANGE = 5.0;
	static final int GAP = 11;

	private Bolt() {}

	static void fire(ServerPlayer p, ThunderState st) {
		if (st.shotCd > 0 || st.step != null) {
			return;
		}
		st.shotCd = Ticks.of(GAP);
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 dir = Aim.direction(p);
		Hitscan.Hit hit = Hitscan.cast(p, eye, dir, RANGE);
		float[] yp = Local.yawPitch(dir);
		Vec3 tip = Local.offset(eye, yp[0], yp[1], -0.3, -0.2, 0.6);
		Tracer.spawn(level, p, tip, hit.end(), Tracer.LIGHTNING);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.TH_CAST, -1);
		Fx.sound(p, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 2.0F);
		Fx.sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.5F, 1.8F);
		Fx.particleExcept(level, p, ParticleTypes.ELECTRIC_SPARK, hit.end().x, hit.end().y, hit.end().z, 10, 0.15, 0.15, 0.15, 0.2);

		LivingEntity victim = hit.target();
		if (victim == null) {
			return;
		}
		SkillDamage.deal(victim, p, DAMAGE_10, SkillDamage.Kind.MULTI_NO_KB);
		StaticCharge.add(p, st, victim);
		Fx.sound(victim, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.8F);

		// 연쇄: 맞은 적 곁의 가장 가까운 다른 적
		Vec3 from = victim.position().add(0.0, victim.getBbHeight() * 0.6, 0.0);
		LivingEntity next = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : Targets.within(level, victim.position(), CHAIN_RANGE, e -> e != p && e != victim)) {
			Vec3 to = e.position().add(0.0, e.getBbHeight() * 0.6, 0.0);
			if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) {
				continue;
			}
			double d = e.distanceToSqr(victim);
			if (d < best) {
				best = d;
				next = e;
			}
		}
		if (next != null) {
			Tracer.spawn(level, p, from, next.position().add(0.0, next.getBbHeight() * 0.6, 0.0), Tracer.LIGHTNING);
			SkillDamage.deal(next, p, CHAIN_10, SkillDamage.Kind.MULTI_NO_KB);
			Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, next.getX(), next.getY() + 1.0, next.getZ(), 10, 0.2, 0.3, 0.2, 0.2);
		}
	}
}
