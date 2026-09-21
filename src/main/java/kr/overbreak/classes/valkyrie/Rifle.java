package kr.overbreak.classes.valkyrie;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Hitscan;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.HitPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Local;
import kr.overbreak.util.Targets;
import kr.overbreak.util.Tracer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [좌클릭 누르고 있기] 연사 — 데이터팩 skill/valkyrie/* 대응 (체력 10배 기준).
 *
 *   20칸 히트스캔, 발당 7 (머리에 맞으면 약한 치명타 10.5), 넉백 없음
 *   간격: 평소 4틱(초당 5발) / 차원 도약으로 뜬 동안 3 · 3 · 2 (평균 2.67틱 = 정확히 1.5배)
 *   탄퍼짐: 붙잡은 지 1초까지 0 → 1~2초에 걸쳐 최대 2.85도 → 고정. 손을 떼면 즉시 0
 *   탄창 50발 — 다 쓰면 저절로, R 키로 언제든 1.5초 재장전 (데이터팩에는 탄창이 없음, 요청으로 추가)
 *     소리는 재장전 동작(valkyrie.reload)과 같은 박자: 걸쇠 → 탄창 빠짐 → 새 탄창 → 끼움 → 장전 손잡이 당김 · 놓음
 *   미사일: 명중 5번마다 그 자리 반경 1.5칸에 7.5 (데이터팩 10번마다 15 에서 너프). 3초간 못 맞히면 세던 것이 풀림
 *
 * 데이터팩은 좌클릭 홀드를 받을 수 없어 우클릭 홀드로 쐈지만, 모드 클라이언트는 좌클릭을 누르고 있는 동안
 * 매 틱 알려 주므로 좌클릭으로 옮겼습니다. 연사 간격은 서버가 정합니다.
 */
public final class Rifle {
	public static final double RANGE = 20.0;
	public static final int DAMAGE_100 = 700;
	/** 1초에 나가는 발수 — 땅 8발, 공중(차원 도약) 10발 (0.1a). */
	public static final int SHOTS_PER_SECOND = 8;
	/** 차원 도약 중 공격속도 +25% (0.1a — 1.5배에서 너프). */
	public static final int BOOSTED_SHOTS_PER_SECOND = 10;
	/** 머리 치명타 계수 (%) — 약한 치명타 (0.1a). */
	public static final int HEADSHOT_PERCENT = 150;
	public static final int SPREAD_START = 20;
	public static final int SPREAD_RAMP = 20;
	public static final double SPREAD_MAX = 2.85;
	public static final int MAG = 50;
	public static final int RELOAD = 30;
	public static final int MISSILE_EVERY = 5;
	public static final int MISSILE_WINDOW = 60;
	public static final double MISSILE_RADIUS = 1.5;
	public static final int MISSILE_100 = 750;
	static final int ORANGE = Fx.rgb(1.00, 0.55, 0.20);

	private Rifle() {}

	/** 직업 틱 — 재장전, 그리고 이번 틱에 누르고 있었으면 간격에 맞춰 한 발. */
	static void tick(ServerPlayer p, ValkyrieState st) {
		// 한 발 값은 틱레이트, 틱마다 초당 발수만큼 깎음 → 틱레이트와 무관하게 정확한 연사 속도
		int perTick = st.boosted ? BOOSTED_SHOTS_PER_SECOND : SHOTS_PER_SECOND;
		st.fireCd = Math.max(st.fireCd - perTick, -(perTick - 1));
		if (st.missileN > 0 && --st.missileT <= 0) {
			st.missileN = 0;
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
		boolean held = st.trigger;
		st.trigger = false;
		if (!held || st.barrage != null) {
			st.holdT = 0;
			return;
		}
		st.holdT++;
		if (st.fireCd > 0 || st.reloadT > 0 || st.ammo <= 0 || Attachments.combatant(p).hardCc()) {
			return;
		}
		st.fireCd += kr.overbreak.core.tick.TickRateConfig.tickRate();
		st.seq = st.boosted ? st.seq + 1 : 0;
		st.ammo--;
		LivingEntity hit = shoot(p, spread(Ticks.time(st.holdT)), DAMAGE_100);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.VK_SHOT, -1);
		if (hit != null) {
			countMissile(p, st, hit);
		}
		if (st.ammo <= 0) {
			startReload(p, st);
		}
	}

	/** R 키 — 이미 재장전 중 · 가득 참 · 탄막 포격 · 과열 분사 · 기절 중이면 무시. */
	static void manualReload(ServerPlayer p, ValkyrieState st) {
		if (st.reloadT > 0 || st.ammo >= MAG || st.barrage != null || st.overheat != null || Attachments.combatant(p).hardCc()) {
			return;
		}
		startReload(p, st);
	}

	/** 스킬이 나가는 순간 재장전을 끊습니다 (0.2e) — 탄창은 그대로. */
	static void interrupt(ServerPlayer p, ValkyrieState st) {
		if (st.reloadT > 0) {
			st.reloadT = 0;
			SkillAnimPayload.stop(p, SkillAnimPayload.VK_RELOAD);
		}
	}

