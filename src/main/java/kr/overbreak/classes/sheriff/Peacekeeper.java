package kr.overbreak.classes.sheriff;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Hitscan;
import kr.overbreak.combat.Motion;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
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
 * [좌클릭] 피스키퍼 — 데이터팩 skill/sheriff/peace/* + class/sheriff/reload_* 대응 (체력 10배 기준).
 *
 *   60칸 정밀 히트스캔 (탄퍼짐 없음), 발당 70 · 10칸부터 줄어 20칸 밖은 21. 맞은 적을 살짝 밀어냄
 *   머리에 맞으면 치명타 175% (가까이 122.5 · 20칸 밖 36.75) — 데이터팩에는 없음, 요청으로 추가
 *   0.8초에 한 발 (16틱). 탄창 6발 — 다 쓰면 저절로, R 키로 언제든 2초 재장전
 *   재장전 소리는 동작(sheriff.reload)과 같은 박자: 탄창 젖힘 → 탄피 털기 → 스피드로더 · 6발 → 탄창 닫힘 → 총 돌리기(드르르륵) → 탁
 */
final class Peacekeeper {
	static final double RANGE = 60.0;
	/** 이 거리부터 {@link #FALLOFF_TO} 까지 피해가 줄어 최대 {@link #FALLOFF_PERCENT}% 감소 (0.1f 에서 30 → 20칸). */
	static final double FALLOFF_FROM = 10.0;
	static final double FALLOFF_TO = 20.0;
	static final int FALLOFF_PERCENT = 70;
	static final int DAMAGE_100 = 7000;
	static final int GAP = 16;
	static final int MAG = 6;
	static final int RELOAD = 40;
	static final double KNOCK = 0.35;
	/** 헤드샷 계수 (%). */
	static final int HEADSHOT_PERCENT = 175;

	private Peacekeeper() {}

	static void fire(ServerPlayer p, SheriffState st) {
		if (st.shotCd > 0) {
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
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 dir = Aim.direction(p);
		Hitscan.Hit hit = Hitscan.cast(p, eye, dir, RANGE);
		float[] yp = Local.yawPitch(dir);
		Vec3 muzzle = Local.offset(eye, yp[0], yp[1], -0.28, -0.22, 0.4);
		Tracer.spawn(level, p, muzzle, hit.end(), Tracer.SHERIFF);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SH_SHOT, -1);
		Fx.sound(p, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.9F);
		Fx.sound(p, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.7F, 0.6F);
		// 쏜 사람 화면에서는 카메라 바로 앞이라 검은 덩어리로 보여 뺍니다 (모드 클라이언트는 총구 화염이 대신)
		Fx.particleExcept(level, p, ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.04, 0.04, 0.04, 0.01);

		LivingEntity victim = hit.target();
		if (victim != null) {
			double dist = eye.distanceTo(hit.end());
			boolean far = dist >= FALLOFF_FROM;
			int damage = damage100(dist) * (hit.head() ? HEADSHOT_PERCENT : 100) / 100;
			HitPayload.critNext = hit.head();
			try {
				SkillDamage.dealFine(victim, p, damage, SkillDamage.Kind.NO_KB);
			} finally {
				HitPayload.critNext = false;
			}
			knockAway(p, victim, KNOCK);
			Fx.particle(level, ParticleTypes.CRIT, victim.getX(), victim.getY() + 1, victim.getZ(), 12, 0.25, 0.35, 0.25, 0.25);
			Fx.sound(victim, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0F, hit.head() ? 1.5F : 1.2F);
			if (far) {
				// 먼 거리 명중은 소리가 한 번 더 얹힙니다
				Fx.sound(victim, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.8F);
			}
		}
		st.shotCd = Ticks.of(GAP);
		st.ammo--;
		if (st.ammo <= 0) {
			startReload(p, st);
		}
	}

