package kr.overbreak.client.mixin;

import kr.overbreak.client.audio.MatchMusicPlayer;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 경기 막판 음악이 도는 동안 바닐라 배경 음악이 새로 시작하지 않게 — 두 곡이 겹치지 않습니다. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void overbreak$holdForMatchMusic(CallbackInfo ci) {
		if (MatchMusicPlayer.playing()) {
			ci.cancel();
		}
	}
}
