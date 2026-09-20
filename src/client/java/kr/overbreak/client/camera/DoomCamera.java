package kr.overbreak.client.camera;

import kr.overbreak.net.DoomAimPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 파멸의 일격 조준 화면 (0.2a) — 오버워치 둠피스트 궁극기처럼, 조준하는 동안 강제로 3인칭이 되고
 * 시야가 지상의 붉은 착탄 원을 따라갑니다.
 *
 *   들어갈 때 : 원래 시점을 기억해 두고 3인칭으로. 1인칭이었다면 카메라가 뒤로 빠지며 벌어집니다
 *   조준 중   : 틱마다 시야를 착탄점 쪽으로 조금씩 돌립니다 (홱 돌지 않게)
 *   끝날 때   : 원래 시점으로. 1인칭으로 돌아갈 때는 카메라가 다시 붙을 때까지 기다렸다가 바꿉니다
 *
 * 이동 키가 원을 끄는 방향은 서버가 처음 각도로 고정해 두므로, 시야가 돌아도 조작이 따라 돌지 않습니다.
 */
public final class DoomCamera {
	/** 한 틱에 남은 각도의 몇 %를 좁히는가. */
	private static final float TURN_RATE = 0.22F;
	/** 3인칭으로 벌어지고 다시 붙는 데 걸리는 시간 (1/20초 단위). */
	private static final float BLEND = 5.0F;

	private static boolean active;
	private static Vec3 target = Vec3.ZERO;
	/** 시작할 때의 시점 — 끝나면 여기로 돌아갑니다. */
	private static CameraType previous = CameraType.FIRST_PERSON;
	/** 0 = 1인칭에 붙음, 1 = 완전히 벌어진 3인칭. */
	private static float open;
	/** 원래 1인칭이었나 (그렇다면 끝에 다시 붙여야 합니다). */
	private static boolean wasFirstPerson;

	private DoomCamera() {}

	public static void receive(DoomAimPayload msg) {
		Minecraft mc = Minecraft.getInstance();
		if (msg.active()) {
			target = new Vec3(msg.x(), msg.y(), msg.z());
			if (!active) {
				active = true;
				previous = mc.options.getCameraType();
				wasFirstPerson = previous.isFirstPerson();
				open = wasFirstPerson ? 0.0F : 1.0F;
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		} else {
			active = false;
		}
	}

	/** 클라이언트 틱마다. */
	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			reset(mc);
			return;
		}
		if (active) {
			open = Math.min(1.0F, open + 1.0F / BLEND);
			face(p);
			return;
		}
		if (open <= 0.0F) {
			return;
		}
		// 끝났습니다 — 1인칭이었다면 카메라가 등에 붙을 때까지 줄였다가 시점을 되돌립니다
		if (!wasFirstPerson) {
			open = 0.0F;
			mc.options.setCameraType(previous);
			return;
		}
		open = Math.max(0.0F, open - 1.0F / BLEND);
		if (open <= 0.0F) {
			mc.options.setCameraType(previous);
		}
	}

	/** 시야를 착탄점 쪽으로 조금씩. */
	private static void face(LocalPlayer p) {
		Vec3 d = target.subtract(p.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		if (d.lengthSqr() < 1.0E-6) {
			return;
		}
		float wantPitch = (float) (-(Mth.atan2(d.y, flat) * 180.0 / Math.PI));
		p.setXRot(p.getXRot() + Mth.wrapDegrees(wantPitch - p.getXRot()) * TURN_RATE);
		// 바로 아래를 겨누는 동안은 방향이 정해지지 않으므로 그때는 보던 쪽을 그대로 둡니다
		if (flat > 1.0) {
			float wantYaw = (float) (Mth.atan2(d.z, d.x) * 180.0 / Math.PI) - 90.0F;
			p.setYRot(p.getYRot() + Mth.wrapDegrees(wantYaw - p.getYRot()) * TURN_RATE);
		}
	}

	private static void reset(Minecraft mc) {
		if (active || open > 0.0F) {
			active = false;
			open = 0.0F;
			mc.options.setCameraType(previous);
		}
	}

	/** 지금 파멸의 일격이 카메라를 잡고 있는가 — 마우스로 돌릴 수 없습니다. */
	public static boolean holding() {
		return active;
	}

	/** 카메라를 얼마나 뒤로 뺄지 (0 = 등에 붙음, 1 = 평소 3인칭). */
	public static float zoom() {
		return active || open > 0.0F ? smooth(open) : 1.0F;
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}
}
