package kr.overbreak.client.input;

import kr.overbreak.net.SkillInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * 스킬 키 서브틱 — 화면 프레임마다 5개 키(공격 · 사용 · 웅크리기 · 인벤토리 · 버리기)의 눌림을 보고,
 * 바뀐 프레임에 "지난 틱에서 몇 % 지점" (DeltaTracker 부분 틱) 을 {@link SkillInputPayload} 로 보냅니다.
 * 키보드 Mixin 없이 프레임 간격만큼 정확합니다 (144fps 면 약 7ms).
 */
public final class InputTimingTracker {
	private static final boolean[] DOWN = new boolean[5];

	private InputTimingTracker() {}

	/** 프레임마다. */
	public static void frame(Minecraft mc) {
		if (mc.player == null || mc.gui.screen() != null || !InputMode.active()) {
			return;
		}
		// 액티브3 은 인벤토리 키(기본 E) 입니다 — MinecraftInventoryMixin 이 창 대신 스킬로 돌립니다
		KeyMapping[] keys = {mc.options.keyAttack, mc.options.keyUse, mc.options.keyShift, mc.options.keyInventory, mc.options.keyDrop};
		float sub = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		for (int i = 0; i < keys.length; i++) {
			boolean down = keys[i].isDown();
			if (down != DOWN[i]) {
				DOWN[i] = down;
				if (ClientPlayNetworking.canSend(SkillInputPayload.TYPE)) {
					ClientPlayNetworking.send(new SkillInputPayload(i, down, sub));
				}
			}
		}
	}
}
