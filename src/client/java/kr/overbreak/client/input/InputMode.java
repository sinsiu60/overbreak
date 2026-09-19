package kr.overbreak.client.input;

import kr.overbreak.net.InputModePayload;
import kr.overbreak.net.LeftClickPayload;
import kr.overbreak.net.RightHoldPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * OVERBREAK 조작 모드 (서버가 알려 줌 — 직업이 있으면 켜짐).
 *
 * 켜져 있는 동안
 *   - 좌클릭은 {@link LeftClickPayload} 만 보냅니다 (바닐라 공격 · 블록 부수기 · 스윙 없음, MinecraftAttackMixin)
 *   - 본인 팔 휘두르기 모션이 나오지 않습니다 (LocalPlayerSwingMixin)
 */
public final class InputMode {
	private static boolean active;
	private static long ticks;
	private static long lastSentTick = -1;
	private static boolean rightSent;
	private static boolean wasActive;

	private InputMode() {}

	public static void receive(InputModePayload msg) {
		active = msg.active();
		Minecraft mc = Minecraft.getInstance();
		if (active && mc.player != null) {
			// 핫바는 무기 한 칸 — 선택을 첫 칸으로 고정 (InventorySelectMixin)
			mc.player.getInventory().setSelectedSlot(0);
		}
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			active = false;
			return;
		}
		ticks++;
	}

	/** 우클릭 누름 · 뗌이 바뀐 틱에만 서버로 (입력 처리 전이라 같은 틱의 사용 패킷보다 먼저 도착). 켜지는 순간에는 한 번 알림. */
	public static void tickRightHold(Minecraft mc) {
		if (mc.player == null || !active) {
			wasActive = false;
			rightSent = false;
			return;
		}
		boolean down = mc.gui.screen() == null && mc.options.keyUse.isDown();
		if ((down != rightSent || !wasActive) && ClientPlayNetworking.canSend(RightHoldPayload.TYPE)) {
			ClientPlayNetworking.send(new RightHoldPayload(down));
			rightSent = down;
			wasActive = true;
		}
	}

	public static boolean active() {
		return active;
	}

	/** 누른 순간. */
	public static void click() {
		send(false);
	}

	/** 누르고 있는 동안 매 틱 — 같은 틱에 누른 순간 신호를 이미 보냈으면 건너뜁니다. */
	public static void hold() {
		if (lastSentTick != ticks) {
			send(true);
		}
	}

	private static void send(boolean held) {
		lastSentTick = ticks;
		if (ClientPlayNetworking.canSend(LeftClickPayload.TYPE)) {
			ClientPlayNetworking.send(new LeftClickPayload(held));
		}
	}
}
