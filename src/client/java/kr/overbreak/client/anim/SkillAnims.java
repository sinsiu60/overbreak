package kr.overbreak.client.anim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * 재생 중인 스킬 애니메이션 (엔티티별). 1인칭 · 3인칭 애니메이션이 모두 여기서 시간을 읽습니다.
 *
 * 시간은 1/20초 단위 연속 시간(ClientClock) + 프레임 보간입니다. 패킷 도착 = 0. 틱레이트와 상관없이 같은 수치가 같은 초입니다.
 */
public final class SkillAnims {
	/** 한 번의 재생. */
	public static final class Play {
		public final int anim;
		final double start;
		final int hiddenId;
		/** 서버가 정한 길이 (틱). 0 이면 번호마다 정해진 길이. */
		int duration;
		double stopTick = Double.NEGATIVE_INFINITY;
		/** 서버 확인 전에 누른 즉시 틀어 둔 재생 (본인 화면만). */
		boolean predicted;
		boolean confirmed;

		Play(int anim, double start, int hiddenId, int duration) {
			this.anim = anim;
			this.start = start;
			this.hiddenId = hiddenId;
			this.duration = duration;
		}

		/** 계획된 길이 (서버 중단과 무관). */
		public float planned() {
			return duration > 0 ? duration : length(anim);
		}

		public float elapsed(float partial) {
			return (float) (ticks - start + kr.overbreak.client.ClientClock.partial(partial));
		}

		/** 본편이 끝나는 시각 — 정해진 길이, 또는 서버가 중단시킨 시각 중 이른 쪽. 이후는 fade 동안 원래 자세로 돌아갑니다. */
		public float end() {
			float def = planned();
			return stopTick == Double.NEGATIVE_INFINITY ? def : Math.min(def, (float) (stopTick - start));
		}
	}

	private static double ticks;
	/** 누른 뒤 서버 확인을 기다리는 시간 (1/20초 단위). 이 안에 안 오면 거부된 것으로 보고 되돌립니다. */
	static final double CONFIRM_WINDOW = 8.0;
	private static final Map<Integer, List<Play>> PLAYS = new HashMap<>();

	private SkillAnims() {}

