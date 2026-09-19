package kr.overbreak.classes.thunder;

import kr.overbreak.core.tick.Ticks;
import java.util.IdentityHashMap;
import java.util.Map;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * [웅크리기] 뇌운 — 조준한 땅 위에 번개구름을 띄움.
 *
 *   16칸 안의 조준점 (벽 · 몸에 닿은 곳, 그 아래 땅) 위 3.5칸 높이에 구름 · 4초 유지
 *   구름 아래 반경 3.5칸의 적: 30% 둔화 (안에 있는 동안) · 0.5초마다 구름에서 번개가 내리쳐 8 피해 (넉백 없음)
 *   정전기는 한 적에게 1초에 한 번만 쌓임 · 쿨타임 13초
 */
final class StormField implements Effects.Active {
	static final int COOLDOWN = 260;
	static final double RANGE = 20.0;
	static final double RADIUS = 4.5;
	static final double HEIGHT = 3.5;
	static final int LIFE = 80;
	static final int ZAP_GAP = 10;
	static final int DAMAGE_10 = 80;
	static final double SLOW = 0.15;

	private final ServerPlayer caster;
	private final ThunderState state;
	private final Vec3 center;
	private final Map<LivingEntity, Integer> lastCharge = new IdentityHashMap<>();
	private int t;

	private StormField(ServerPlayer caster, ThunderState state, Vec3 center) {
		this.caster = caster;
		this.state = state;
		this.center = center;
	}

	static void cast(ServerPlayer p, ThunderState st) {
		if (st.descent != null || Cooldowns.blocked(p, Thunder.FIELD, "뇌운", ChatFormatting.AQUA)) {
			return;
		}
		Attachments.profile(p).setCooldown(Thunder.FIELD, COOLDOWN);
		Vec3 at = Thunder.groundTarget(p, RANGE);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.TH_FIELD, -1);
		Fx.sound(p.level(), at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.3F);
		Fx.sound(p.level(), at.x, at.y, at.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.5F, 1.8F);
		Effects.add(new StormField(p, st, at));
	}

	@Override
	public boolean tick() {
		ServerLevel level = caster.level();
		t++;
		Vec3 cloud = center.add(0.0, HEIGHT, 0.0);
		// 구름: 납작하게 모인 짙은 남회색 먼지 (떠오르는 연기는 흩날려 지저분해서 쓰지 않음) + 속에서 번쩍이는 불꽃
		if (Ticks.ambient()) {
			Fx.particle(level, Fx.dust(Fx.rgb(0.20, 0.24, 0.36), 3.5F), cloud.x, cloud.y, cloud.z, 8, RADIUS * 0.4, 0.12, RADIUS * 0.4, 0);
			Fx.particle(level, Fx.dust(Fx.rgb(0.34, 0.40, 0.55), 2.5F), cloud.x, cloud.y + 0.25, cloud.z, 4, RADIUS * 0.3, 0.1, RADIUS * 0.3, 0);
		}
		if (Ticks.every(t, 3)) {
			Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, cloud.x, cloud.y, cloud.z, 6, RADIUS * 0.4, 0.2, RADIUS * 0.4, 0.1);
			Fx.ring(level, center, RADIUS, 20, 0.1, Fx.dust(StaticCharge.CYAN, 1.0F));
		}
		boolean alive = caster.isAlive() && !caster.isRemoved();
		for (LivingEntity e : Targets.within(level, center, RADIUS, e -> e != caster)) {
			if (Math.abs(e.getY() - center.y) > HEIGHT + 1.0) {
				continue;
			}
			CrowdControl.slow(e, SLOW, 4);
			if (!Ticks.every(t, ZAP_GAP)) {
				continue;
			}
			Vec3 from = cloud.add((level.getRandom().nextDouble() - 0.5) * RADIUS, 0.0, (level.getRandom().nextDouble() - 0.5) * RADIUS);
			Tracer.spawn(level, alive ? caster : null, from, e.position().add(0.0, e.getBbHeight() * 0.6, 0.0), Tracer.LIGHTNING);
			SkillDamage.deal(e, alive ? caster : null, DAMAGE_10, SkillDamage.Kind.MULTI_NO_KB);
			Fx.sound(e, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.6F, 2.0F);
			Integer last = lastCharge.get(e);
			if (alive && (last == null || t - last >= Ticks.of(20))) {
				lastCharge.put(e, t);
				StaticCharge.add(caster, state, e);
			}
		}
		return t < Ticks.of(LIFE);
	}

	@Override
	public void cancel() {
	}

	@Override
	public Object owner() {
		return caster;
	}
}
