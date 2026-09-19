package kr.overbreak.classes.sheriff;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Hitscan;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Local;
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
 * [우클릭] 리볼버 난사 — 데이터팩 skill/sheriff/fan/* 대응 (체력 10배 기준).
 *
 *   0.6초(12틱) 동안 2틱마다 한 발, 6발. 8칸 히트스캔 · 발당 15 (전탄 90) · 넉백 없음
 *   탄퍼짐: n 번째 발(0부터)은 최대 n × 1.43도 — 첫 발은 정확, 여섯 번째 발 7.15도
 *   반동: 한 발마다 시야가 3도 튀고 좌우로 번갈아 1도 (클라이언트 Recoil, 60% 는 저절로 돌아옴) + 총이 크게 튐 (sheriff.fan)
 *   정신집중: 난사 중에는 평타 · 다른 스킬 잠김. 기절하면 남은 탄이 사라짐 (쿨타임은 그대로)
 *   재장전 중에는 쓸 수 없음. 탄창은 쓰지 않음. 쿨타임 6초 (전술 구르기로 즉시 초기화)
 */
final class FanHammer implements Effects.Active {
	/** 쿨타임 없이 탄창으로만 씁니다 (0.1 버전). */
	static final int COOLDOWN = 0;
	static final int SHOTS = 6;
	static final int INTERVAL = 2;
	static final int DURATION = 12;
	static final double RANGE = 8.0;
	static final int DAMAGE_100 = 3000;
	static final double SPREAD_STEP = 1.43;

	private final ServerPlayer caster;
	private final SheriffState state;
	private int t;
	private int fired;

	private FanHammer(ServerPlayer caster, SheriffState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, SheriffState st) {
		if (st.fan != null) {
			return;
		}
		if (st.reloadT > 0 || st.ammo <= 0) {
			Peacekeeper.denied(p);
			return;
		}
		Attachments.combatant(p).casting = true;
		FanHammer f = new FanHammer(p, st);
		st.fan = f;
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.1F, 1.4F);
		f.shot();
		Effects.add(f);
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected() || Attachments.combatant(caster).hardCc()) {
			end();
			return false;
		}
		t++;
		if (Ticks.every(t, INTERVAL) && fired < shots()) {
			shot();
		}
		if (t >= Ticks.of(DURATION)) {
			end();
			return false;
		}
		return true;
	}

	/** 남은 탄만큼만 나갑니다 (0.1 버전). */
	private int shots() {
		return Math.min(SHOTS, state.ammo + fired);
	}

	private void shot() {
		if (state.ammo <= 0) {
			return;
		}
		state.ammo--;
		ServerLevel level = caster.level();
		double spread = fired * SPREAD_STEP;
		Vec3 dir = Aim.direction(caster);
		if (spread > 0.0) {
			dir = Hitscan.turn(dir, (caster.getRandom().nextDouble() * 2.0 - 1.0) * spread, (caster.getRandom().nextDouble() * 2.0 - 1.0) * spread);
		}
		Vec3 eye = caster.getEyePosition();
		Hitscan.Hit hit = Hitscan.cast(caster, eye, dir, RANGE);
		float[] yp = Local.yawPitch(dir);
		Vec3 muzzle = Local.offset(eye, yp[0], yp[1], -0.28, -0.22, 0.4);
		Tracer.spawn(level, caster, muzzle, hit.end(), Tracer.SHERIFF);
		SkillAnimPayload.broadcast(caster, SkillAnimPayload.SH_FAN, -1);
		Fx.sound(caster, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35F, 2.0F);
		Fx.particleExcept(level, caster, ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.01);
		fired++;
		LivingEntity victim = hit.target();
		if (victim != null) {
			SkillDamage.dealFine(victim, caster, DAMAGE_100, SkillDamage.Kind.MULTI_NO_KB);
			Fx.particle(level, ParticleTypes.CRIT, victim.getX(), victim.getY() + 1, victim.getZ(), 5, 0.2, 0.3, 0.2, 0.2);
			Fx.sound(victim, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.7F, 1.6F);
		}
	}

	private void end() {
		if (state.ammo <= 0) {
			Peacekeeper.startReload(caster, state);
		}
		if (state.fan == this) {
			state.fan = null;
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