	/** 본편 길이 (틱). 피의 사슬은 서버가 멈출 때까지 (최대 80 — 준비 14 + 비행 7 + 대기 8 + 견인 40). */
	public static float length(int anim) {
		return switch (anim) {
			case SkillAnimPayload.SLAY -> 21.0F;
			case SkillAnimPayload.FURY -> 12.0F;
			case SkillAnimPayload.CHAIN -> 80.0F;
			case SkillAnimPayload.ULT -> 22.0F;
			case SkillAnimPayload.BASIC, SkillAnimPayload.BASIC_BACK -> 6.0F;
			case SkillAnimPayload.HK_SMASH -> 16.0F;
			case SkillAnimPayload.HK_SLAM -> 9.0F;
			case SkillAnimPayload.HK_CHARGE -> 14.0F;
			case SkillAnimPayload.HK_ULT -> 26.0F;
			case SkillAnimPayload.IF_SHOT -> 6.0F;
			case SkillAnimPayload.IF_CHARGE -> 40.0F;
			case SkillAnimPayload.IF_PUNCH -> 16.0F;
			case SkillAnimPayload.IF_BLOCK -> 40.0F;
			case SkillAnimPayload.IF_SLAM_AIR -> 200.0F;
			case SkillAnimPayload.IF_SLAM_HIT -> 12.0F;
			case SkillAnimPayload.IF_ULT_RISE -> 20.0F;
			case SkillAnimPayload.IF_ULT_DROP -> 24.0F;
			case SkillAnimPayload.VK_SHOT -> 8.0F;
			case SkillAnimPayload.VK_ROCKET -> 10.0F;
			case SkillAnimPayload.VK_FLOAT -> 200.0F;
			case SkillAnimPayload.VK_OVERHEAT -> 22.0F;
			case SkillAnimPayload.VK_BARRAGE -> 94.0F;
			case SkillAnimPayload.VK_RELOAD -> 30.0F;
			case SkillAnimPayload.SH_SHOT -> 9.0F;
			case SkillAnimPayload.SH_FAN -> 3.0F;
			case SkillAnimPayload.SH_RELOAD -> 40.0F;
			case SkillAnimPayload.SH_ROLL -> 8.0F;
			case SkillAnimPayload.SH_FLASH -> 10.0F;
			case SkillAnimPayload.SH_DEADEYE -> 40.0F;
			case SkillAnimPayload.SH_DEADEYE_FIRE -> 10.0F;
			case SkillAnimPayload.SD_REND -> 8.0F;
			case SkillAnimPayload.SD_EVADE -> 12.0F;
			case SkillAnimPayload.SD_KUNAI -> 8.0F;
			case SkillAnimPayload.SD_STRIKE -> 5.0F;
			case SkillAnimPayload.SD_STEP -> 4.0F;
			case SkillAnimPayload.TH_CAST -> 6.0F;
			case SkillAnimPayload.TH_DASH -> 4.0F;
			case SkillAnimPayload.TH_FIELD -> 10.0F;
			case SkillAnimPayload.TH_SMITE -> 14.0F;
			case SkillAnimPayload.TH_ULT -> 64.0F;
			case SkillAnimPayload.BR_BASIC, SkillAnimPayload.BR_BASIC_BACK -> 7.0F;
			case SkillAnimPayload.BR_BLOW -> 14.0F;
			case SkillAnimPayload.BR_WHIRL -> 20.0F;
			case SkillAnimPayload.BR_REGROUP -> 20.0F;
			case SkillAnimPayload.BR_ULT -> 22.0F;
			case SkillAnimPayload.GS_SHOT, SkillAnimPayload.GS_SHOT_L -> 6.0F;
			case SkillAnimPayload.GS_RELOAD -> 25.0F;
			case SkillAnimPayload.GS_BOOST -> 12.0F;
			case SkillAnimPayload.GS_SCATTER -> kr.overbreak.classes.gunslinger.DashScatter.LENGTH;
			case SkillAnimPayload.GS_ANCHOR -> 12.0F;
			case SkillAnimPayload.GS_RELEASE_TWIRL -> 10.0F;
			case SkillAnimPayload.GS_RELEASE_SNAP -> 12.0F;
			case SkillAnimPayload.GS_GLIDE -> 200.0F;
			case SkillAnimPayload.KNOCKDOWN -> 40.0F;
			default -> 0.0F;
		};
	}

	/** 끝난 뒤 원래 자세로 돌아가는 시간 (틱). */
	public static float fade(int anim) {
		// 돌진 난사가 끊기면 0.1초 만에 기본 자세로 (스펙 PART 2-4)
		return anim == SkillAnimPayload.SLAY ? 5.0F : anim == SkillAnimPayload.KNOCKDOWN ? 6.0F
				: anim == SkillAnimPayload.GS_SCATTER ? 2.0F : 4.0F;
	}

	public static void receive(SkillAnimPayload msg) {
		List<Play> list = PLAYS.computeIfAbsent(msg.entityId(), k -> new ArrayList<>());
		if (msg.anim() == SkillAnimPayload.KNOCKDOWN) {
			// 넘어진 채로 다시 오면(연장) 처음부터 다시 쓰러지지 않고 남은 시간만 늘립니다
			for (Play p : list) {
				if (p.anim == SkillAnimPayload.KNOCKDOWN && p.stopTick == Double.NEGATIVE_INFINITY && ticks - p.start < p.end()) {
					p.duration = (int) Math.round(ticks - p.start) + msg.duration();
					return;
				}
			}
		}
		if (msg.anim() > 0) {
			int anim = msg.anim();
			// 누른 즉시 틀어 둔 재생이 있으면 처음부터 다시 틀지 않고 그대로 확인만 합니다 (다시 틀면 핑만큼 덜컹임)
			if (msg.elapsed() == 0) {
				for (Play p : list) {
					if (p.anim == anim && p.predicted && !p.confirmed && p.stopTick == Double.NEGATIVE_INFINITY
							&& ticks - p.start <= CONFIRM_WINDOW) {
						p.confirmed = true;
						if (msg.duration() > 0) {
							p.duration = msg.duration();
						}
						return;
					}
				}
			}
			// 늦게 받았거나 단계를 건너뛴 재생 — 지난 만큼 앞당겨 시작, 이미 끝났으면 틀지 않음
			int skip = Math.max(0, msg.elapsed());
			float planned = msg.duration() > 0 ? msg.duration() : length(anim);
			if (skip > 0 && skip >= planned) {
				return;
			}
			// 기본 공격 정방향 · 역방향은 같은 동작의 두 모습이라 서로 덮어씁니다
			boolean basic = anim == SkillAnimPayload.BASIC || anim == SkillAnimPayload.BASIC_BACK;
			list.removeIf(p -> p.anim == anim || basic && (p.anim == SkillAnimPayload.BASIC || p.anim == SkillAnimPayload.BASIC_BACK));
			list.add(new Play(anim, ticks - skip, msg.hiddenId(), msg.duration()));
		} else {
			for (Play p : list) {
				if (p.anim == -msg.anim() && p.stopTick == Double.NEGATIVE_INFINITY) {
					p.stopTick = ticks;
				}
			}
		}
	}

