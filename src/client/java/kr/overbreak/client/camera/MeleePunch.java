package kr.overbreak.client.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * 근접 타격 화면 반동 (0.2a) — 오버워치처럼, 휘두를 때 화면이 그 방향으로 살짝 기울었다가 돌아옵니다.
 *
 *   베는 방향을 따라 시계 · 반시계로 기울고(Z), 힘을 싣는 느낌으로 아주 조금 숙였다가(X) 풉니다.
 *   기를 모으는 스킬(살육 · 분쇄 · 강타)은 누른 순간이 아니라 실제로 후려치는 순간에 흔들립니다.
 *   들어갈 때는 두 틱 만에 홱, 풀릴 때는 여섯 틱에 걸쳐 천천히 — 때린 손맛은 남기고 조준은 방해하지 않습니다.
 *
 * 실제 시야각(xRot · yRot)은 건드리지 않고 화면 행렬만 돌리므로 조준점이 가리키는 곳은 그대로입니다.
 * 총을 쏘는 직업의 {@link Recoil} 과 달리 "맞히는 위치" 에 영향이 없습니다.
 */
public final class MeleePunch {
	/** 기울기 (도). */
	private static final float ROLL = 1.9F;
	/** 숙임 (도). */
	private static final float PITCH = 0.85F;
	/** 세게 치는 스킬은 더 크게. */
	private static final float HEAVY = 1.5F;
	private static final float RISE = 2.0F;
	private static final float FALL = 6.0F;

	/** 지금 세기 (0~1) 와 방향 (+1 = 시계, -1 = 반시계). */
	private static float power;
	private static float side = 1.0F;
	private static float scale = 1.0F;
	/** 마지막 반동이 시작된 시각 (1/20초 단위 연속 시계). */
	private static double startedAt = -1000;

	private MeleePunch() {}

	/** 애니메이션 패킷 — 내가 휘두른 것만. */
	public static void onAnim(SkillAnimPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || msg.entityId() != mc.player.getId()) {
			return;
		}
		float dir = direction(msg.anim());
		if (dir == 0.0F) {
			return;
		}
		side = dir;
		scale = heavy(msg.anim()) ? HEAVY : 1.0F;
		// 기 모으는 스킬은 누른 순간이 아니라 실제로 후려치는 순간에 흔들립니다 (0.2b)
		startedAt = kr.overbreak.client.ClientClock.now() + windup(msg.anim());
		power = 1.0F;
	}

	/**
	 * 후려치기까지의 선딜레이 (1/20초 단위) — 서버 쪽 기 모으는 시간과 같은 값입니다.
	 * 살육 {@code Slay.CHARGE} · 분쇄 {@code Smash.CHANNEL} · 강타 {@code HeavyBlow.WINDUP}.
	 */
	private static float windup(int anim) {
		return switch (anim) {
			case SkillAnimPayload.SLAY -> 14.0F;
			case SkillAnimPayload.HK_SMASH -> 10.0F;
			case SkillAnimPayload.BR_BLOW -> 6.0F;
			default -> 0.0F;
		};
	}

	public static void tick() {
		if (power > 0.0F && kr.overbreak.client.ClientClock.now() - startedAt > RISE + FALL) {
			power = 0.0F;
		}
	}

	/** 기 모으는 동안에는 아직 아무 일도 일어나지 않습니다. */
	private static boolean waiting(float e) {
		return e < 0.0F;
	}

	public static void clear() {
		power = 0.0F;
		startedAt = -1000;
	}

	/** 월드 화면 행렬에 겁니다 ({@link RollCamera} 와 같은 자리). partial 은 바닐라 부분 틱. */
	public static void apply(PoseStack pose, float partialTick) {
		if (power <= 0.0F) {
			return;
		}
		float e = (float) (kr.overbreak.client.ClientClock.at(partialTick) - startedAt);
		if (waiting(e)) {
			return;
		}
		float k = e < RISE ? e / RISE : 1.0F - (e - RISE) / FALL;
		if (k <= 0.0F) {
			return;
		}
		// 풀릴 때는 부드럽게 (제곱으로 꼬리를 길게)
		k = e < RISE ? Mth.sin(k * Mth.HALF_PI) : k * k;
		float amount = k * scale;
		pose.mulPose(Axis.ZP.rotationDegrees(side * ROLL * amount));
		pose.mulPose(Axis.XP.rotationDegrees(PITCH * amount));
	}

	/** 이 동작이 근접 타격인가, 그렇다면 어느 쪽으로 기우는가. 0 이면 반동 없음. */
	private static float direction(int anim) {
		return switch (anim) {
			// 두 방향 대각선 베기 — 화면도 그 방향을 따라갑니다
			case SkillAnimPayload.BASIC, SkillAnimPayload.BR_BASIC, SkillAnimPayload.SD_REND -> 1.0F;
			case SkillAnimPayload.BASIC_BACK, SkillAnimPayload.BR_BASIC_BACK -> -1.0F;
			// 한쪽으로만 휘두르는 큰 동작
			case SkillAnimPayload.SLAY, SkillAnimPayload.HK_SMASH, SkillAnimPayload.BR_BLOW,
				 SkillAnimPayload.SD_STRIKE -> 1.0F;
			case SkillAnimPayload.HK_SLAM, SkillAnimPayload.IF_PUNCH -> -1.0F;
			default -> 0.0F;
		};
	}

	private static boolean heavy(int anim) {
		return anim == SkillAnimPayload.SLAY || anim == SkillAnimPayload.HK_SMASH || anim == SkillAnimPayload.HK_SLAM
				|| anim == SkillAnimPayload.BR_BLOW || anim == SkillAnimPayload.IF_PUNCH;
	}
}
