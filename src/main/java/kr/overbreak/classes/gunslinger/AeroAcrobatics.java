package kr.overbreak.classes.gunslinger;

import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Hitscan;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * [웅크리기 + 우클릭] 곡예 난사 — 몸을 한 바퀴 돌리며 사방으로 여덟 발.
 *
 *   0.8초(16틱) 동안 2틱마다 한 발, 모두 여덟 발. 한 발마다 45도씩 돌아 360도를 채웁니다
 *   사거리 8칸 · 발당 15 — 붙어 있는 적은 여러 발을 맞아 최대 120
 *   도는 동안 무적 (i-frame) · 느린 낙하로 공중에 머무릅니다
 *   탄창을 쓰지 않습니다. 쿨타임 9초
 *
 * 웅크리기만 누르면 체공 훈풍(활공)이라, 이 스킬은 웅크린 채 우클릭해야 나갑니다.
 */
public final class AeroAcrobatics implements Effects.Active {
	/** 쿨타임 (시간 단위 · 9초). */
	public static final int COOLDOWN = 180;
	public static final int SHOTS = 8;
	/** 한 발 간격 (시간 단위). */
	static final int INTERVAL = 2;
	/** 도는 시간 (시간 단위 · 0.8초). */
	public static final int DURATION = 16;
	static final double RANGE = 8.0;
	/** 발당 피해 x100 (15.0). */
	static final int DAMAGE_100 = 1500;
	private static final int SKY = Fx.rgb(0.70, 0.92, 1.00);

	private final ServerPlayer caster;
	private final GunslingerState state;
	private final float baseYaw;
	private final float basePitch;
	private int t;
	private int fired;

	private AeroAcrobatics(ServerPlayer caster, GunslingerState state) {
		this.caster = caster;
		this.state = state;
		float[] yp = Aim.yawPitch(caster);
		this.baseYaw = yp[0];
		// 사방으로 뿌리는 난사라 위아래는 살짝만 따라갑니다
		this.basePitch = yp[1] * 0.4F;
	}

	static void cast(ServerPlayer p, GunslingerState st) {
		if (st.acrobatics != null || st.inUlt()) {
			return;
		}
		if (st.reloadT > 0) {
			DualPistols.denied(p);
			return;
		}
		if (Cooldowns.blocked(p, Gunslinger.ACRO, "곡예 난사", ChatFormatting.AQUA)) {
			return;
		}
		Attachments.profile(p).setCooldown(Gunslinger.ACRO, COOLDOWN);
		Attachments.combatant(p).casting = true;
		AeroAcrobatics a = new AeroAcrobatics(p, st);
		st.acrobatics = a;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.GS_ACRO, -1, Ticks.of(DURATION));
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Ticks.of(DURATION + 4), 0, false, false));
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.1F, 1.5F);
		Fx.sound(p, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.9F, 1.6F);
		a.shot();
		Effects.add(a);
	}

	/** 무적 프레임이 도는 중인가. */
	public boolean spinning() {
		return t < Ticks.of(DURATION);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			end();
			return false;
		}
		t++;
		if (Ticks.every(t, INTERVAL) && fired < SHOTS) {
			shot();
		}
		if (Ticks.ambient()) {
			ServerLevel level = caster.level();
			Fx.ring(level, caster.position().add(0, 1.0, 0), 1.1, 10, 0.0, Fx.dust(SKY, 0.9F));
		}
		if (t >= Ticks.of(DURATION)) {
			end();
			return false;
		}
		return true;
	}

	/** 45도씩 돌며 한 발. 첫 발은 조준한 쪽입니다. */
	private void shot() {
		double turn = fired * (360.0 / SHOTS);
		float yaw = baseYaw + (float) turn;
		Vec3 dir = dirOf(yaw, basePitch);
		LivingEntity victim = fireOne(dir);
		fired++;
		if (victim == null) {
			return;
		}
		Fx.sound(victim, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.8F, 1.5F);
	}

	private LivingEntity fireOne(Vec3 dir) {
		ServerLevel level = caster.level();
		Vec3 eye = caster.getEyePosition();
		Hitscan.Hit hit = Hitscan.cast(caster, eye, dir, RANGE);
		kr.overbreak.util.Tracer.spawn(level, caster, eye.add(dir.scale(0.45)), hit.end(), kr.overbreak.util.Tracer.SHERIFF);
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.GS_SHOT, -1);
		Fx.sound(caster, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.3F, 2.0F);
		Fx.particle(level, ParticleTypes.SMOKE, hit.end(), 2, 0.05, 0.05, 0.05, 0.01);
		LivingEntity victim = hit.target();
		if (victim != null) {
			boolean air = AeroDrift.airborne(caster);
			int damage = air ? DAMAGE_100 * AeroDrift.CRIT_PERCENT / 100 : DAMAGE_100;
			kr.overbreak.net.HitPayload.critNext = air;
			try {
				kr.overbreak.combat.SkillDamage.dealFine(victim, caster, damage, kr.overbreak.combat.SkillDamage.Kind.MULTI_NO_KB);
			} finally {
				kr.overbreak.net.HitPayload.critNext = false;
			}
			Fx.particle(level, ParticleTypes.CRIT, victim.getX(), victim.getY() + 1, victim.getZ(), 5, 0.2, 0.3, 0.2, 0.2);
			if (air) {
				AeroDrift.onAirHit(caster);
			}
		}
		return victim;
	}

	/** {yaw, pitch} → 단위 벡터 (바닐라 시선 계산과 같은 식). */
	private static Vec3 dirOf(float yaw, float pitch) {
		double y = Math.toRadians(yaw);
		double x = Math.toRadians(pitch);
		double cos = Math.cos(x);
		return new Vec3(-Math.sin(y) * cos, -Math.sin(x), Math.cos(y) * cos);
	}

	private void end() {
		if (state.acrobatics == this) {
			state.acrobatics = null;
		}
		Attachments.combatant(caster).casting = false;
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
