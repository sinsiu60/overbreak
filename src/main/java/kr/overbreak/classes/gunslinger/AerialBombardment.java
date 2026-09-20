package kr.overbreak.classes.gunslinger;

import java.util.List;

import kr.overbreak.combat.Aim;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * [Q] 차원 회전 포격 — 높이 솟구쳐 공중에 멈춰 선 채 아래를 3초 동안 갈아엎습니다.
 *
 *   0.4초 동안 약 6칸 솟구친 뒤 그 자리에 붙잡힘 (중력 · 이동 없음)
 *   조준한 곳 아래 땅에 지름 10칸(반경 5칸)의 조준원이 생기고, 0.25초마다 포탄이 쏟아짐
 *   조준원 안의 적은 0.25초마다 15 — 초당 60, 끝까지 맞으면 180
 *   포격 중에는 반동 도약을 쿨타임 없이 쓸 수 있습니다 (공중 이동) — 쓰면 0.4초 동안 붙잡힘이 풀립니다
 *   끝나면 떨어집니다 (낙하 피해 없음)
 *
 * 1인칭 시점은 그대로 둡니다 (0.2b 에서 강제 시점 변경을 되돌린 것과 같은 결). 대신 바닥의 조준원이 착탄점을 알려 줍니다.
 */
public final class AerialBombardment implements Effects.Active {
	/** 솟구치는 시간 (시간 단위). */
	public static final int RISE = 8;
	/** 포격 시간 (시간 단위 · 3초). */
	public static final int DURATION = 60;
	static final int LENGTH = RISE + DURATION;
	/** 포탄 간격 (시간 단위 · 0.25초). */
	static final int PULSE = 5;
	/** 조준원 반경 (칸) — 지름 10칸. */
	public static final double RADIUS = 5.0;
	/** 한 번의 포격 피해 x100 (15.0 → 초당 60). */
	static final int PULSE_DAMAGE_100 = 1500;
	/** 조준원을 찾는 최대 거리 (칸). */
	static final double AIM_RANGE = 40.0;
	/** 반동 도약으로 붙잡힘이 풀리는 시간 (시간 단위). */
	static final int FREE = 8;
	private static final int SKY = Fx.rgb(0.70, 0.92, 1.00);
	private static final int FIRE = Fx.rgb(1.00, 0.78, 0.35);

	private final ServerPlayer caster;
	private final GunslingerState state;
	private int t;
	/** 반동 도약으로 풀려 있는 남은 틱. */
	private int freeT;
	private Vec3 mark;

	private AerialBombardment(ServerPlayer caster, GunslingerState state) {
		this.caster = caster;
		this.state = state;
		this.mark = groundTarget(caster);
	}

	static void cast(ServerPlayer p, GunslingerState st) {
		if (st.bombardment != null) {
			return;
		}
		UltGauge.consume(p);
		if (st.acrobatics != null) {
			st.acrobatics.cancel();
		}
		DualPistols.cancelReload(p, st);
		AeroDrift.stop(p, st);
		AerialBombardment b = new AerialBombardment(p, st);
		st.bombardment = b;
		Attachments.combatant(p).ccImmune = true;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.GS_ULT, -1, Ticks.of(LENGTH));
		ServerLevel level = p.level();
		Fx.sound(p, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.4F, 0.8F);
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.3F, 1.4F);
		Fx.particleExcept(level, p, ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 60, 0.8, 0.2, 0.8, 0.2);
		Fx.ring(level, p.position(), 2.2, 28, 0.1, Fx.dust(SKY, 1.5F));
		if (!SkillAnimPayload.canSend(p)) {
			Hud.title(p, Hud.bold("차원 회전 포격", ChatFormatting.AQUA), Component.empty(), 0, 30, 10);
		}
		Effects.add(b);
	}

	/** 남은 시간 0~100 (HUD). */
	public int remainingPercent() {
		int length = Ticks.of(LENGTH);
		return Math.max(0, (length - t) * 100 / length);
	}

	/** 반동 도약을 썼다 — 잠깐 붙잡힘을 풉니다. */
	void loosen() {
		freeT = Ticks.of(FREE);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			end();
			return false;
		}
		t++;
		ServerLevel level = caster.level();
		int rise = Ticks.of(RISE);
		if (freeT > 0) {
			// 반동 도약으로 날아가는 동안에는 붙잡지 않습니다
			freeT--;
		} else if (t <= rise) {
			caster.setDeltaMovement(0.0, Ticks.speed(1.0 * (1.0 - (t - 1) / (double) rise)), 0.0);
			caster.hurtMarked = true;
		} else {
			caster.setDeltaMovement(0.0, 0.0, 0.0);
			caster.hurtMarked = true;
		}
		caster.resetFallDistance();
		mark = groundTarget(caster);
		if (Ticks.ambient()) {
			Fx.ring(level, mark, RADIUS, 40, 0.06, Fx.dust(SKY, 1.3F));
			Fx.ring(level, mark, RADIUS * 0.55, 22, 0.06, Fx.dust(FIRE, 0.9F));
			Fx.particleExcept(level, caster, ParticleTypes.CLOUD, caster.getX(), caster.getY() + 0.1, caster.getZ(), 4, 0.4, 0.1, 0.4, 0.03);
		}
		if (t > rise && Ticks.every(t - rise, PULSE)) {
			pulse(level);
		}
		if (t >= Ticks.of(LENGTH)) {
			end();
			return false;
		}
		return true;
	}

	/** 포탄 한 무리 — 조준원 안에 흩뿌려 터뜨리고, 원 안의 적 전원에게 같은 피해. */
	private void pulse(ServerLevel level) {
		for (int i = 0; i < 4; i++) {
			double a = caster.getRandom().nextDouble() * Math.PI * 2;
			double r = RADIUS * Math.sqrt(caster.getRandom().nextDouble());
			Vec3 at = mark.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
			Fx.particle(level, ParticleTypes.EXPLOSION, at, 1, 0, 0, 0, 0);
			Fx.particle(level, Fx.dust(FIRE, 1.4F), at, 12, 0.5, 0.3, 0.5, 0);
			Fx.particle(level, ParticleTypes.CLOUD, at, 10, 0.5, 0.2, 0.5, 0.06);
		}
		Fx.sound(level, mark.x, mark.y, mark.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.3F, 1.2F);
		Fx.sound(caster, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.8F);
		List<LivingEntity> list = Targets.enemies(level, mark, RADIUS, caster);
		for (LivingEntity e : list) {
			SkillDamage.dealFine(e, caster, PULSE_DAMAGE_100, SkillDamage.Kind.MULTI_NO_KB);
		}
	}

	/** 조준선이 닿는 곳 아래의 땅 — 조준원 한가운데. */
	static Vec3 groundTarget(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 dir = Aim.direction(p);
		Vec3 far = eye.add(dir.scale(AIM_RANGE));
		HitResult clip = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 at = clip.getType() == HitResult.Type.MISS ? far : clip.getLocation();
		// 맞은 곳이 공중이면 그 아래 땅까지 한 번 더
		HitResult down = p.level().clip(new ClipContext(at, at.subtract(0, AIM_RANGE, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return down.getType() == HitResult.Type.MISS ? at : down.getLocation();
	}

	private void end() {
		if (state.bombardment == this) {
			state.bombardment = null;
		}
		Attachments.combatant(caster).ccImmune = false;
		SkillAnimPayload.stop(caster, SkillAnimPayload.GS_ULT);
		caster.resetFallDistance();
	}

	@Override
	public void cancel() {
		end();
	}

	@Override
	public Object owner() {
		return caster;
	}
}