	/**
	 * 누른 즉시 본인 화면에서 먼저 틉니다 (서버 확인 전 · 스펙 PART 8-1).
	 * 서버가 같은 번호를 보내면 그대로 이어 가고, {@link #CONFIRM_WINDOW} 안에 안 오면 기본 자세로 되돌립니다.
	 */
	public static void predict(int entityId, int anim) {
		List<Play> list = PLAYS.computeIfAbsent(entityId, k -> new ArrayList<>());
		list.removeIf(p -> p.anim == anim);
		Play p = new Play(anim, ticks, -1, 0);
		p.predicted = true;
		list.add(p);
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			PLAYS.clear();
			return;
		}
		ticks = kr.overbreak.client.ClientClock.now();
		// 확인이 오지 않은 예측 재생은 거부된 것 — 이미 난 연출은 두고 자세만 되돌립니다
		for (List<Play> list : PLAYS.values()) {
			for (Play p : list) {
				if (p.predicted && !p.confirmed && p.stopTick == Double.NEGATIVE_INFINITY && ticks - p.start > CONFIRM_WINDOW) {
					p.stopTick = ticks;
				}
			}
		}
		PLAYS.values().forEach(list -> list.removeIf(p -> ticks - p.start >= p.end() + fade(p.anim)));
		PLAYS.values().removeIf(List::isEmpty);
	}

	public static @Nullable Play find(int entityId, int anim) {
		List<Play> list = PLAYS.get(entityId);
		if (list != null) {
			for (Play p : list) {
				if (p.anim == anim) {
					return p;
				}
			}
		}
		return null;
	}

	/** 가장 나중에 시작한 재생 (겹치면 새 것이 자세를 가져갑니다). */
	public static @Nullable Play latest(int entityId) {
		List<Play> list = PLAYS.get(entityId);
		return list == null || list.isEmpty() ? null : list.getLast();
	}

	/** 주어진 번호들 중 가장 나중에 시작한 재생. */
	public static @Nullable Play latestOf(int entityId, int... anims) {
		List<Play> list = PLAYS.get(entityId);
		if (list == null) {
			return null;
		}
		for (int i = list.size() - 1; i >= 0; i--) {
			Play p = list.get(i);
			for (int a : anims) {
				if (p.anim == a) {
					return p;
				}
			}
		}
		return null;
	}

	/** 시험용: 시작부터 지난 시간 (1/20초 단위, 반올림). 없으면 -1. */
	public static long elapsedTicks(int entityId, int anim) {
		Play p = find(entityId, anim);
		return p == null ? -1 : Math.round(ticks - p.start);
	}

	/** 본편이 재생 중인가 (서버가 멈추기 전 · 정해진 길이 안). */
	public static boolean playing(int entityId, int anim) {
		Play p = find(entityId, anim);
		return p != null && p.stopTick == Double.NEGATIVE_INFINITY && ticks - p.start < p.end();
	}

	/** 모델 애니메이션이 대신 보여 주는 디스플레이 엔티티 — 본편이 도는 동안 숨깁니다. */
	public static boolean hides(Entity e) {
		int id = e.getId();
		for (List<Play> list : PLAYS.values()) {
			for (Play p : list) {
				if (p.hiddenId == id && p.stopTick == Double.NEGATIVE_INFINITY && ticks - p.start < p.end()) {
					return true;
				}
			}
		}
		return false;
	}
}
