package kr.overbreak.combat;

import java.util.Map;
import java.util.WeakHashMap;

import kr.overbreak.core.tick.TickRateConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * 지연 보상 (쏜 사람 우선) — 플레이어 히트박스를 서버 틱마다 기록해 두고, 히트스캔 판정 때 쏜 사람의 핑만큼 되감아 봅니다.
 * 오버워치처럼 "내 화면에서 맞았으면 맞은 것" 에 가깝게.
 *
 *   기록: 플레이어마다 최근 {@link #SIZE} 서버 틱 (60틱이면 1초) 의 히트박스 — 기본 배열 링버퍼, 10명이면 약 30KB
 *   되감는 시간 = 핑 + 화면 보간 지연 {@link #INTERP_MS}, 최대 {@link #MAX_MS} (그 이상은 잘라서 고핑 악용 방지)
 *   몹 · 더미는 기록하지 않고 지금 위치로 판정 (서버가 직접 움직여 클라이언트 화면과 거의 같음)
 */
public final class HitboxRewind {
	public static final int SIZE = 60;
	public static final int MAX_MS = 200;
	/** 남의 캐릭터는 클라이언트가 받은 위치 사이를 부드럽게 이어 그려 그만큼 늦게 보입니다. */
	public static final int INTERP_MS = 50;

	private static final class Track {
		final double[] box = new double[SIZE * 6];
		int head = -1;
		int count;
	}

	private static final Map<LivingEntity, Track> TRACKS = new WeakHashMap<>();

	private HitboxRewind() {}

	/** 서버 틱마다 — 지금 히트박스를 기록. */
	public static void record(LivingEntity e) {
		Track t = TRACKS.computeIfAbsent(e, k -> new Track());
		t.head = (t.head + 1) % SIZE;
		t.count = Math.min(SIZE, t.count + 1);
		AABB b = e.getBoundingBox();
		int i = t.head * 6;
		t.box[i] = b.minX;
		t.box[i + 1] = b.minY;
		t.box[i + 2] = b.minZ;
		t.box[i + 3] = b.maxX;
		t.box[i + 4] = b.maxY;
		t.box[i + 5] = b.maxZ;
	}

	/** ticksAgo 서버 틱 전의 히트박스 (기록이 모자라면 가장 오래된 것, 기록이 없는 생명체는 지금). */
	public static AABB boxAt(LivingEntity e, int ticksAgo) {
		Track t = TRACKS.get(e);
		if (ticksAgo <= 0 || t == null || t.count == 0) {
			return e.getBoundingBox();
		}
		int back = Math.min(ticksAgo, t.count - 1);
		int i = Math.floorMod(t.head - back, SIZE) * 6;
		return new AABB(t.box[i], t.box[i + 1], t.box[i + 2], t.box[i + 3], t.box[i + 4], t.box[i + 5]);
	}

	/** 쏜 사람 기준 되감을 서버 틱 수 = (핑 + 보간) 을 최대 200ms 로 잘라 지금 틱레이트로. */
	public static int rewindTicks(ServerPlayer shooter) {
		int ping = shooter.connection == null ? 0 : Math.max(0, shooter.connection.latency());
		return ticksFor(ping);
	}

	public static int ticksFor(int pingMs) {
		int ms = Math.min(MAX_MS, Math.max(0, pingMs) + INTERP_MS);
		return (int) Math.round(ms * TickRateConfig.tickRate() / 1000.0);
	}

	/** 시험용. */
	public static void forget(LivingEntity e) {
		TRACKS.remove(e);
	}
}
