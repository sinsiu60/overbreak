package kr.overbreak.client.camera;

import kr.overbreak.client.input.InputMode;
import kr.overbreak.net.ViewPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * 스킬이 도는 동안 시점을 3인칭으로 잡아 두는 자리 (곡예 난사 — 제 몸이 도는 것을 봐야 하는 동작).
 *
 *   잡을 때 지금 시점을 적어 두고 어깨 너머 3인칭으로 바꿉니다.
 *   풀 때 적어 둔 시점으로 되돌립니다 — 원래 3인칭이었으면 그대로 3인칭입니다.
 *   잡혀 있는 동안 F5 를 눌러 바꾸면 그 시점을 존중해서, 풀 때 되돌리지 않습니다.
 */
public final class ViewLock {
	/** 잡기 전의 시점 (null = 잡고 있지 않음). */
	private static @Nullable CameraType saved;
	/** 우리가 바꿔 놓은 시점 — 이것과 다르면 사람이 직접 바꾼 것입니다. */
	private static @Nullable CameraType forced;

	private ViewLock() {}

	public static void receive(ViewPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		if (msg.third()) {
			hold(mc);
		} else {
			release(mc);
		}
	}

	private static void hold(Minecraft mc) {
		if (saved != null) {
			return;
		}
		saved = mc.options.getCameraType();
		forced = CameraType.THIRD_PERSON_BACK;
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
	}

	private static void release(Minecraft mc) {
		CameraType back = saved;
		saved = null;
		if (back == null) {
			return;
		}
		// 잡혀 있는 동안 사람이 F5 로 바꿨으면 그 선택을 그대로 둡니다
		if (mc.options.getCameraType() == forced) {
			mc.options.setCameraType(back);
		}
		forced = null;
	}

	/** 월드를 나가거나 직업이 풀리면 흔적을 지웁니다. */
	public static void tick(Minecraft mc) {
		if (mc.level == null || !InputMode.active()) {
			saved = null;
			forced = null;
		}
	}

	/** 시험용: 지금 잡고 있는가. */
	public static boolean held() {
		return saved != null;
	}
}