	/** 거리별 피해 (x100) — 10칸까지 그대로, 20칸부터는 70% 감소로 고정. */
	static int damage100(double distance) {
		double k = Math.max(0.0, Math.min(1.0, (distance - FALLOFF_FROM) / (FALLOFF_TO - FALLOFF_FROM)));
		return (int) Math.round(DAMAGE_100 * (1.0 - k * FALLOFF_PERCENT / 100.0));
	}

	/** 시전자 반대쪽으로 수평으로 밀어냅니다 (데이터팩 util/kb/dir + pvp.kb_p). */
	static void knockAway(ServerPlayer caster, LivingEntity e, double power) {
		if (!e.isAlive()) {
			return;
		}
		double dx = e.getX() - caster.getX();
		double dz = e.getZ() - caster.getZ();
		if (dx * dx + dz * dz < 1.0E-4) {
			Vec3 look = Motion.flatLook(caster);
			dx = look.x;
			dz = look.z;
		}
		Motion.knock(e, dx, dz, power);
	}

	/** 직업 틱 — 사격 간격 · 재장전. */
	static void tick(ServerPlayer p, SheriffState st) {
		if (st.shotCd > 0) {
			st.shotCd--;
		}
		if (st.reloadT > 0) {
			st.reloadT--;
			// 소리 박자는 시간 단위 — 그 시간에 처음 닿는 틱에 한 번
			int elapsed = Ticks.of(RELOAD) - st.reloadT;
			int time = Ticks.toTime(elapsed);
			if (Ticks.of(time) == elapsed) {
				reloadSound(p, time);
			}
			if (st.reloadT == 0) {
				st.ammo = MAG;
			}
		}
	}

	/** R 키 — 이미 재장전 중 · 가득 참 · 난사 · 황야의 무법자 · 기절 중이면 무시. */
	static void manualReload(ServerPlayer p, SheriffState st) {
		if (st.reloadT > 0 || st.ammo >= MAG || st.fan != null || st.deadeye != null || Attachments.combatant(p).hardCc()) {
			return;
		}
		startReload(p, st);
	}

	static void startReload(ServerPlayer p, SheriffState st) {
		if (st.reloadT > 0) {
			return;
		}
		st.reloadT = Ticks.of(RELOAD);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SH_RELOAD, -1);
	}

	/** 재장전 중 사격 시도. */
	static void denied(ServerPlayer p) {
		Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5F, 0.5F);
	}

	/** 재장전 시작 후 틱 → 소리. 바닐라 금속 소리를 겹쳐 리볼버 부품 소리처럼 만듭니다. */
	private static void reloadSound(ServerPlayer p, int t) {
		switch (t) {
			case 3 -> { // 탄창을 옆으로 젖힘
				Fx.sound(p, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.6F, 1.8F);
				Fx.sound(p, SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6F, 1.7F);
			}
			case 7 -> { // 빈 탄피가 쏟아짐
				Fx.sound(p, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.5F, 1.9F);
				Fx.sound(p, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.4F, 2.0F);
			}
			case 17 -> { // 스피드로더로 6발을 한 번에
				Fx.sound(p, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.8F, 1.6F);
				Fx.sound(p, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.6F, 1.5F);
			}
			case 21 -> { // 탄창을 밀어 닫음
				Fx.sound(p, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.5F, 1.8F);
				Fx.sound(p, SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.4F, 1.9F);
			}
			case 22, 23, 24, 25, 26, 27, 28, 29 -> // 방아쇠울에 손가락을 걸고 총을 돌림 — 드르르르륵 (점점 느려지고 작아짐)
				Fx.sound(p, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.55F - (t - 22) * 0.05F, 2.0F - (t - 22) * 0.06F);
			case 31 -> { // 탁! — 다 돌린 총을 손에 딱 잡음
				Fx.sound(p, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.0F, 1.5F);
				Fx.sound(p, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.4F, 1.9F);
				Fx.sound(p, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.8F, 1.3F);
			}
			default -> {
			}
		}
	}
}
