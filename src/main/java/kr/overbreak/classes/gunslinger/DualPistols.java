package kr.overbreak.classes.gunslinger;

import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Hitscan;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.HitPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Local;
import kr.overbreak.util.Tracer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * [좌클릭] 쌍권총 연사 — 건슬링어의 평타.
 *
 *   16칸 히트스캔 · 발당 20 · 0.2초에 한 발. 오른손 총구 → 왼손 총구를 번갈아 씁니다
 *   공중에서 맞히면 무조건 치명타 150% (30) — 그리고 반동 도약 · 사선 앵커 쿨타임이 0.5초씩 깎입니다
 *   탄창 18발 · R 또는 다 쓰면 1.25초 재장전 (재장전 중에는 다른 행동이 잠깁니다)
 */
public final class DualPistols {
	static final double RANGE = 16.0;
	/** 발당 피해 x100 (20.0). */
	static final int DAMAGE_100 = 2000;
	/** 사격 간격 (시간 단위 · 0.2초). */
	static final int GAP = 4;
	public static final int MAG = 18;
	/** 재장전 (시간 단위 · 1.25초 = 25틱 @20). */
	public static final int RELOAD = 25;

	private DualPistols() {}

	static void fire(ServerPlayer p, GunslingerState st) {
		if (st.shotCd > 0 || st.inUlt()) {
			return;
		}
		if (st.reloadT > 0) {
			denied(p);
			return;
		}
		if (st.ammo <= 0) {
			startReload(p, st);
			return;
		}
		shoot(p, st, Aim.direction(p), DAMAGE_100);
		st.shotCd = Ticks.of(GAP);
		st.ammo--;
		if (st.ammo <= 0) {
			startReload(p, st);
		}
	}

	/**
	 * 한 발 (총구 · 예광탄 · 공중 치명타).
	 *
	 * 쏜 반동으로 밀려나지는 않습니다 — 조준선이 흔들려 평타가 제자리에 꽂히지 않았습니다 (0.2d).
	 * 뒤로 날아가는 것은 반동 도약(RMB)이 맡습니다.
	 *
	 * @return 맞은 대상 (없으면 null)
	 */
	static LivingEntity shoot(ServerPlayer p, GunslingerState st, Vec3 dir, int damage100) {
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		Hitscan.Hit hit = Hitscan.cast(p, eye, dir, RANGE);
		float[] yp = Local.yawPitch(dir);
		// 총구는 번갈아 — 오른쪽 총, 왼쪽 총
		boolean left = st.leftMuzzle;
		double side = left ? 0.30 : -0.30;
		st.leftMuzzle = !st.leftMuzzle;
		Vec3 muzzle = Local.offset(eye, yp[0], yp[1], side, -0.20, 0.42);
		Tracer.spawn(level, p, muzzle, hit.end(), left ? Tracer.GUNSLINGER_L : Tracer.GUNSLINGER);
		// 쓴 쪽 손만 반동이 나가도록 번호를 나눕니다 (1인칭 · 3인칭 공통)
		SkillAnimPayload.broadcast(p, left ? SkillAnimPayload.GS_SHOT_L : SkillAnimPayload.GS_SHOT, -1);
		Fx.sound(p, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.34F, 2.0F);
		Fx.sound(p, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.45F, 1.4F);
		Fx.particleExcept(level, p, ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.04, 0.04, 0.04, 0.01);

		boolean air = AeroDrift.airborne(p);
		LivingEntity victim = hit.target();
		if (victim != null) {
			int damage = air ? damage100 * AeroDrift.CRIT_PERCENT / 100 : damage100;
			HitPayload.critNext = air;
			try {
				// 0.2초에 한 발 — 바닐라 무적 시간(0.5초)을 관통하지 않으면 절반이 씩힙니다
				SkillDamage.dealFine(victim, p, damage, SkillDamage.Kind.MULTI_NO_KB);
			} finally {
				HitPayload.critNext = false;
			}
			Fx.particle(level, ParticleTypes.CRIT, victim.getX(), victim.getY() + 1, victim.getZ(), air ? 12 : 6, 0.25, 0.35, 0.25, 0.2);
			Fx.sound(victim, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.9F, air ? 1.6F : 1.2F);
			if (air) {
				AeroDrift.onAirHit(p);
			}
		}
		return victim;
	}

