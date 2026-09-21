package kr.overbreak.client.fx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import kr.overbreak.classes.gunslinger.DashScatter;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.sound.OverbreakSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * 돌진 난사 발사음 — 서버가 아니라 클라이언트가 동작 시각에 맞춰 직접 틉니다 (스펙 PART 7-2).
 *
 *   0.05초마다 한 발 · 18발. 볼륨 0.9, 피치 0.9~1.1 무작위
 *   동시에 울리는 발사음은 6개까지 — 넘으면 가장 오래된 것부터 끊습니다 (여러 명이 한꺼번에 써도 소리가 뭉개지지 않게)
 *   보는 사람 모두가 자기 화면의 동작 시각대로 들으므로 총구 불꽃 · 예광탄과 소리가 어긋나지 않습니다
 */
public final class ScatterSounds {
	/** 동시에 울리는 발사음 최대 수. */
	private static final int VOICES = 6;

	private record Fired(SkillAnims.Play play, int shots) {}

	private static final Map<Integer, Fired> FIRED = new HashMap<>();
	private static final Deque<SoundInstance> PLAYING = new ArrayDeque<>();

	private ScatterSounds() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			FIRED.clear();
			PLAYING.clear();
			return;
		}
		PLAYING.removeIf(s -> !mc.getSoundManager().isActive(s));
		for (AbstractClientPlayer p : mc.level.players()) {
			SkillAnims.Play play = SkillAnims.find(p.getId(), SkillAnimPayload.GS_SCATTER);
			if (play == null) {
				FIRED.remove(p.getId());
				continue;
			}
			Fired f = FIRED.get(p.getId());
			// 새 재생(다시 쓰거나 단계를 건너뛰어 다시 받음)이면 처음부터 셉니다
			int done = f != null && f.play == play ? f.shots : shotsBefore(play.elapsed(0.0F) - 1.0E-3F);
			float e = Math.min(play.elapsed(1.0F), play.end());
			int due = shotsBefore(e);
			for (int i = done; i < due; i++) {
				shoot(mc, p);
			}
			FIRED.put(p.getId(), new Fired(play, Math.max(done, due)));
		}
	}

	/** 시각 e 까지 나갔어야 할 발 수. */
	private static int shotsBefore(float e) {
		float into = e - DashScatter.SCATTER_START;
		if (into < 0.0F) {
			return 0;
		}
		return Math.min(DashScatter.SHOTS, (int) Math.floor(into) + 1);
	}

	private static void shoot(Minecraft mc, AbstractClientPlayer p) {
		while (PLAYING.size() >= VOICES) {
			mc.getSoundManager().stop(PLAYING.removeFirst());
		}
		float pitch = 0.9F + p.getRandom().nextFloat() * 0.2F;
		SoundInstance s = new SimpleSoundInstance(OverbreakSounds.SCATTER_SHOT.value(), SoundSource.PLAYERS, 0.9F, pitch,
				p.getRandom(), p.getX(), p.getEyeY() - 0.3, p.getZ());
		mc.getSoundManager().play(s);
		PLAYING.addLast(s);
	}

	/** 시험용: 지금 울리고 있는 발사음 수. */
	public static int playing() {
		return PLAYING.size();
	}
}
