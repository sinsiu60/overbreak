package kr.overbreak.client.mixin;

import kr.overbreak.client.input.InputMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 전장에서는 인벤토리(기본 E)가 열리지 않고 액티브3 스킬이 나갑니다 (0.2).
 *
 * 키를 따로 감시하지 않고 "인벤토리 창이 열리려는 순간" 을 잡습니다.
 * 그래서 인벤토리 키를 다른 것으로 바꿔 두었어도 그 키가 그대로 액티브3 이 됩니다.
 * 바닐라는 {@code Minecraft.handleKeybinds} 에서 {@code gui.setScreen(new InventoryScreen(player))} 로 엽니다.
 *
 * 그대로 열어 두는 경우 — 직업이 없을 때(로비 · 관리자 자유 이동)와 크리에이티브 · 관전 모드.
 */
@Mixin(Gui.class)
public abstract class GuiInventoryMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void overbreak$inventoryIsSkill(Screen screen, CallbackInfo ci) {
		if (!(screen instanceof InventoryScreen) || !InputMode.active()) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player != null && (player.isCreative() || player.isSpectator())) {
			// 개발자 · 관리자용 — 크리에이티브나 관전 중에는 평소처럼 열립니다
			return;
		}
		InputMode.tertiary();
		ci.cancel();
	}
}
