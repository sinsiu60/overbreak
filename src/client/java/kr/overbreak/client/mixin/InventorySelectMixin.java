package kr.overbreak.client.mixin;

import kr.overbreak.client.input.InputMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 조작 모드에서는 핫바가 무기 한 칸이라, 본인 핫바 선택을 첫 칸으로 고정합니다 (숫자키 · 휠 무시).
 * 싱글플레이의 내장 서버 쪽 인벤토리는 플레이어 객체가 달라 영향이 없습니다.
 */
@Mixin(Inventory.class)
public abstract class InventorySelectMixin {
	@Shadow
	@Final
	public Player player;

	@ModifyVariable(method = "setSelectedSlot", at = @At("HEAD"), argsOnly = true)
	private int overbreak$lockSlot(int selected) {
		return InputMode.active() && this.player == Minecraft.getInstance().player ? 0 : selected;
	}
}
