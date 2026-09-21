package kr.overbreak.client.audio;

import kr.overbreak.net.MusicPayload;
import kr.overbreak.sound.OverbreakSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * 경기 막판 배경 음악 — 게임을 방해하지 않게 낮은 볼륨으로 깝니다.
 *
 *   켜질 때 2초에 걸쳐 서서히 올라오고, 꺼질 때 3초에 걸쳐 서서히 빠집니다 (갑자기 끊기지 않게)
 *   곡이 끝나면 처음부터 다시 (경기가 길어져도 이어짐)
 *   설정의 "음악" 볼륨을 따릅니다 — 음악을 꺼 둔 사람에게는 들리지 않습니다
 *   도는 동안 바닐라 배경 음악은 멈춰 두 곡이 겹치지 않습니다 (MusicManagerMixin)
 */
public final class MatchMusicPlayer {
	/** 음악 볼륨 기준 세기 — 효과음 · 발소리가 묻히지 않을 만큼. */
	static final float VOLUME = 0.3F;
	/** 올라오는 · 빠지는 시간 (1/20초 단위 — 소리 틱도 틱레이트대로 돌아 틱 수로 바꿔 씁니다). */
	static final int FADE_IN = 40;
	static final int FADE_OUT = 60;

	private static @Nullable Track track;

	private MatchMusicPlayer() {}

	public static void receive(MusicPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		if (msg.on()) {
			start(mc);
		} else if (track != null) {
			track.fadeOut();
		}
	}

	private static void start(Minecraft mc) {
		if (track != null && !track.isStopped()) {
			track.fadeIn();
			return;
		}
		mc.getMusicManager().stopPlaying();
		track = new Track();
		mc.getSoundManager().play(track);
	}

	/** 월드를 나가면 바로 끕니다. */
	public static void tick(Minecraft mc) {
		if (mc.level == null && track != null) {
			mc.getSoundManager().stop(track);
			track = null;
		}
		if (track != null && track.isStopped()) {
			track = null;
		}
	}

	/** 지금 경기 음악이 울리고 있는가 — 바닐라 배경 음악을 멈춰 둘지. */
	public static boolean playing() {
		return track != null && !track.isStopped();
	}

	/** 볼륨을 스스로 올리고 내리는 곡. */
	private static final class Track extends AbstractTickableSoundInstance {
		/** 0~1 — 지금 세기. */
		private float level;
		private boolean leaving;

		Track() {
			super(OverbreakSounds.MATCH_POINT.value(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 0.0001F;
		}

		void fadeIn() {
			leaving = false;
		}

		void fadeOut() {
			leaving = true;
		}

		@Override
		public void tick() {
			int in = kr.overbreak.core.tick.Ticks.of(FADE_IN);
			int out = kr.overbreak.core.tick.Ticks.of(FADE_OUT);
			level = leaving ? level - 1.0F / out : Math.min(1.0F, level + 1.0F / in);
			if (level <= 0.0F) {
				stop();
				return;
			}
			volume = Math.max(0.0001F, VOLUME * level);
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
