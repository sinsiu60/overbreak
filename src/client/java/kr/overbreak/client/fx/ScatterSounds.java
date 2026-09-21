package kr.overbreak.client.fx;

import java.util.ArrayDeque;
import java.util.Deque;

import kr.overbreak.sound.OverbreakSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * 돌진 난사 발사음 — 서버가 아니라 클라이언트가 동작 시각에 맞춰 직접 틉니다 (스펙 PART 7-2).
 *
 *   발 시각은 3인칭 애니메이션의 사격 이벤트가 정합니다 (양팔 30발). 볼륨 0.6, 피치 1.1~1.3 무작위
 *   동시에 울리는 발사음은 6개까지 — 넘으면 가장 오래된 것부터 끊습니다 (여러 명이 한꺼번에 써도 소리가 뭉개지지 않게)
 *   보는 사람 모두가 자기 화면의 동작 시각대로 들으므로 총구 불꽃 · 예광탄과 소리가 어긋나지 않습니다
 */
public final class ScatterSounds {
	/** 동시에 울리는 발사음 최대 수. */
	private static final int VOICES = 6;

	private static final Deque<SoundInstance> PLAYING = new ArrayDeque<>();

	private ScatterSounds() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			PLAYING.clear();
			return;
		}
		PLAYING.removeIf(s -> !mc.getSoundManager().isActive(s));
	}

	/** 한 발 (돌진 난사 사격 이벤트가 부름). */
	public static void shoot(Minecraft mc, AbstractClientPlayer p) {
		while (PLAYING.size() >= VOICES) {
			mc.getSoundManager().stop(PLAYING.removeFirst());
		}
		float pitch = 1.1F + p.getRandom().nextFloat() * 0.2F;
		SoundInstance s = new SimpleSoundInstance(OverbreakSounds.SCATTER_SHOT.value(), SoundSource.PLAYERS, 0.6F, pitch,
				p.getRandom(), p.getX(), p.getEyeY() - 0.3, p.getZ());
		mc.getSoundManager().play(s);
		PLAYING.addLast(s);
	}

	/** 시험용: 지금 울리고 있는 발사음 수. */
	public static int playing() {
		return PLAYING.size();
	}
}
