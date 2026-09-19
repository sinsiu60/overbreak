package kr.overbreak.classes.thunder;

import kr.overbreak.core.tick.Ticks;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Motion;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
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
 * [우클릭] 섬전 — 몸이 번개가 되어 앞으로 내달림.
 *
 *   바라보는 수평 방향으로 0.2초(4틱) 동안 틱당 2칸 = 8칸 (높이 유지). 적을 뚫고 지나감
 *   달리는 동안 모습이 사라지고(클라이언트) 지나간 길에 번개가 남음 · 피해를 받지 않음
 *   지나가며 몸 1.6칸 안의 적마다 25 피해 (넉백 없음) + 정전기 1스택. 벽에 막히면 멈춤
 *   이동기라 균열 지대 · 섬광 봉인 중 불가 · 쿨타임 9초
 */
final class LightningStep implements Effects.Active {
	static final int COOLDOWN = 180;
	static final int TICKS = 4;
	/** 쓰기 전 기 모으는 시간 (0.1 버전 너프). */
	static final int WINDUP = 4;
	static final double SPEED = 2.0;
	static final double REACH = 1.6;
	static final int DAMAGE_10 = 250;

	private final ServerPlayer caster;
	private final ThunderState state;
	private final Set<LivingEntity> hit = Collections.newSetFromMap(new IdentityHashMap<>());
	private Vec3 last;
	private int t;
	private int windup = Ticks.of(WINDUP);
	private boolean dashing;

	private LightningStep(ServerPlayer caster, ThunderState state) {
		this.caster = caster;
		this.state = state;
		this.last = caster.position();
	}

	static void cast(ServerPlayer p, ThunderState st) {
		if (st.step != null || st.descent != null) {
			return;
		}
		if (Attachments.combatant(p).sealT > 0) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("이동기가 봉인되어 있다", ChatFormatting.AQUA));
			return;
		}
		if (Cooldowns.blocked(p, Thunder.STEP, "섬전", ChatFormatting.AQUA)) {
			return;
		}
		Attachments.profile(p).setCooldown(Thunder.STEP, COOLDOWN);
		LightningStep s0 = new LightningStep(p, st);
		st.step = s0;
		Attachments.combatant(p).casting = true;
		Fx.sound(p, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 0.6F);
		Effects.add(s0);
	}

	/** 기를 다 모으면 튀어 나갑니다. */
	private void launch() {
		ServerPlayer p = caster;
		ThunderState st = state;
		dashing = true;
		Attachments.combatant(p).casting = false;
		Vec3 dir = Motion.flatLook(p);
		Motion.dash(p, dir.x, dir.z, SPEED, TICKS, true);
		CrowdControl.track(p);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.TH_DASH, -1, TICKS);
		ServerLevel level = p.level();
		// 달려갈 길 전체에 번개 한 줄기 (벽이 있으면 벽 앞까지) — 이동은 클라이언트가 보내 와 늦게 보이므로 미리 그림
		Vec3 from = p.position().add(0.0, 1.0, 0.0);
		Vec3 to = from.add(dir.scale(SPEED * TICKS));
		net.minecraft.world.phys.BlockHitResult wall = level.clip(new net.minecraft.world.level.ClipContext(from, to,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			to = wall.getLocation().subtract(dir.scale(0.4));
		}
		Tracer.spawn(level, p, from, to, Tracer.LIGHTNING);
		Tracer.spawn(level, p, from.add(0.0, 0.4, 0.0), to.add(0.0, -0.3, 0.0), Tracer.LIGHTNING);
		Fx.sound(p, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.6F, 2.0F);
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 2.0F);
		Fx.particleExcept(level, p, ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.3, 0.7, 0.3, 0.5);
		zap();
	}

	/** 달리는 중인가 (기 모으는 동안에는 무적이 아닙니다). */
	boolean dashing() {
		return dashing;
	}

	private void zap() {
		ServerLevel level = caster.level();
		Vec3 now = caster.position();
		// 궤적 번개 · 불꽃은 시간 단위마다 한 번 (60틱에서 세 배로 겹치지 않게)
		if (t == 0 || Ticks.ambient()) {
			if (now.distanceToSqr(last) > 0.04) {
				Tracer.spawn(level, caster, last.add(0.0, 1.0, 0.0), now.add(0.0, 1.0, 0.0), Tracer.LIGHTNING);
			}
			last = now;
			Fx.particleExcept(level, caster, ParticleTypes.ELECTRIC_SPARK, now.x, now.y + 1.0, now.z, 12, 0.3, 0.6, 0.3, 0.3);
		}
		for (LivingEntity e : Targets.within(level, now, REACH, e -> e != caster)) {
			if (!hit.add(e)) {
				continue;
			}
			SkillDamage.deal(e, caster, DAMAGE_10, SkillDamage.Kind.MULTI_NO_KB);
			StaticCharge.add(caster, state, e);
			Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1.0, e.getZ(), 20, 0.3, 0.5, 0.3, 0.3);
		}
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			end(false);
			return false;
		}
		if (!dashing) {
			// 기 모으기 — 발밑에 불꽃이 감김
			if (Attachments.combatant(caster).hardCc()) {
				end(false);
				return false;
			}
			if (Ticks.ambient()) {
				Fx.particle(caster.level(), ParticleTypes.ELECTRIC_SPARK, caster.getX(), caster.getY() + 0.2, caster.getZ(),
						6, 0.35, 0.1, 0.35, 0.05);
			}
			if (--windup > 0) {
				return true;
			}
			last = caster.position();
			launch();
			return true;
		}
		t++;
		zap();
		if (t >= Ticks.of(TICKS) || (t > Ticks.of(1) && caster.horizontalCollision)) {
			end(true);
			return false;
		}
		return true;
	}

	private void end(boolean brake) {
		Attachments.combatant(caster).casting = false;
		if (state.step == this) {
			state.step = null;
		}
		if (brake) {
			Motion.brake(caster);
			Fx.particleExcept(caster.level(), caster, ParticleTypes.ELECTRIC_SPARK, caster.getX(), caster.getY() + 1.0, caster.getZ(), 30, 0.4, 0.7, 0.4, 0.4);
			Fx.sound(caster, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.8F, 1.6F);
		}
	}

	@Override
	public void cancel() {
		end(false);
	}

	@Override
	public Object owner() {
		return caster;
	}
}
