package kr.overbreak.classes.ironfist;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.InputModePayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [Q] 파멸의 일격 — 0.1a 에서 오버워치 둠피스트의 「파멸의 일격」 방식으로 바뀌었습니다.
 *
 *   1단계 솟구침 1초(20틱): 틱당 0.6칸씩 12칸 위로 (무적)
 *   2단계 조준 최대 3초(60틱): 그 높이에 붙들림 · 모습이 사라짐 (무적)
 *     <b>이동 키(WASD)로</b> 지상의 붉은 착탄 원을 끌고 다닙니다 — 시작한 자리에서 최대 20칸, 상대에게도 보입니다
 *     시야는 자유 (원을 내려다보면 됩니다). 우클릭으로 확정, 3초가 지나면 그 자리로
 *   3단계 낙하 4틱: 투명이 풀리고 착탄점으로 내리꽂힘
 *   착탄: 반경 6칸 · 중심 150 에서 가장자리 15 까지 줄어드는 피해 (기절 없음 — 원본과 같음)
 *         흡수 체력 가득(120) · 다음 로켓 펀치 강화
 */
final class MeteorStrike implements Effects.Active {
	static final int RISE = 20;
	static final int AIM = 60;
	static final int DROP = 4;
	static final double RISE_SPEED = 0.6;
	/** 착탄 원을 끌고 다닐 수 있는 최대 거리 (솟구친 자리 기준). */
	static final double STEER_RANGE = 20.0;
	/** 착탄 원이 움직이는 속도 (1/20초 기준 칸). */
	static final double STEER_SPEED = 0.55;
	static final double RADIUS = 6.0;
	/** 중심 피해 150 → 가장자리 15 (10배 기준). */
	static final int CENTER_DAMAGE = 1500;
	static final int EDGE_DAMAGE = 150;
	/** 조준 중에는 이동 키가 착탄 원을 움직일 뿐 몸은 그 자리에 붙들립니다 (되돌리기로 화면이 튀지 않게). */
	private static final net.minecraft.resources.Identifier AIM_HOLD = Overbreak.id("doom_aim");

	private final ServerPlayer caster;
	private final IronFistState state;
	private int stage = 1;
	private int t = Ticks.of(RISE);
	private Vec3 anchor;
	private double aimX;
	private double aimZ;
	private Vec3 target;
	private @Nullable GroundShape disc;
	private @Nullable GroundShape inner;

	private MeteorStrike(ServerPlayer caster, IronFistState state) {
		this.caster = caster;
		this.state = state;
		this.anchor = caster.position();
		this.aimX = anchor.x;
		this.aimZ = anchor.z;
		this.target = caster.position();
	}

