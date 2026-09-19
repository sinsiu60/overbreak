package kr.overbreak.client.input;

import kr.overbreak.Overbreak;
import kr.overbreak.net.ReloadPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/** OVERBREAK 조작 키 (설정 → 조작에서 바꿀 수 있음). */
public final class Keys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Overbreak.id("overbreak"));
	/** GLFW R */
	private static final int KEY_R = 82;

	private static KeyMapping reload;

	private Keys() {}

	public static void register() {
		reload = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.overbreak.reload", KEY_R, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (reload.consumeClick()) {
				// 탄창이 없는 직업 · 가득 찬 탄창 · 기절 중이면 서버가 무시합니다
				if (InputMode.active() && mc.player != null && ClientPlayNetworking.canSend(ReloadPayload.TYPE)) {
					ClientPlayNetworking.send(ReloadPayload.INSTANCE);
				}
			}
		});
	}
}
