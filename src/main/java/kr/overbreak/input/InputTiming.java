package kr.overbreak.input;

import java.util.Map;
import java.util.WeakHashMap;

import kr.overbreak.core.tick.GameClock;
import kr.overbreak.net.SkillInputPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 스킬 키 서브틱 시각 — 클라이언트가 알려 준 "틱 안 몇 % 지점" 을 게임 틱 번호에 붙여 소수 시각으로 보관합니다.
 *
 *   시각 = 받은 게임 틱 - 1 + subTick  (클라이언트가 보낸 순간은 이미 지난 틱 안이므로)
 *   검증: slot 0~4 밖이면 버림 · subTick 은 0.0~1.0 으로 자름 · NaN 버림 · 1초에 {@link #MAX_PER_SECOND} 개 넘으면 버림
 *
 * 쓸 곳 (예정): 로켓 펀치 충전량 · 콤보 입력 창처럼 50ms 보다 세밀한 입력 시각이 필요한 스킬.
 */
public final class InputTiming {
	public static final int SLOTS = 5;
	public static final int MAX_PER_SECOND = 60;

	private static final class Record {
		final double[] pressAt = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
		final double[] releaseAt = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
		long windowStart = Long.MIN_VALUE / 2;
		int windowCount;
		int dropped;
	}

	private static final Map<ServerPlayer, Record> RECORDS = new WeakHashMap<>();

	private InputTiming() {}

	/** @return 받아들였으면 true */
	public static boolean receive(ServerPlayer p, SkillInputPayload msg) {
		Record r = RECORDS.computeIfAbsent(p, k -> new Record());
		long now = GameClock.now();
		if (now - r.windowStart >= 20) {
			r.windowStart = now;
			r.windowCount = 0;
		}
		if (++r.windowCount > MAX_PER_SECOND || msg.slot() < 0 || msg.slot() >= SLOTS || Float.isNaN(msg.subTick())) {
			r.dropped++;
			return false;
		}
		double sub = Math.max(0.0, Math.min(1.0, msg.subTick()));
		double at = now - 1 + sub;
		if (msg.pressed()) {
			r.pressAt[msg.slot()] = at;
		} else {
			r.releaseAt[msg.slot()] = at;
		}
		return true;
	}

	/** 마지막으로 누른 소수 게임 틱 (없으면 NaN). */
	public static double pressedAt(ServerPlayer p, int slot) {
		Record r = RECORDS.get(p);
		return r == null ? Double.NaN : r.pressAt[slot];
	}

	public static double releasedAt(ServerPlayer p, int slot) {
		Record r = RECORDS.get(p);
		return r == null ? Double.NaN : r.releaseAt[slot];
	}

	/** 누른 채로 있던 시간 (게임 틱, 소수) — 뗀 기록이 누른 뒤에 있으면 그 사이, 아니면 지금까지. */
	public static double heldTicks(ServerPlayer p, int slot) {
		double press = pressedAt(p, slot);
		if (Double.isNaN(press)) {
			return 0.0;
		}
		double release = releasedAt(p, slot);
		double end = !Double.isNaN(release) && release >= press ? release : GameClock.now();
		return Math.max(0.0, end - press);
	}

	/** 시험용: 버린 패킷 수. */
	public static int dropped(ServerPlayer p) {
		Record r = RECORDS.get(p);
		return r == null ? 0 : r.dropped;
	}
}