	private static void startReload(ServerPlayer p, ValkyrieState st) {
		st.reloadT = Ticks.of(RELOAD);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.VK_RELOAD, -1);
	}

	/** 재장전 시작 후 틱 → 소리. 바닐라 금속 소리를 겹쳐 총 부품 소리처럼 만듭니다. */
	private static void reloadSound(ServerPlayer p, int t) {
		switch (t) {
			case 3 -> { // 탄창 걸쇠 누름
				Fx.sound(p, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.6F, 1.9F);
				Fx.sound(p, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.35F, 2.0F);
			}
			case 6 -> { // 탄창이 미끄러져 빠짐
				Fx.sound(p, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.8F, 1.45F);
				Fx.sound(p, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.45F, 1.6F);
			}
			case 13 -> // 허리에서 새 탄창을 꺼냄
				Fx.sound(p, SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.6F, 1.25F);
			case 19 -> { // 새 탄창 끼움 — 딸깍
				Fx.sound(p, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.7F, 1.75F);
				Fx.sound(p, SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 0.3F, 2.0F);
				Fx.sound(p, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.7F, 2.0F);
			}
			case 24 -> { // 장전 손잡이 당김
				Fx.sound(p, SoundEvents.CROSSBOW_QUICK_CHARGE_1, SoundSource.PLAYERS, 0.8F, 1.5F);
				Fx.sound(p, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.25F, 1.9F);
			}
			case 25 -> { // 손잡이를 놓아 노리쇠가 닫힘 — 철컥
				Fx.sound(p, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.9F, 1.3F);
				Fx.sound(p, SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.6F, 1.6F);
				Fx.sound(p, SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.5F, 1.4F);
			}
			default -> {
			}
		}
	}

	/** 붙잡은 틱 → 탄퍼짐 최대 각도 (도). */
	/** @param holdT 누르고 있던 시간 (1/20초 단위, 연속) */
	public static double spread(double holdT) {
		if (holdT <= SPREAD_START) {
			return 0.0;
		}
		return SPREAD_MAX * Math.min(SPREAD_RAMP, holdT - SPREAD_START) / SPREAD_RAMP;
	}

	/**
	 * 오른손 총구에서 한 발 (연사 · 탄막 포격 공용). 머리에 맞으면 치명타 2배.
	 * @return 맞은 대상 (없으면 null)
	 */
	static @Nullable LivingEntity shoot(ServerPlayer p, double spreadDeg, int damage100) {
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 dir = Aim.direction(p);
		if (spreadDeg > 0.0) {
			dir = Hitscan.turn(dir, (p.getRandom().nextDouble() * 2.0 - 1.0) * spreadDeg, (p.getRandom().nextDouble() * 2.0 - 1.0) * spreadDeg);
		}
		Hitscan.Hit hit = Hitscan.cast(p, eye, dir, RANGE);
		float[] yp = Local.yawPitch(dir);
		// 오른손 총구 (^x 는 왼쪽이 양수라 음수) — 레이저는 탄퍼짐으로 튼 방향 그대로
		Vec3 muzzle = Local.offset(eye, yp[0], yp[1], -0.28, -0.22, 0.2);
		Tracer.spawn(level, p, muzzle, hit.end(), Tracer.VALKYRIE);
		Fx.sound(p, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 2.0F);
		LivingEntity victim = hit.target();
		if (victim == null) {
			return null;
		}
		HitPayload.critNext = hit.head();
		try {
			SkillDamage.dealFine(victim, p, damage100 * (hit.head() ? HEADSHOT_PERCENT : 100) / 100, SkillDamage.Kind.MULTI_NO_KB);
		} finally {
			HitPayload.critNext = false;
		}
		Fx.particle(level, ParticleTypes.CRIT, hit.end().x, hit.end().y, hit.end().z, 5, 0.1, 0.1, 0.1, 0.2);
		Fx.sound(victim, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.7F, hit.head() ? 1.5F : 1.9F);
		return victim;
	}

	/** 명중만 셉니다. 다섯 번째에 그 자리에서 미사일이 터집니다. */
	private static void countMissile(ServerPlayer p, ValkyrieState st, LivingEntity hit) {
		st.missileN++;
		st.missileT = Ticks.of(MISSILE_WINDOW);
		if (st.missileN >= MISSILE_EVERY) {
			st.missileN = 0;
			missile(p, hit.position());
			return;
		}
		Fx.sound(p, SoundEvents.NOTE_BLOCK_HAT, SoundSource.PLAYERS, 0.35F + 0.1F * st.missileN, 1.3F + 0.15F * st.missileN);
	}

	private static void missile(ServerPlayer p, Vec3 at) {
		ServerLevel level = p.level();
		Fx.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.6F);
		Fx.sound(level, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8F, 1.8F);
		Fx.particle(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.8, at.z, 1, 0.2, 0.2, 0.2, 0);
		Fx.particle(level, Fx.dust(ORANGE, 1.4F), at.x, at.y + 0.8, at.z, 16, 0.5, 0.4, 0.5, 0);
		Fx.particle(level, ParticleTypes.FLAME, at.x, at.y + 0.8, at.z, 8, 0.35, 0.35, 0.35, 0.04);
		for (LivingEntity e : Targets.enemies(level, at, MISSILE_RADIUS, p)) {
			SkillDamage.dealFine(e, p, MISSILE_100, SkillDamage.Kind.MULTI_NO_KB);
		}
	}
}
