package kr.overbreak.client;

import kr.overbreak.core.tick.Ticks;
import net.minecraft.client.Minecraft;

/**
 * 클라이언트 연출 시계 — 1/20초 단위의 연속 시간.
 *
 * 애니메이션 · 반동 · 궤적 · HUD 효과의 수치는 1/20초 단위로 적혀 있습니다. 클라이언트가 60틱으로 돌면 틱마다 1/3 씩 흐르므로
 * 기존 수치를 그대로 쓰면서 1초에 60번 부드럽게 갱신됩니다.
 *
 *   now()              지금 시간 (클라이언트 틱이 끝날 때마다 틱 하나만큼 증가)
 *   at(partial)        화면 프레임 시각 = now + 부분 틱
 *   partial(partial)   부분 틱을 시간 단위로 (60틱이면 partial / 3)
 */
public final class ClientClock {
	private static double now;

	private ClientClock() {}

	/** 클라이언트 틱마다 한 번 (가장 먼저). */
	public static void tick(Minecraft mc) {
		if (!mc.isPaused()) {
			now += Ticks.step();
		}
	}

	public static double now() {
		return now;
	}

	public static float partial(float partialTick) {
		return (float) (partialTick * Ticks.step());
	}

	public static double at(float partialTick) {
		return now + partial(partialTick);
	}
}
