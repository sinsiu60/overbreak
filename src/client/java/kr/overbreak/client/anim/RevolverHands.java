package kr.overbreak.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import kr.overbreak.client.anim.data.DataPose;
import kr.overbreak.client.anim.data.Molang;
import kr.overbreak.client.anim.data.PlayerAnimation;
import kr.overbreak.client.anim.data.PlayerAnimations;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * 1인칭 보안관 리볼버 — 왼손 · 왼손에 든 물건 · 젖힌 탄창의 포즈를 계산합니다 (그리기는 ItemInHandRendererMixin).
 *
 *   왼손: 평소에는 안 보이고, 난사 · 재장전 · 섬광 · 황야의 무법자 동작 중에만 화면 왼쪽 아래 밖(REST)에서 들어옴.
 *         애니메이션 파일의 firstperson_left_hand (카메라 기준 픽셀) 만큼 옮긴 자리로 어깨 쪽에서 곧게 뻗은 팔을 그림
 *   탄창: v.cylinder_out 동안 firstperson_cylinder 의 x(크레인 축으로 젖힘) · z(탄창 굴림) 대로 따로 그림
 *   좌표: 총 모델 픽셀 → 손 기준은 리볼버 1인칭 표시 변환 {@link #DISPLAY} (모델 json 과 같은 값)
 */
public final class RevolverHands {
	/** revolver.json firstperson_righthand: rotation [-1, 9.5, 186.5] · translation [0.484, -0.881, -2.716] · scale 0.816 (오버워치 캐서디 1인칭 자리, 총 가운데를 축으로 20도 내리고 손과 함께 오른쪽 아래로 밀어 넣음). */
	private static final Matrix4f DISPLAY = new Matrix4f()
			.translation(0.484F / 16.0F, -0.881F / 16.0F, -2.716F / 16.0F)
			.rotateXYZ((float) Math.toRadians(-1.0), (float) Math.toRadians(9.5), (float) Math.toRadians(186.5))
			.scale(0.816F)
			.translate(-0.5F, -0.5F, -0.5F);
	private static final Matrix4f DISPLAY_INV = new Matrix4f(DISPLAY).invert();
	/** 탄창 크레인 축 (모델 픽셀) — 몸통 오른쪽 아래, 총열과 나란히. */
	private static final Vector3f CRANE = new Vector3f(9.9F, 4.0F, 0.0F).div(16.0F);
	/** 탄창 가운데 (모델 픽셀). */
	private static final Vector3f CYLINDER = new Vector3f(8.0F, 2.2F, 0.0F).div(16.0F);
	/** 총구 끝 (모델 픽셀). */
	private static final Vector3f MUZZLE_MODEL = new Vector3f(8.0F, 1.8F, -10.8F).div(16.0F);

	/** 왼손이 쉬는 자리 (카메라 기준, 화면 왼쪽 아래 밖). 총 위치를 옮기면 같은 만큼 옮겨 손 자리가 총에 맞게 유지됩니다. */
	private static final Vector3f REST = new Vector3f(-0.4467F, -0.9827F, -0.88F);
	/** 왼팔 어깨 쪽 (카메라 기준). */
	private static final Vector3f SHOULDER = new Vector3f(-0.55F, -1.15F, -0.35F);
	private static final float ARM_SCALE = 0.6F;

	/** 오른팔 손끝을 옮기는 양 (블록, 주 손 기준 x) — 총 손잡이 윗부분에 주먹이 오도록. 총 1인칭 표시 변환을 바꾸면 같이 맞춰 주세요. */
	private static final Vector3f GRIP_SHIFT = new Vector3f(-0.3146F, 0.0339F, 0.0195F);
	/** 오른팔을 손끝을 축으로 오른쪽으로 꺾는 각도 (도) — 팔 방향을 총 손잡이 각도에 맞춤. */
	private static final float ARM_TILT = 60.0F;
	/** 오른팔을 손끝을 축으로 화면 위쪽으로 드는 각도 (도) — 어깨 쪽이 올라와 팔뚝이 앞으로 들림. */
	private static final float ARM_RAISE = 30.0F;
	/**
	 * 바닐라 1인칭 오른팔 그리기(ItemInHandRenderer.renderPlayerArm, 휘두르기 0)가 쌓는 변환에서 본 손끝 자리.
	 * 팔 모델: 어깨 기준점 (-5, 2) · zRot 0.1 · 상자 y -2 ~ 10 → 손끝 (-1, 10).
	 */
	private static final Vector3f RIGHT_TIP = new Matrix4f()
			.translation(0.64F, -0.6F, -0.72F)
			.rotateY((float) Math.toRadians(45.0))
			.translate(-1.0F, 3.6F, 3.5F)
			.rotateZ((float) Math.toRadians(120.0))
			.rotateX((float) Math.toRadians(200.0))
			.rotateY((float) Math.toRadians(-135.0))
			.translate(5.6F, 0.0F, 0.0F)
			.translate(-5.0F / 16.0F, 2.0F / 16.0F, 0.0F)
			.rotateZ(0.1F)
			.transformPosition(new Vector3f(-1.0F / 16.0F, 10.0F / 16.0F, 0.0F));

	public static final String LEFT_ITEM = "firstperson_left_item";
	public static final String CYLINDER_BONE = "firstperson_cylinder";
	public static final String SPIN_BONE = "firstperson_item_spin";

	/** 그릴 것들 — 없으면 null. 모두 손 기준점이 없는 기본 포즈 위에 곱합니다. */
	public record Pose(@Nullable Matrix4f leftArm, @Nullable Matrix4f leftItem, @Nullable ItemStack leftStack, @Nullable Matrix4f cylinder) {
		static final Pose NONE = new Pose(null, null, null, null);
	}

	private RevolverHands() {}

	/**
	 * 리볼버를 쥔 주 손 팔 포즈 — 두 손 총과 같은 손잡이 자리(FirstPersonAnim.gunGrip)에서 총과 함께 왼쪽으로 옮기고,
	 * 손끝을 축으로 화면 기준 오른쪽으로 꺾어(어깨 쪽이 오른쪽으로) 팔이 손잡이 각도를 따라가게 합니다. 손끝 자리는 그대로.
	 */
	public static void gripArm(PoseStack pose, int invert) {
		FirstPersonAnim.gunGrip(pose, invert, false);
		pose.translate(invert * GRIP_SHIFT.x, GRIP_SHIFT.y, GRIP_SHIFT.z);
		pose.translate(invert * RIGHT_TIP.x, RIGHT_TIP.y, RIGHT_TIP.z);
		pose.mulPose(Axis.ZP.rotationDegrees(invert * ARM_TILT));
		pose.mulPose(Axis.XP.rotationDegrees(-ARM_RAISE));
		pose.translate(-invert * RIGHT_TIP.x, -RIGHT_TIP.y, -RIGHT_TIP.z);
	}

	/** FirstPersonAnim.gunGrip(주 손) 이 옮기는 양 — (-0.56 + 0.06, 0.52 - 0.08, 0.72 + 0.06), x 는 주 손 방향을 곱함. */
	private static final Vector3f GRIP_OFFSET = new Vector3f(-0.50F, 0.44F, 0.78F);

	/**
	 * 주 손 팔을 그릴 기준 포즈 (손 기준점이 없는 기본 포즈 위에 곱함).
	 * 팔은 평소(동작 없는) 총 자리 기준으로 그리고, 손끝이 지금 총 손잡이 자리로 오도록 위치만 옮깁니다 —
	 * 재장전 · 구르기처럼 총이 크게 기울거나 돌아도 팔 방향은 그대로라 팔의 어깨 쪽 끝이 화면에 들어오지 않습니다.
	 * @param rel 기본 포즈 → 지금 총 포즈
	 */
	public static Matrix4f armBase(Matrix4f rel, int invert) {
		Vector3f tipGun = gripTip(invert);
		Matrix4f idle = new Matrix4f().translation(invert * 0.56F, -0.52F, -0.72F);
		Vector3f delta = rel.transformPosition(new Vector3f(tipGun)).sub(idle.transformPosition(new Vector3f(tipGun)));
		return new Matrix4f().translation(delta).mul(idle);
	}

	/** 주 손 손끝 (방아쇠 손가락) 자리 — 총 기준 좌표 (동작 없는 총 자리의 기본 손 자리가 원점). */
	private static Vector3f gripTip(int invert) {
		return new Vector3f(invert * (GRIP_OFFSET.x + GRIP_SHIFT.x + RIGHT_TIP.x), GRIP_OFFSET.y + GRIP_SHIFT.y + RIGHT_TIP.y, GRIP_OFFSET.z + GRIP_SHIFT.z + RIGHT_TIP.z);
	}

	/**
	 * firstperson_item_spin: 총을 방아쇠 손가락(주 손 손끝)을 축으로 돌리는 회전 — 총 자세 맨 뒤에 곱합니다.
	 * position(픽셀, -x = 손바닥 쪽)은 도는 자리를 옮김 — 총이 주먹과 겹치지 않고 주먹 안쪽에서 돌게.
	 * 주 손 팔은 이 회전을 빼고 따라가므로, 총만 크게 돌고 팔은 firstperson_item 만큼만 움직입니다.
	 * @return 이 뼈대가 없거나 동작이 끝났으면 null
	 */
	public static @Nullable Matrix4f spin(SkillAnims.Play play, int invert, float partial) {
		PlayerAnimation anim = PlayerAnimations.forSkill(play.anim);
		if (anim == null || !anim.has(SPIN_BONE)) {
			return null;
		}
		float e = play.elapsed(partial);
		float end = play.end();
		float fade = SkillAnims.fade(play.anim);
		if (e >= end + fade) {
			return null;
		}
		double seconds = Math.min(e, end) / 20.0;
		double[] r = anim.sample(SPIN_BONE, PlayerAnimation.ROTATION, seconds, new Molang.Context());
		double[] t = anim.sample(SPIN_BONE, PlayerAnimation.POSITION, seconds, new Molang.Context());
		if (r == null && t == null) {
			return null;
		}
		float k = 1.0F;
		if (e > end) {
			float q = Mth.clamp((e - end) / fade, 0.0F, 1.0F);
			k = 1.0F - q * q * (3.0F - 2.0F * q);
		}
		Vector3f p = gripTip(invert);
		Matrix4f m = new Matrix4f();
		if (t != null) {
			m.translation(invert * (float) t[0] / 16.0F * k, (float) t[1] / 16.0F * k, (float) t[2] / 16.0F * k);
		}
		m.translate(p);
		if (r != null) {
			m.rotateY(invert * (float) r[1] * k * Mth.DEG_TO_RAD)
					.rotateX((float) r[0] * k * Mth.DEG_TO_RAD)
					.rotateZ(invert * (float) r[2] * k * Mth.DEG_TO_RAD);
		}
		return m.translate(-p.x, -p.y, -p.z);
	}

	/** 1인칭 총구 끝 (손 기준, 주 손이 오른손일 때). */
	public static Vector3f muzzle() {
		return DISPLAY.transformPosition(new Vector3f(MUZZLE_MODEL));
	}

	/**
	 * @param rel 기본 포즈 → 총 포즈
	 */
	public static Pose compute(Matrix4f rel, int invert, int playerId, float partial) {
		SkillAnims.Play play = SkillAnims.latestOf(playerId, SkillAnimPayload.SH_RELOAD, SkillAnimPayload.SH_FLASH, SkillAnimPayload.SH_FAN,
				SkillAnimPayload.SH_DEADEYE, SkillAnimPayload.SH_DEADEYE_FIRE);
		if (play == null) {
			return Pose.NONE;
		}
		PlayerAnimation anim = PlayerAnimations.forSkill(play.anim);
		if (anim == null) {
			return Pose.NONE;
		}
		float e = play.elapsed(partial);
		float end = play.end();
		float fade = SkillAnims.fade(play.anim);
		if (e >= end + fade) {
			return Pose.NONE;
		}
		float k = 1.0F;
		if (e > end) {
			float q = Mth.clamp((e - end) / fade, 0.0F, 1.0F);
			k = 1.0F - q * q * (3.0F - 2.0F * q);
		}
		double seconds = Math.min(e, end) / 20.0;
		Molang.Context c = new Molang.Context();
		c.lifeTime = e / 20.0;

		Matrix4f arm = null;
		Matrix4f item = null;
		ItemStack stack = null;
		double[] hand = anim.sample(DataPose.FIRST_PERSON_LEFT_HAND, PlayerAnimation.POSITION, seconds, c);
		if (hand != null) {
			Vector3f at = new Vector3f(
					invert * (REST.x + (float) hand[0] / 16.0F * k),
					REST.y + (float) hand[1] / 16.0F * k,
					REST.z + (float) hand[2] / 16.0F * k);
			Vector3f shoulder = new Vector3f(invert * SHOULDER.x, SHOULDER.y, SHOULDER.z);
			arm = armMatrix(at, shoulder, invert);
			boolean loader = play.anim == SkillAnimPayload.SH_RELOAD && e < end && anim.variable(SheriffAnim.LOADER, seconds, 0.0) >= 0.5;
			boolean flash = play.anim == SkillAnimPayload.SH_FLASH && e < end && anim.variable(SheriffAnim.FLASH_HELD, seconds, 0.0) >= 0.5;
			if (loader || flash) {
				stack = loader ? SheriffAnim.loaderStack() : SheriffAnim.flashbangStack();
				double[] r = anim.sample(LEFT_ITEM, PlayerAnimation.ROTATION, seconds, c);
				item = new Matrix4f().translation(at).translate(invert * 0.02F, 0.05F, -0.04F);
				if (r != null) {
					item.rotateY(invert * (float) r[1] * Mth.DEG_TO_RAD).rotateX((float) r[0] * Mth.DEG_TO_RAD).rotateZ(invert * (float) r[2] * Mth.DEG_TO_RAD);
				}
			}
		}

		Matrix4f cylinder = null;
		if (play.anim == SkillAnimPayload.SH_RELOAD && invert > 0 && e < end && anim.variable(SheriffAnim.CYLINDER_OUT, seconds, 0.0) >= 0.5) {
			double[] r = anim.sample(CYLINDER_BONE, PlayerAnimation.ROTATION, seconds, c);
			float swing = r == null ? 90.0F : (float) r[0];
			float spin = r == null ? 0.0F : (float) r[2];
			Matrix4f model = new Matrix4f()
					.translation(CRANE).rotateZ(swing * Mth.DEG_TO_RAD).translate(-CRANE.x, -CRANE.y, -CRANE.z)
					.translate(CYLINDER).rotateZ(spin * Mth.DEG_TO_RAD).translate(-CYLINDER.x, -CYLINDER.y, -CYLINDER.z);
			cylinder = new Matrix4f(rel).mul(DISPLAY).mul(model).mul(DISPLAY_INV);
		}
		return new Pose(arm, item, stack, cylinder);
	}

	/** 어깨 쪽에서 손 자리로 곧게 뻗은 팔 (팔 안쪽이 위). FirstPersonAnim.gunHands 와 같은 계산. */
	static Matrix4f armMatrix(Vector3f hand, Vector3f shoulder, int invert) {
		Vector3f dir = new Vector3f(hand).sub(shoulder).normalize();
		Vector3f side = new Vector3f(0.0F, 1.0F, 0.0F).sub(new Vector3f(dir).mul(dir.y));
		if (side.lengthSquared() < 1.0E-6F) {
			side.set(1.0F, 0.0F, 0.0F);
		}
		side.normalize().mul(-invert);
		Vector3f depth = new Vector3f(side).cross(dir);
		Matrix3f target = new Matrix3f(side, dir, depth);
		Matrix3f lean = new Matrix3f().rotationZ(-0.1F * invert);
		Matrix3f turn = new Matrix3f(target).mul(new Matrix3f(lean).transpose());
		Vector3f palm = lean.transform(new Vector3f(invert, 8.5F, 0.0F)).add(5.0F * invert, 2.0F, 0.0F).div(16.0F);
		return new Matrix4f().translation(hand).mul(new Matrix4f().set(turn)).scale(ARM_SCALE).translate(-palm.x, -palm.y, -palm.z);
	}
}
