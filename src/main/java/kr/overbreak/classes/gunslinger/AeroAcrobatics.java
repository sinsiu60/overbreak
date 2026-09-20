package kr.overbreak.classes.gunslinger;

import java.util.List;

import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.HitPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.net.ViewPayload;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * [웅크리기] 곡예 난사 — 제자리에서 한 바퀴 돌며 사방으로 퍼붓습니다 (0.2d).
 *
 *   0.8초(16틱) 동안 0.1초마다 한 번, 모두 여덟 번. 한 번마다 반경 8칸 안의 <b>모든</b> 적에게 15
 *   — 조준도 시야도 보지 않습니다. 끝까지 범위 안에 있으면 120 (공중이면 치명타로 180)
 *   도는 동안 무적 (i-frame) · 느린 낙하로 공중에 머무릅니다
 *   쓰는 동안 발밑에 반경 8칸 원이 깔려 사거리가 보이고, 시점이 3인칭으로 바뀌었다가 끝나면 원래대로 돌아옵니다
 *   탄창을 쓰지 않습니다. 쿨타임 9초
 */
public final class AeroAcrobatics implements Effects.Active {
	/** 쿨타임 (시간 단위 · 9초). */
	public static final int COOLDOWN = 180;
	/** 난사 횟수. */
	public static final int SHOTS = 8;
	/** 한 번 간격 (시간 단위). */
	static final int INTERVAL = 2;
	/** 도는 시간 (시간 단위 · 0.8초). */
	public static final int DURATION = 16;
	/** 사거리 (칸) — 이 안에 있으면 무조건 맞습니다. */
	public static final double RADIUS = 8.0;
	/** 한 번의 피해 x100 (15.0). */
	static final int DAMAGE_100 = 1500;
	/** 눈높이에서 총구까지 (칸). */
	private static final double MUZZLE = 0.5;
	private static final int SKY = Fx.rgb(0.70, 0.92, 1.00);

	private final ServerPlayer caster;
	private final GunslingerState state;
	private final float baseYaw;
	private int t;
	private int fired;

	private AeroAcrobatics(ServerPlayer caster, GunslingerState state) {
		this.caster = caster;
		this.state = state;
		this.baseYaw = caster.getYRot();
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
		// 제 몸이 도는 것을 봐야 하는 동작이라 도는 동안만 3인칭 (끝나면 쓰던 시점으로)
		ViewPayload.send(p, true);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Ticks.of(DURATION + 4), 0, false, false));
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.1F, 1.5F);
		Fx.sound(p, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.9F, 1.6F);
		a.ring();
		a.burst();
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
			burst();
		}
		if (Ticks.ambient()) {
			ring();
		}
		if (t >= Ticks.of(DURATION)) {
			end();
			return false;
		}
		return true;
	}

	/** 사거리를 알려 주는 발밑 원 (반경 8칸). */
	private void ring() {
		ServerLevel level = caster.level();
		Vec3 at = caster.position();
		Fx.ring(level, at, RADIUS, 56, 0.06, Fx.dust(SKY, 1.2F));
		Fx.ring(level, at, RADIUS * 0.5, 28, 0.06, Fx.dust(SKY, 0.7F));
	}

	/**
	 * 한 번의 난사 — 45도씩 돌아간 여덟 갈래로 예광탄을 뿌리고, 반경 안의 모든 적에게 피해.
	 * 예광탄은 보여 주기용이고 판정은 거리만 봅니다 (벽 뒤도 맞습니다).
	 */
	private void burst() {
		ServerLevel level = caster.level();
		Vec3 eye = caster.getEyePosition();
		float turn = baseYaw + fired * (360.0F / SHOTS);
		for (int i = 0; i < SHOTS; i++) {
			double a = Math.toRadians(turn + i * (360.0 / SHOTS));
			Vec3 dir = new Vec3(-Math.sin(a), -0.08, Math.cos(a)).normalize();
			Tracer.spawn(level, caster, eye.add(dir.scale(MUZZLE)), eye.add(dir.scale(RADIUS)), Tracer.GUNSLINGER);
		}
		fired++;
		// 한 발씩 쓰는 동작은 보내지 않습니다 — 그쪽이 더 나중에 시작한 재생이 돼
		// 곡예 난사의 몸 회전(ThirdPersonAnim)을 밀어내 캐릭터가 도는 모습이 꺼졌습니다.
		// 총구 불꽃 · 총소리는 예광탄과 아래 소리가 대신합니다.
		Fx.sound(caster, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.45F, 2.0F);
		Fx.particleExcept(level, caster, ParticleTypes.SMOKE, eye.x, eye.y, eye.z, 6, 0.4, 0.2, 0.4, 0.02);

		boolean air = AeroDrift.airborne(caster);
		int damage = air ? DAMAGE_100 * AeroDrift.CRIT_PERCENT / 100 : DAMAGE_100;
		List<LivingEntity> list = Targets.enemies(level, caster.position(), RADIUS, caster);
		for (LivingEntity e : list) {
			HitPayload.critNext = air;
			try {
				SkillDamage.dealFine(e, caster, damage, SkillDamage.Kind.MULTI_NO_KB);
			} finally {
				HitPayload.critNext = false;
			}
			Fx.particle(level, ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 6, 0.25, 0.35, 0.25, 0.2);
			Fx.sound(e, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.8F, 1.5F);
		}
		if (air && !list.isEmpty()) {
			AeroDrift.onAirHit(caster);
		}
	}

	private void end() {
		if (state.acrobatics == this) {
			state.acrobatics = null;
		}
		Attachments.combatant(caster).casting = false;
		SkillAnimPayload.stop(caster, SkillAnimPayload.GS_ACRO);
		if (!caster.hasDisconnected()) {
			ViewPayload.send(caster, false);
		}
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
