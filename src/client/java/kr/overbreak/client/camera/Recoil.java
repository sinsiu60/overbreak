package kr.overbreak.client.camera;

import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * 화면 반동 — 내가 쏠 때 시야가 살짝 위로 튀었다가 절반쯤 돌아옵니다.
 *
 *   발키리 연사 0.45도 · 전술 로켓 1.8도 · 탄막 포격 한 발 0.3도
 *   튀는 양은 2틱에 나눠 올리고, 쏘기를 멈추면 60% 만큼 천천히 되돌립니다.
 *   시야를 실제로 움직이므로 조준점도 함께 올라갑니다 (끌어내려야 계속 맞음).
 *
 * 틱 끝에 xRot 만 바꾸고 이전 틱 값은 두므로, 프레임 사이는 바닐라 보간으로 부드럽게 이어집니다.
 */
public final class Recoil {
	private static final float SHOT = 0.45F;
	private static final float ROCKET = 1.8F;
	private static final float BARRAGE = 0.3F;
	private static final float RECOVER = 0.6F;
	private static final float PEACEKEEPER = 1.2F;
	private static final float FAN = 3.0F;
	private static final float FAN_YAW = 1.0F;

	private static float pendingYaw;
	private static int fanShots;

	private static float pending;
	private static float recover;
	/** 마지막 반동 뒤 지난 시간 (1/20초 단위). */
	private static float sinceShot;
	private static long lastBarrageShot = -1;

	private Recoil() {}

	/** 애니메이션 패킷 — 내가 쏜 것만 반동. */
	public static void onAnim(SkillAnimPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || msg.entityId() != mc.player.getId()) {
			return;
		}
		if (msg.anim() == SkillAnimPayload.VK_SHOT) {
			kick(SHOT);
		} else if (msg.anim() == SkillAnimPayload.VK_ROCKET) {
			kick(ROCKET);
		} else if (msg.anim() == SkillAnimPayload.SH_SHOT) {
			kick(PEACEKEEPER);
		} else if (msg.anim() == SkillAnimPayload.SH_FAN) {
			// 리볼버 난사: 한 발마다 3도 위로, 좌우는 번갈아 1도 (익히면 손으로 되잡을 수 있는 정해진 모양)
			fanShots = sinceShot > 10 ? 1 : fanShots + 1;
			kick(FAN);
			pendingYaw += fanShots % 2 == 0 ? FAN_YAW : -FAN_YAW;
		}
		// 황야의 무법자 발사는 화면을 흔들지 않고 총 반동 모션(sheriff.deadeye_fire)으로 보여 줍니다
	}

	private static void kick(float degrees) {
		pending += degrees;
		sinceShot = 0;
	}

	/** 시간 1 동안 fraction 만큼 옮기는 것을 dt 동안으로 (1 - (1 - f)^dt). */
	private static float rate(float fraction, float dt) {
		return (float) (1.0 - Math.pow(1.0 - fraction, dt));
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused()) {
			if (p == null) {
				pending = 0.0F;
				recover = 0.0F;
			}
			return;
		}
		// 탄막 포격: 기 모으기(14틱) 뒤 2틱마다 한 발
		long t = SkillAnims.elapsedTicks(p.getId(), SkillAnimPayload.VK_BARRAGE);
		if (t >= 14 && t % 2 == 1 && t != lastBarrageShot && SkillAnims.playing(p.getId(), SkillAnimPayload.VK_BARRAGE)) {
			lastBarrageShot = t;
			kick(BARRAGE);
		}
		// 틱레이트가 달라도 1초에 같은 만큼 되돌아오게: 시간 단위로 세고, 틱마다 옮기는 비율은 거듭제곱으로 나눔
		float dt = (float) kr.overbreak.core.tick.Ticks.step();
		sinceShot += dt;
		if (pendingYaw != 0.0F) {
			float ys = Math.abs(pendingYaw) < 0.1F * dt ? pendingYaw : pendingYaw * rate(0.6F, dt);
			p.setYRot(p.getYRot() + ys);
			pendingYaw -= ys;
		}
		if (pending > 0.0F) {
			float step = Math.min(pending, Math.max(0.2F * dt, pending * rate(0.6F, dt)));
			float yaw = (p.getRandom().nextFloat() - 0.5F) * 0.16F * dt;
			p.setXRot(Mth.clamp(p.getXRot() - step, -90.0F, 90.0F));
			p.setYRot(p.getYRot() + yaw);
			pending -= step;
			recover += step * RECOVER;
		} else if (sinceShot > 3 && recover > 0.01F) {
			float step = Math.min(recover, Math.max(0.05F * dt, recover * rate(0.25F, dt)));
			p.setXRot(Mth.clamp(p.getXRot() + step, -90.0F, 90.0F));
			recover -= step;
		}
	}
}
