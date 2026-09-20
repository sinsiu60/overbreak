package kr.overbreak.classes.ironfist;

import kr.overbreak.core.tick.Ticks;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Motion;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [E] 지진 강타 — 데이터팩 skill/slam/* 대응 (체력 10배 기준).
 *
 *   도약: 조준 방향(피치 포함)으로 속도를 한 번만 주고 나머지는 중력 (포물선). 땅에서 쓰면 반드시 떠오름
 *     시간 제한 없이 착지까지. 그동안 평타 · 스킬 불가. 발사 직후 6틱은 착지로 보지 않음
 *     비행 중 F 재입력 = 캔슬 (강타 없음, 그대로 떨어짐) · 기절도 캔슬. 쿨타임은 누른 순간부터 7초
 *   착지: 그 자리 · 그 순간의 조준 방향으로 90도 · 16칸 파면이 틱당 2.25칸씩 퍼짐 (파면이 닿는 순간에만 피해)
 *     피해 15 (시전 전 체공 + 비행 20틱이면 최대 +75% = 26.2) + 1.5초 30% 둔화, 대상당 1회, 적중 1명당 흡수 +20
 *   균열 지대 위(봉인)에서는 쓸 수 없음
 */
final class SeismicSlam {
	static final int COOLDOWN = 140;
	static final int GRACE = 6;
	static final double POWER = 1.40;
	static final double RANGE = 16.0;
	static final double ARC = 90.0;
	static final double SPEED = 2.25;
	/** 최소 40 (체공 0) ~ 최대 60 (체공 1초) — 0.1 버전. */
	static final int BASE_100 = 400;
	static final int AIR_MAX = 20;

	private final ServerPlayer caster;
	private final IronFistState state;
	private int grace = Ticks.of(GRACE);

	private SeismicSlam(ServerPlayer caster, IronFistState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, IronFistState st) {
		if (Attachments.combatant(p).sealT > 0) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("균열 지대 위에서는 이동기를 쓸 수 없다", ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (st.slam != null || Cooldowns.blocked(p, IronFist.SLAM, "지진 강타", ChatFormatting.GOLD)) {
			return;
		}
		Attachments.profile(p).setCooldown(IronFist.SLAM, COOLDOWN);
		Motion.launch(p, Aim.facing(p), POWER);
		st.slam = new SeismicSlam(p, st);
		Attachments.combatant(p).casting = true;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.IF_SLAM_AIR, -1);
		Fx.sound(p, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.2F, 0.8F);
		Fx.sound(p, SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1.0F, 1.2F);
		Fx.particle(p.level(), ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 20, 0.4, 0.1, 0.4, 0.1);
	}

	/** 비행 — 직업 틱에서 체공 카운터를 갱신하기 전에 부릅니다 (착지한 틱의 체공 값을 써야 하므로). */
	void flyTick() {
		if (Attachments.combatant(caster).hardCc()) {
			cancel();
			return;
		}
		caster.resetFallDistance();
		if (Ticks.ambient()) {
			Fx.particle(caster.level(), ParticleTypes.CLOUD, caster.getX(), caster.getY() + 0.2, caster.getZ(), 3, 0.2, 0.1, 0.2, 0.02);
		}
		if (grace > 0) {
			grace--;
			if (grace > 0) {
				return;
			}
		}
		if (caster.onGround()) {
			land();
		}
	}

	/** 재시전 · 기절 — 강타 없이 끝냅니다. 속도는 건드리지 않아 그대로 떨어집니다. */
	void cancel() {
		finish();
		SkillAnimPayload.stop(caster, SkillAnimPayload.IF_SLAM_AIR);
		Attachments.profile(caster).msgT = 25;
		Hud.actionbar(caster, Hud.text("지진 강타 취소", ChatFormatting.DARK_GRAY));
		Fx.sound(caster, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.4F);
	}

	private void land() {
		finish();
		int air = Math.min(Ticks.toTime(state.air), AIR_MAX);
		int base100 = BASE_100 * (100 + air * 50 / AIR_MAX) / 100;
		SkillAnimPayload.stop(caster, SkillAnimPayload.IF_SLAM_AIR);
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.IF_SLAM_HIT, -1);

		ServerLevel level = caster.level();
		Fx.sound(caster, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.8F);
		Fx.sound(caster, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9F, 0.6F);
		Fx.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
				caster.getX(), caster.getY() + 0.2, caster.getZ(), 60, 1.2, 0.2, 1.2, 0.5);
		Fx.particle(level, ParticleTypes.EXPLOSION, caster.getX(), caster.getY() + 0.3, caster.getZ(), 2, 0.6, 0.1, 0.6, 0);
		// 10배 기준 0.01 단위
		Effects.add(new Wave(level, caster, caster.position(), Aim.facingYawPitch(caster)[0], base100 * 10));
	}

	private void finish() {
		Attachments.combatant(caster).casting = false;
		if (state.slam == this) {
			state.slam = null;
		}
	}

	/**
	 * 퍼져 나가는 파면 — 착지 지점 · 방향에 고정됩니다. 시전자가 죽거나 움직여도 그대로 퍼집니다.
	 * 바닥 표시: 90도 · 16칸 부채꼴 윤곽 + 앞으로 나아가는 밝은 호.
	 */
	static final class Wave implements Effects.Active {
		private final ServerPlayer caster;
		private final Vec3 origin;
		private final float yaw;
		private final int damage100;
		private final GroundShape outline;
		private final GroundShape front;
		private final Set<LivingEntity> hit = Collections.newSetFromMap(new IdentityHashMap<>());
		private double radius;

		Wave(ServerLevel level, ServerPlayer caster, Vec3 origin, float yaw, int damage100) {
			this.caster = caster;
			this.origin = origin;
			this.yaw = yaw;
			this.damage100 = damage100;
			this.outline = GroundShape.sector(level, origin, yaw, ARC, RANGE, 0x22B48C5A, 0xD0C8A064);
			this.front = GroundShape.arc(level, origin, yaw, ARC, 0.6, 0xF0FFE68C, 0.5);
		}

		@Override
		public boolean tick() {
			radius = Math.min(RANGE, radius + Ticks.speed(SPEED));
			front.setRadius(Math.max(0.6, radius));
			ServerLevel level = (ServerLevel) caster.level();
			ServerPlayer src = caster.isAlive() && !caster.isRemoved() ? caster : null;
			for (LivingEntity e : Targets.within(level, origin, radius, e -> e != caster)) {
				if (!hit.contains(e) && Targets.inCone(origin, yaw, ARC / 2.0, radius, e)) {
					hit.add(e);
					strike(level, src, e);
				}
			}
			if (radius >= RANGE) {
				GroundShape.fadeOut(front, 4, null);
				GroundShape.fadeOut(outline, 8, null);
				return false;
			}
			return true;
		}

		private void strike(ServerLevel level, @Nullable ServerPlayer src, LivingEntity e) {
			SkillDamage.dealFine(e, src, damage100, SkillDamage.Kind.MULTI_NO_KB);
			if (e.isAlive()) {
				CrowdControl.slow(e, 0.3, 30);
			}
			if (src != null) {
				Absorb.gain(src);
			}
			Fx.particle(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
					e.getX(), e.getY() + 0.3, e.getZ(), 20, 0.3, 0.2, 0.3, 0.3);
			Fx.sound(e, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.1F, 0.7F);
		}

		@Override
		public void cancel() {
			outline.discard();
			front.discard();
		}
	}
}
