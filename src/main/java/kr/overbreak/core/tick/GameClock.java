package kr.overbreak.core.tick;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.entity.LivingEntity;

/**
 * 게임 틱 시계 — 서버가 60틱으로 돌아도 게임 로직(직업 스킬 · CC · 쿨타임 · 게이지 · HUD)은 1초에 20번 돌립니다.
 *
 * 왜: 직업 코드의 지속시간 · 쿨타임 · 돌진 길이는 20틱 기준 숫자이고, 플레이어 이동은 클라이언트(1초 20틱)가 계산합니다.
 * 서버 60틱의 이득(입력을 17ms 안에 처리 · 되감기 판정용 위치 기록)은 챙기면서, 스킬은 지금과 같은 시간 · 거리로 유지합니다.
 * 스킬을 초 단위(TickRateConfig)로 옮기면 이 시계 없이 서버 틱마다 돌릴 수 있습니다 (클라이언트 60틱 단계).
 *
 *   {@link #advance()}  서버 틱마다 한 번 — 이번 서버 틱에 게임 틱이 도는가
 *   {@link #now()}      게임 틱 번호 (입력 창 · 조준 유효 시간 같은 20틱 기준 비교에 씀, level.getGameTime() 대신)
 */
public final class GameClock {
	private static long ticks;
	private static double acc;
	private static boolean unit = true;
	/** 바닐라 무적 시간(invulnerableTime)이 서버 틱마다 줄어드는 것을 게임 틱 기준으로 되돌릴 생명체. */
	private static final Set<LivingEntity> HURT = Collections.newSetFromMap(new WeakHashMap<>());

	private GameClock() {}

	public static boolean advance() {
		acc += TickRateConfig.VANILLA / (double) TickRateConfig.tickRate();
		if (acc >= 1.0 - 1.0E-9) {
			acc -= 1.0;
			ticks++;
			unit = true;
			return true;
		}
		unit = false;
		return false;
	}

	/**
	 * 이번 서버 틱이 시간 단위(1/20초) 경계인가 — 매 틱 뿌리던 연출 파티클을 이걸로 거르면 60틱에서도 20틱과 같은 밀도.
	 * (한 번만 터지는 파티클은 거르지 않음)
	 */
	public static boolean unit() {
		return unit;
	}

	public static long now() {
		return ticks;
	}

	/** 방금 맞은 생명체 — 무적 시간을 게임 틱 기준으로 세도록 등록. */
	public static void hurt(LivingEntity e) {
		if (TickRateConfig.tickRate() != TickRateConfig.VANILLA) {
			HURT.add(e);
		}
	}

	/**
	 * 게임 틱이 아닌 서버 틱에는 바닐라가 깎은 무적 시간을 1 되돌림 → 무적 시간이 20틱 기준 실제 시간(0.5초 · 1초)과 같게.
	 * (멀티 히트 스킬이 무적 시간을 존중하는 Kind.NORMAL · NO_KB 판정이 60틱에서 3배 자주 들어가지 않게)
	 */
	public static void holdInvulnerability(boolean gameTick) {
		if (HURT.isEmpty()) {
			return;
		}
		HURT.removeIf(e -> e.isRemoved() || e.invulnerableTime <= 0);
		if (!gameTick) {
			for (LivingEntity e : HURT) {
				e.invulnerableTime++;
			}
		}
	}
}
