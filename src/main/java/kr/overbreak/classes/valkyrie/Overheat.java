package kr.overbreak.classes.valkyrie;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Local;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [E] 과열 분사 — 데이터팩 skill/overheat/* 대응 (체력 10배 기준).
 *
 *   1초 동안 2틱마다 한 발, 모두 10발. 매 발 그 순간의 조준 방향 60도 · 5칸 부채꼴 전원에게
 *   발당 8.5 (다 맞으면 85) + 1.5초 40% 둔화. 넉백 없음
 *   난사 중 이동은 자유. 기절 · 넘어뜨림 · 에어본에 걸리면 남은 발수는 사라짐
 *   판정 범위: 조준을 따라 도는 반투명 부채꼴. 쿨타임 10초
 */
final class Overheat implements Effects.Active {
	static final int SHOTS = 10;
	static final int INTERVAL = 2;
	static final double RADIUS = 5.0;
	static final double ARC = 60.0;
	static final int DAMAGE_100 = 850;
	static final int COOLDOWN = 200;

	private final ServerPlayer caster;
	private final ValkyrieState state;
	private final GroundShape shape;
	private int shots = SHOTS;
	private int t;

	private Overheat(ServerPlayer caster, ValkyrieState state, GroundShape shape) {
		this.caster = caster;
		this.state = state;
		this.shape = shape;
	}

	static void cast(ServerPlayer p, ValkyrieState st) {
		if (st.overheat != null || Cooldowns.blocked(p, Valkyrie.OVERHEAT, "과열 분사", ChatFormatting.GOLD)) {
			return;
		}
		Attachments.profile(p).setCooldown(Valkyrie.OVERHEAT, COOLDOWN);
		GroundShape shape = GroundShape.sector(p.level(), p.position(), Aim.facingYawPitch(p)[0], ARC, RADIUS, 0x34FF7A1E, 0xD8FF9A3C);
		Overheat o = new Overheat(p, st, shape);
		st.overheat = o;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.VK_OVERHEAT, -1);
		Fx.sound(p, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.4F, 0.6F);
		Effects.add(o);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		if (Attachments.combatant(caster).interrupted()) {
			ServerLevel level = caster.level();
			Fx.particle(level, ParticleTypes.SMOKE, caster.getX(), caster.getY() + 1, caster.getZ(), 15, 0.3, 0.4, 0.3, 0.02);
			SkillAnimPayload.stop(caster, SkillAnimPayload.VK_OVERHEAT);
			cleanup();
			return false;
		}
		float yaw = Aim.facingYawPitch(caster)[0];
		shape.moveTo(caster.position());
		shape.setYaw(yaw);
		if (--t <= 0) {
			t = Ticks.of(INTERVAL);
			fire(yaw);
			if (shots <= 0) {
				Fx.sound(caster, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.2F);
				cleanup();
				return false;
			}
		}
		return true;
	}

	private void fire(float yaw) {
		shots--;
		ServerLevel level = caster.level();
		Fx.sound(caster, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.4F);
		// 총구 불꽃 (범위 표시가 아니라 발사 연출). 시전자 본인 화면은 가리지 않게 남에게만
		Vec3 muzzle = Local.flat(caster.getEyePosition(), yaw, -0.3, -0.45, 0.8);
		Fx.particleExcept(level, caster, ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.12, 0.06, 0.12, 0.01);
		for (LivingEntity e : Targets.enemies(level, caster.position(), RADIUS, caster)) {
			if (Targets.inCone(caster.position(), yaw, ARC / 2.0, RADIUS, e)) {
				SkillDamage.dealFine(e, caster, DAMAGE_100, SkillDamage.Kind.MULTI_NO_KB);
				if (e.isAlive()) {
					CrowdControl.slow(e, 0.4, 30);
				}
				Fx.particle(level, ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 8, 0.2, 0.3, 0.2, 0.3);
			}
		}
	}

	@Override
	public void cancel() {
		cleanup();
	}

	@Override
	public @Nullable Object owner() {
		return caster;
	}

	private void cleanup() {
		GroundShape.fadeOut(shape, 5, null);
		if (state.overheat == this) {
			state.overheat = null;
		}
	}
}