	/** 직업 틱 — 사격 간격 · 재장전. */
	static void tick(ServerPlayer p, GunslingerState st) {
		if (st.shotCd > 0) {
			st.shotCd--;
		}
		if (st.reloadT > 0) {
			st.reloadT--;
			int elapsed = Ticks.of(RELOAD) - st.reloadT;
			int time = Ticks.toTime(elapsed);
			if (Ticks.of(time) == elapsed) {
				reloadSound(p, time);
			}
			if (st.reloadT == 0) {
				st.ammo = MAG;
				Attachments.combatant(p).casting = false;
			}
		}
	}

	/** R 키 — 이미 재장전 중 · 가득 참 · 곡예 난사 · 궁극기 · 기절 중이면 무시. */
	static void manualReload(ServerPlayer p, GunslingerState st) {
		if (st.reloadT > 0 || st.ammo >= MAG || st.scatter != null || st.inUlt() || Attachments.combatant(p).hardCc()) {
			return;
		}
		startReload(p, st);
	}

	/** 재장전 시작 — 1.25초 동안 사격 · 스킬이 잠깁니다 (정신집중). */
	static void startReload(ServerPlayer p, GunslingerState st) {
		if (st.reloadT > 0) {
			return;
		}
		st.reloadT = Ticks.of(RELOAD);
		Attachments.combatant(p).casting = true;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.GS_RELOAD, -1);
	}

	/** 재장전이 끊겼을 때 (사망 · 직업 해제) — 잠금만 풀고 탄창은 그대로. */
	static void cancelReload(ServerPlayer p, GunslingerState st) {
		if (st.reloadT > 0) {
			st.reloadT = 0;
			Attachments.combatant(p).casting = false;
			SkillAnimPayload.stop(p, SkillAnimPayload.GS_RELOAD);
		}
	}

	static void denied(ServerPlayer p) {
		Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5F, 0.5F);
	}

	/**
	 * 공중 재장전 박자 (애니메이션과 같은 프레임):
	 *   0  탄창 두 개를 위로 튕겨 올림 (금속 배출음)
	 *   10~15 총을 돌리는 바람 소리
	 *   18 떨어지는 탄창을 받아 끼움 — 묵직한 "철컥"
	 *   22 손 반동 (마무리)
	 */
	private static void reloadSound(ServerPlayer p, int t) {
		switch (t) {
			case 0 -> {
				Fx.sound(p, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.7F, 1.9F);
				Fx.sound(p, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.5F, 2.0F);
			}
			case 10 -> Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.8F);
			case 13 -> Fx.sound(p, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.4F, 2.0F);
			case 15 -> Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4F, 2.0F);
			case 18 -> {
				Fx.sound(p, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.1F, 1.4F);
				Fx.sound(p, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.5F, 1.9F);
				Fx.sound(p, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.8F, 1.7F);
				// 총구에서 튀는 하얀 불꽃 — 탄창이 맞물리는 순간
				net.minecraft.world.phys.Vec3 hands = p.getEyePosition().add(p.getLookAngle().scale(0.5)).subtract(0, 0.25, 0);
				Fx.particle(p.level(), net.minecraft.core.particles.ParticleTypes.END_ROD, hands, 10, 0.22, 0.16, 0.22, 0.04);
				Fx.particle(p.level(), Fx.dust(Fx.rgb(1.0, 1.0, 1.0), 0.9F), hands, 14, 0.25, 0.2, 0.25, 0);
			}
			case 22 -> Fx.sound(p, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.5F, 1.6F);
			default -> {
			}
		}
	}
}
