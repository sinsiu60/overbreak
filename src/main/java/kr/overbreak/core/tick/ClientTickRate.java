package kr.overbreak.core.tick;

/** 클라이언트가 받은 틱레이트를 공용 설정에 넣는 창구 (전용 서버에 접속한 클라이언트용). */
public final class ClientTickRate {
	private ClientTickRate() {}

	public static void sync(int rate) {
		if (rate > 0 && rate != TickRateConfig.tickRate()) {
			TickRateConfig.set(rate);
		}
	}
}