	static void cast(ServerPlayer p, IronFistState st) {
		UltGauge.consume(p);
		MeteorStrike m = new MeteorStrike(p, st);
		st.doom = m;
		// 궁극기를 쓰면 다음 로켓 펀치가 강화됩니다 (착지 순간 지속시간을 다시 채움)
		st.empowerT = Ticks.of(PowerBlock.EMPOWER);
		Attachments.combatant(p).casting = true;
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, Ticks.of(120), 4, false, false));
		p.setNoGravity(true);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.IF_ULT_RISE, -1);
		Fx.sound(p, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.7F, 1.4F);
		Fx.sound(p, SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.3F, 0.8F);
		Hud.title(p, Hud.bold("파멸의 일격", ChatFormatting.RED), Component.empty(), 0, 20, 8);
		Effects.add(m);
	}

	boolean aiming() {
		return stage == 2;
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		ServerLevel level = caster.level();
		switch (stage) {
			case 1 -> {
				caster.setDeltaMovement(0.0, Ticks.speed(RISE_SPEED), 0.0);
				caster.hurtMarked = true;
				caster.resetFallDistance();
				if (Ticks.ambient()) {
					Fx.particle(level, ParticleTypes.CLOUD, caster.getX(), caster.getY(), caster.getZ(), 8, 0.3, 0.2, 0.3, 0.05);
				}
				if (--t <= 0) {
					aimStart(level);
				}
			}
			case 2 -> {
				hold();
				steer();
				target = ground(level, aimX, aimZ);
				if (disc != null && inner != null) {
					disc.moveTo(target);
					inner.moveTo(target);
				}
				if (!InputModePayload.canSend(caster)) {
					Attachments.profile(caster).msgT = 2;
					Hud.actionbar(caster, Component.empty()
							.append(Hud.bold("파멸의 일격  ", ChatFormatting.RED))
							.append(Hud.text("이동 키로 조준 · 우클릭으로 내리꽂기", ChatFormatting.GRAY)));
				}
				if (--t <= 0) {
					drop();
				}
			}
			default -> {
				// 남은 틱 동안 착탄점까지 나눠 날아갑니다. 마지막 틱에 정확히 맞춥니다.
				Vec3 v = target.subtract(caster.position()).scale(1.0 / Math.max(1, t));
				caster.setDeltaMovement(v);
				caster.hurtMarked = true;
				caster.resetFallDistance();
				if (--t <= 0) {
					caster.teleportTo(level, target.x, target.y, target.z, Relative.ROTATION, 0.0F, 0.0F, false);
					blast(level);
					cleanup();
					return false;
				}
			}
		}
		return true;
	}

	private void aimStart(ServerLevel level) {
		stage = 2;
		t = Ticks.of(AIM);
		anchor = caster.position();
		aimX = anchor.x;
		aimZ = anchor.z;
		caster.setDeltaMovement(Vec3.ZERO);
		caster.hurtMarked = true;
		caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Ticks.of(AIM + 20), 0, false, false));
		CrowdControl.mod(caster, Attributes.MOVEMENT_SPEED, AIM_HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		CrowdControl.mod(caster, Attributes.JUMP_STRENGTH, AIM_HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		target = ground(level, aimX, aimZ);
		disc = GroundShape.sector(level, target, 0.0F, 360.0, RADIUS, 0x38FF2020, 0xE8FF3A3A);
		inner = GroundShape.ring(level, target, RADIUS / 3.0, 0xC0FF7A50);
		SkillAnimPayload.stop(caster, SkillAnimPayload.IF_ULT_RISE);
		Fx.sound(caster, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.6F);
	}

	/** 이동 키로 착탄 원을 끌고 다닙니다 (둠피스트 원본과 같은 조작). */
	private void steer() {
		Input in = caster.getLastClientInput();
		double f = (in.forward() ? 1.0 : 0.0) - (in.backward() ? 1.0 : 0.0);
		double s = (in.left() ? 1.0 : 0.0) - (in.right() ? 1.0 : 0.0);
		if (f == 0.0 && s == 0.0) {
			return;
		}
		double yaw = Math.toRadians(caster.getYRot());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		// 바라보는 방향 = (-sin, cos), 왼쪽 = (cos, sin)
		double mx = -sin * f + cos * s;
		double mz = cos * f + sin * s;
		double len = Math.sqrt(mx * mx + mz * mz);
		if (len < 1.0E-4) {
			return;
		}
		double step = Ticks.speed(STEER_SPEED) / len;
		aimX += mx * step;
		aimZ += mz * step;
		double dx = aimX - anchor.x;
		double dz = aimZ - anchor.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d > STEER_RANGE) {
			aimX = anchor.x + dx / d * STEER_RANGE;
			aimZ = anchor.z + dz / d * STEER_RANGE;
		}
	}

	/** 올라온 자리에 붙들어 둡니다. 회전은 건드리지 않습니다. */
	private void hold() {
		if (caster.position().distanceToSqr(anchor) > 0.05 * 0.05) {
			caster.teleportTo(caster.level(), anchor.x, anchor.y, anchor.z, Relative.ROTATION, 0.0F, 0.0F, false);
		}
		caster.setDeltaMovement(Vec3.ZERO);
		caster.resetFallDistance();
	}

	/** 2단계에서 우클릭 · 시간 초과. */
	void drop() {
		if (stage != 2) {
			return;
		}
		stage = 3;
		t = Ticks.of(DROP);
		unhold();
		caster.removeEffect(MobEffects.RESISTANCE);
		caster.removeEffect(MobEffects.INVISIBILITY);
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.IF_ULT_DROP, -1);
		Fx.sound(caster, SoundEvents.TRIDENT_RIPTIDE_2, SoundSource.PLAYERS, 1.4F, 0.6F);
	}

	/** (x, z) 아래의 지면. */
	private Vec3 ground(ServerLevel level, double x, double z) {
		Vec3 from = new Vec3(x, anchor.y + 1.0, z);
		BlockHitResult down = level.clip(new ClipContext(from, from.add(0.0, -64.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		return down.getType() == HitResult.Type.MISS ? new Vec3(x, anchor.y - 12.0, z) : down.getLocation();
	}

	private void blast(ServerLevel level) {
		Vec3 at = caster.position();
		Fx.sound(caster, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6F, 0.5F);
		Fx.sound(caster, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.6F, 0.6F);
		Fx.sound(caster, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.2F);
		// 착탄 연출은 짧게 — 큰 폭발 파티클을 여러 개 겹치면 시전자 화면이 한동안 하얗게 덮입니다
		Fx.particle(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 4, 2.6, 0.2, 2.6, 0);
		Fx.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
				at.x, at.y + 0.2, at.z, 60, 2.6, 0.1, 2.6, 0.5);
		GroundShape.flash(level, at, 0.0F, 360.0, RADIUS, 0x60FF3020, 0xF0FF5030, 8);

		// 중심에서 멀어질수록 줄어드는 피해 (중심 150 → 가장자리 15)
		for (LivingEntity e : Targets.enemies(level, at, RADIUS, caster)) {
			SkillDamage.deal(e, caster, damage10(at, e), SkillDamage.Kind.MULTI_NO_KB);
			Absorb.gain(caster);
			Fx.particle(level, ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.4, 0.3, 0.3);
		}
		Absorb.fill(caster, state);
		state.empowerT = Ticks.of(PowerBlock.EMPOWER);
	}

	/** 중심에서의 수평 거리에 따라 줄어드는 피해 (10배 기준). */
	static int damage10(Vec3 center, LivingEntity e) {
		double dx = e.getX() - center.x;
		double dz = e.getZ() - center.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		double k = 1.0 - Math.min(1.0, Math.max(0.0, d / RADIUS));
		return EDGE_DAMAGE + (int) Math.round((CENTER_DAMAGE - EDGE_DAMAGE) * k);
	}

	@Override
	public void cancel() {
		cleanup();
	}

	@Override
	public @Nullable Object owner() {
		return caster;
	}

	private void unhold() {
		CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, AIM_HOLD);
		CrowdControl.unmod(caster, Attributes.JUMP_STRENGTH, AIM_HOLD);
	}

	private void cleanup() {
		unhold();
		Attachments.combatant(caster).casting = false;
		caster.setNoGravity(false);
		caster.resetFallDistance();
		if (stage < 3) {
			caster.removeEffect(MobEffects.RESISTANCE);
			caster.removeEffect(MobEffects.INVISIBILITY);
			SkillAnimPayload.stop(caster, SkillAnimPayload.IF_ULT_RISE);
		}
		if (disc != null) {
			disc.discard();
		}
		if (inner != null) {
			inner.discard();
		}
		if (state.doom == this) {
			state.doom = null;
		}
	}
}
