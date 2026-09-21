package kr.overbreak.client.fx;

import static kr.overbreak.classes.gunslinger.DashScatter.DASH_START;
import static kr.overbreak.classes.gunslinger.DashScatter.RECOVER_START;
import static kr.overbreak.classes.gunslinger.DashScatter.SCATTER_START;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.net.ScatterHitPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.sound.OverbreakSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 돌진 난사 소리 — 전부 클라이언트가 동작 시각에 맞춰 직접 틉니다 (서버는 이 소리를 방송하지 않음 → 시전자에게 두 번 들리지 않음).
 *
 *   기 모으기 시작 : 철컥 (본체, 0.8)
 *   돌진 시작      : 휘익 (본체를 따라 움직임, 0.9)
 *   분신이 쏠 때마다: 발사음 — 쏜 분신의 총구 자리에서 (0.9 · 피치 0.95~1.05). 시전자당 동시 6개, 넘으면 가장 오래된 것부터 끊음
 *   펄스가 적을 맞힘: 적중음 — 시전자 본인에게만, 위치 없이 (서버가 {@link ScatterHitPayload} 로 알림 · 공중 치명타면 피치 1.15)
 *   난사가 끝까지 감: 여운 (본체, 1.0 — 길어서 마무리가 끝나도 끊지 않음) + 0.05초 뒤 탄피 (0.6)
 *
 *   끊기면: 난사 전이면 휘익을 멈추고 끝 · 난사 도중이면 여운만 0.6 으로 한 번 (탄피 없음) · 죽었으면 아무 소리도 없음
 *   늦게 받은 사람은 받은 시점 이후의 소리만 듣습니다 (지난 소리를 몰아서 틀지 않음).
 */
public final class ScatterSounds {
	/** 시전자당 동시에 울리는 발사음 최대 수. */
	private static final int VOICES = 6;
	/** 탄피는 여운보다 0.05초 뒤. */
	private static final float CASINGS_AT = RECOVER_START + 1.0F;

	/** 시전자별 진행 — 이번 재생 · 지난 틱 시각 · 따라다니는 돌진 소리. */
	private static final class Track {
		final SkillAnims.Play play;
		float last;
		SoundInstance whoosh;

		Track(SkillAnims.Play play, float last) {
			this.play = play;
			this.last = last;
		}
	}

	private static final Map<Integer, Track> TRACKS = new HashMap<>();
	private static final Map<Integer, Deque<SoundInstance>> SHOTS = new HashMap<>();

	private ScatterSounds() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			TRACKS.clear();
			SHOTS.clear();
			return;
		}
		SHOTS.values().forEach(d -> d.removeIf(s -> !mc.getSoundManager().isActive(s)));
		SHOTS.values().removeIf(Deque::isEmpty);
		if (mc.isPaused()) {
			return;
		}
		// 끝났거나 끊긴 재생
		TRACKS.entrySet().removeIf(en -> {
			SkillAnims.Play now = SkillAnims.find(en.getKey(), SkillAnimPayload.GS_SCATTER);
			if (now == en.getValue().play) {
				return false;
			}
			interrupted(mc, en.getKey(), en.getValue());
			return true;
		});
		for (AbstractClientPlayer p : mc.level.players()) {
			SkillAnims.Play play = SkillAnims.find(p.getId(), SkillAnimPayload.GS_SCATTER);
			if (play == null) {
				continue;
			}
			float e = Math.min(play.elapsed(1.0F), play.end());
			Track t = TRACKS.get(p.getId());
			if (t == null) {
				// 새 재생 — 받은 시점 이전의 소리는 건너뜀
				t = new Track(play, play.elapsed(0.0F) - 1.0E-3F);
				TRACKS.put(p.getId(), t);
			}
			phase(mc, p, t, t.last, e);
			t.last = Math.max(t.last, e);
		}
	}

	/** (from, to] 사이에 온 단계 소리. */
	private static void phase(Minecraft mc, AbstractClientPlayer p, Track t, float from, float to) {
		if (crossed(0.0F, from, to)) {
			at(mc, OverbreakSounds.SCATTER_COCK.value(), p.position().add(0, 1.2, 0), 0.8F, 1.0F, p.getRandom());
		}
		if (crossed(DASH_START, from, to)) {
			t.whoosh = new EntityBoundSoundInstance(OverbreakSounds.SCATTER_WHOOSH.value(), SoundSource.PLAYERS, 0.9F, 1.0F, p,
					p.getRandom().nextLong());
			mc.getSoundManager().play(t.whoosh);
		}
		if (crossed(RECOVER_START, from, to)) {
			at(mc, OverbreakSounds.SCATTER_TAIL.value(), p.position().add(0, 1.2, 0), 1.0F, 1.0F, p.getRandom());
		}
		if (crossed(CASINGS_AT, from, to)) {
			at(mc, OverbreakSounds.SCATTER_CASINGS.value(), p.position().add(0, 0.2, 0), 0.6F, 1.0F, p.getRandom());
		}
	}

	private static boolean crossed(float at, float from, float to) {
		return at > from && at <= to;
	}

	/** 난사가 끝까지 가지 못하고 멈춤. */
	private static void interrupted(Minecraft mc, int id, Track t) {
		if (t.last >= RECOVER_START) {
			return;
		}
		if (t.whoosh != null && t.last < SCATTER_START) {
			mc.getSoundManager().stop(t.whoosh);
		}
		if (!(mc.level.getEntity(id) instanceof AbstractClientPlayer p) || !p.isAlive()) {
			return;
		}
		if (t.last >= SCATTER_START) {
			at(mc, OverbreakSounds.SCATTER_TAIL.value(), p.position().add(0, 1.2, 0), 0.6F, 1.0F, p.getRandom());
		}
	}

	/** 분신 한 발 — 쏜 총구 자리에서 (돌진 난사 사격 이벤트가 부름). */
	public static void shoot(Minecraft mc, AbstractClientPlayer p, Vec3 muzzle) {
		Deque<SoundInstance> voices = SHOTS.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
		while (voices.size() >= VOICES) {
			mc.getSoundManager().stop(voices.removeFirst());
		}
		float pitch = 0.95F + p.getRandom().nextFloat() * 0.1F;
		SoundInstance s = new SimpleSoundInstance(OverbreakSounds.SCATTER_SHOT.value(), SoundSource.PLAYERS, 0.9F, pitch,
				p.getRandom(), muzzle.x, muzzle.y, muzzle.z);
		mc.getSoundManager().play(s);
		voices.addLast(s);
	}

	/** 적중음 — 시전자 본인에게만, 위치 없이 귀에 바로. */
	public static void hit(ScatterHitPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		mc.getSoundManager().play(new SimpleSoundInstance(OverbreakSounds.SCATTER_HIT.value().location(), SoundSource.PLAYERS,
				0.8F, msg.crit() ? 1.15F : 1.0F, mc.player.getRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
	}

	private static void at(Minecraft mc, SoundEvent sound, Vec3 pos, float volume, float pitch, RandomSource random) {
		mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.PLAYERS, volume, pitch, random, pos.x, pos.y, pos.z));
	}

	/** 시험용: 지금 울리고 있는 발사음 수 (모든 시전자 합). */
	public static int playing() {
		return SHOTS.values().stream().mapToInt(Deque::size).sum();
	}
}
