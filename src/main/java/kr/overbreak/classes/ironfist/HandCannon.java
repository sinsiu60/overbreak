package kr.overbreak.classes.ironfist;

import kr.overbreak.core.tick.Ticks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import kr.overbreak.combat.Aim;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.HitPayload;
import kr.overbreak.net.InputModePayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Local;
import kr.overbreak.util.Tracer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * [좌클릭] 철권포 — 데이터팩 skill/ironfist/blast · pellet 대응 (체력 10배 기준).
 *
 *   왼손에서 탄환 11개를 실제로 쏩니다 (눈 → 조준 방향 레이캐스트, 벽에 막힘). 발당 3.5, 사거리 5칸, 넉백 없음
 *   탄퍼짐은 항상 같은 모양입니다 (데이터팩은 매 발 무작위). 좌우로 넓게 퍼진 11발 — PATTERN
 *   피해는 대상마다 맞은 탄환을 모두 더해 한 번에 넣습니다 (11발 중 9발 = 31.5 한 번). 머리에 맞은 탄환은 치명타 2배
 *   초당 3발. 탄창 4발 — 마지막 발사 0.7초(14틱) 뒤부터 0.7초마다 한 발씩 저절로 참 (쏘면 다시 0.7초부터)
 */
public final class HandCannon {
	public static final int MAG = 4;
	public static final int RELOAD = 14;
	/** 발사 간격을 1/20 초 단위로 셉니다: 한 발 = 20, 틱당 SHOTS_PER_SECOND 씩 줄어 초당 정확히 3발 (7 · 7 · 6틱). */
	public static final int SHOT_UNITS = 20;
	public static final int SHOTS_PER_SECOND = 3;
	public static final int DAMAGE_100 = 350;
	public static final double RANGE = 5.0;
	public static final int CRIT_MULTIPLIER = 2;

	/**
	 * 고정 탄퍼짐 {좌우(도, + = 오른쪽), 위아래(도, + = 위)} — 매번 같은 모양입니다 (데이터팩은 발마다 무작위 ±7.6도).
	 * 좌우로 넓은 모양이라, 사람 크기 표적에 거리마다 맞는 발 수가 데이터팩 무작위의 평균과 비슷합니다
	 * (1칸 11발 · 2칸 약 9발 · 3칸 약 7발 · 4칸 약 4발 · 5칸 약 4발).
	 */
	public static final double[][] PATTERN = {
			{0.0, 0.0},
			{0.0, -4.0}, {0.0, 6.5},
			{-3.0, 1.5}, {3.0, 1.5},
			{-5.5, -3.0}, {5.5, -3.0},
			{-8.5, 2.0}, {8.5, 2.0},
			{-12.0, -1.0}, {12.0, -1.0}};

	private HandCannon() {}

	static void fire(ServerPlayer p, IronFistState st) {
		if (st.fireCd > 0) {
			return;
		}
		if (st.ammo <= 0) {
			dry(p);
			return;
		}
		// 남은 몫(0 이하)을 이어 받아 7 · 7 · 6틱 간격으로 초당 정확히 3발
		// 1초 = 틱레이트만큼의 틱 x 틱마다 3 → 한 발 몫은 틱레이트 (20틱 20, 60틱 60)
		st.fireCd += kr.overbreak.core.tick.TickRateConfig.tickRate();
		st.ammo--;
		st.reloadT = Ticks.of(RELOAD);

		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		float[] yp = Local.yawPitch(Aim.direction(p));
		// 왼손 총구 (^x 는 왼쪽이 양수)
		Vec3 muzzle = Local.offset(eye, yp[0], yp[1], 0.34, -0.28, 0.6);
		Predicate<Entity> target = e -> e != p && e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand);
		// 대상별 맞은 탄환 {몸통, 머리}
		Map<LivingEntity, int[]> hits = new LinkedHashMap<>();

		for (double[] off : PATTERN) {
			// yaw + = 오른쪽으로, pitch + = 아래로
			Vec3 dir = Vec3.directionFromRotation((float) (yp[1] - off[1]), (float) (yp[0] + off[0]));
			Vec3 end = eye.add(dir.scale(RANGE));
			BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			if (block.getType() != HitResult.Type.MISS) {
				end = block.getLocation();
			}
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0), target, eye.distanceToSqr(end));
			if (hit != null && hit.getEntity() instanceof LivingEntity victim) {
				end = hit.getLocation();
				hits.computeIfAbsent(victim, k -> new int[2])[headshot(victim, end) ? 1 : 0]++;
			}
			Tracer.spawn(level, p, muzzle, end, Tracer.IRONFIST);
		}

		// 대상마다 한 번: (몸통 + 머리 x 2) x 3.5
		for (Map.Entry<LivingEntity, int[]> e : hits.entrySet()) {
			int[] n = e.getValue();
			boolean crit = n[1] > 0;
			HitPayload.critNext = crit;
			try {
				SkillDamage.dealFine(e.getKey(), p, (n[0] + n[1] * CRIT_MULTIPLIER) * DAMAGE_100, SkillDamage.Kind.MULTI_NO_KB);
			} finally {
				HitPayload.critNext = false;
			}
			if (crit) {
				Fx.sound(e.getKey(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 1.3F);
			}
		}

		SkillAnimPayload.broadcast(p, SkillAnimPayload.IF_SHOT, -1);
		Fx.sound(p, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 1.8F);
		Fx.sound(p, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.9F);
		if (!InputModePayload.canSend(p)) {
			ammoHud(p, st);
		}
	}

	private static void dry(ServerPlayer p) {
		Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.5F, 0.5F);
		if (!InputModePayload.canSend(p)) {
			ammoHud(p, IronFist.state(p));
		}
	}

	static void ammoHud(ServerPlayer p, IronFistState st) {
		Attachments.profile(p).msgT = 20;
		String full = "● ".repeat(Math.max(0, st.ammo));
		String empty = "○ ".repeat(Math.max(0, MAG - st.ammo));
		Hud.actionbar(p, Component.empty()
				.append(Hud.bold("철권포  ", ChatFormatting.GRAY))
				.append(Hud.bold(full, ChatFormatting.WHITE))
				.append(Hud.bold(empty, ChatFormatting.DARK_GRAY)));
	}

	/**
	 * 머리 판정 — 눈높이에서 머리 크기만큼 아래부터 위 끝까지.
	 * 플레이어는 머리 0.5칸 중 눈 아래가 약 0.32칸이라, 키에 비례해 같은 비율로 봅니다 (웅크리면 같이 낮아짐).
	 */
	public static boolean headshot(LivingEntity e, Vec3 hit) {
		return kr.overbreak.combat.Hitscan.headshot(e, hit);
	}

	/** 매 틱: 발사 간격 · 한 발씩 재장전. */
	static void tick(ServerPlayer p, IronFistState st) {
		// 쉬는 동안 몫이 쌓여 연사가 몰리지 않게, 1틱 미만만 남깁니다
		st.fireCd = Math.max(st.fireCd - SHOTS_PER_SECOND, -(SHOTS_PER_SECOND - 1));
		if (st.reloadT > 0) {
			st.reloadT--;
		}
		if (st.reloadT <= 0) {
			st.reloadT = Ticks.of(RELOAD);
			if (st.ammo < MAG) {
				st.ammo++;
				Fx.sound(p, SoundEvents.NOTE_BLOCK_HAT, SoundSource.PLAYERS, 0.4F, 1.9F);
			}
		}
	}
}
