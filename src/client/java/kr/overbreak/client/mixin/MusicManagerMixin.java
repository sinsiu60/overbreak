package kr.overbreak.client.mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 마인크래프트 기본 배경 음악을 틀지 않습니다 (0.2f) — 메뉴 · 로비 · 전장 모두.
 * 이미 울리고 있던 곡도 멈춥니다. 모드 음악(경기 막판 음악, client/audio/MatchMusicPlayer)은 따로 틀어서 영향이 없습니다.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void overbreak$noVanillaMusic(CallbackInfo ci) {
		((MusicManager) (Object) this).stopPlaying();
		ci.cancel();
	}
}
