package kr.overbreak.classes.thunder;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Targets;
import kr.overbreak.util.Tracer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * [E] 낙뢰 — 조준한 자리에 하늘에서 벼락을 내리꽂음.
 *
 *   18칸 안의 조준점 · 0.6초(12틱) 동안 바닥에 번쩍이는 고리로 예고 (피할 수 있음)
 *   반경 2.5칸: 45 피해 + 0.6초 띄우기 + 정전기 1스택 · 하늘에서 굵은 번개 + 바닐라 벼락 연출 (불 · 피해 없음)
 *   쿨타임 11초
 */
final class Thunderbolt implements Effects.Active {
	static final int COOLDOWN = 220;
	static final double RANGE = 18.0;
	static final int DELAY = 14;
	static final double RADIUS = 2.5;
	static final int DAMAGE_10 = 450;
	static final int AIRBORNE = 8;

	private final ServerPlayer caster;
	private final ThunderState state;
	private final Vec3 at;
	private int t;

	private Thunderbolt(ServerPlayer caster, ThunderState state, Vec3 at) {
		this.caster = caster;
		this.state = state;
		this.at = at;
	}

	static void cast(ServerPlayer p, ThunderState st) {
		if (st.descent != null || Cooldowns.blocked(p, Thunder.SMITE, "낙뢰", ChatFormatting.AQUA)) {
			return;
		}
		Attachments.profile(p).setCooldown(Thunder.SMITE, COOLDOWN);
		Vec3 at = Thunder.groundTarget(p, RANGE);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.TH_SMITE, -1);
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		Fx.sound(p.level(), at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.2F, 1.4F);
		Effects.add(new Thunderbolt(p, st, at));
	}

	@Override
	public boolean tick() {
		ServerLevel level = caster.level();
		t++;
		int delay = Ticks.of(DELAY);
		if (t < delay) {
			// 예고: 좁아지는 고리 + 하늘로 오르는 불꽃
			if (Ticks.ambient()) {
				double r = RADIUS * (1.0 - 0.5 * t / (double) delay);
				Fx.ring(level, at, RADIUS, 24, 0.05, Fx.dust(StaticCharge.CYAN, 1.2F));
				Fx.ring(level, at, r, 16, 0.05, ParticleTypes.ELECTRIC_SPARK);
				Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3, at.z, 4, 0.3, 0.2, 0.3, 0.2);
			}
			return true;
		}
		strike(level, at, caster.isAlive() && !caster.isRemoved() ? caster : null, state, DAMAGE_10, RADIUS, AIRBORNE);
		return false;
	}

	/** 한 자리에 벼락 — 낙뢰 · 뇌신강림이 같이 씁니다. */
	static void strike(ServerLevel level, Vec3 at, ServerPlayer src, ThunderState st, int damage10, double radius, int airborne) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		Tracer.spawn(level, src, at.add(0.0, 18.0, 0.0), at, Tracer.LIGHTNING_BIG);
		Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 80, 0.8, 0.8, 0.8, 0.6);
		Fx.particle(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
		Fx.particle(level, Fx.dust(StaticCharge.CYAN, 2.0F), at.x, at.y + 0.3, at.z, 40, radius * 0.5, 0.3, radius * 0.5, 0);
		for (LivingEntity e : Targets.within(level, at, radius, e -> e != src)) {
			if (Math.abs(e.getY() - at.y) > 3.0) {
				continue;
			}
			SkillDamage.deal(e, src, damage10, SkillDamage.Kind.MULTI_NO_KB);
			if (e.isAlive() && airborne > 0) {
				CrowdControl.airborne(e, airborne, 1);
			}
			if (src != null) {
				StaticCharge.add(src, st, e);
			}
		}
	}

	@Override
	public void cancel() {
	}

	@Override
	public Object owner() {
		return caster;
	}
}
