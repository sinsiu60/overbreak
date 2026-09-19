package kr.overbreak.client.anim.data;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Blockbench 애니메이션 → 게임 모델.
 *
 * 축 (overbreak_player.geo.json 과 같음)
 *   rotation: Blockbench 에 적힌 도 값 그대로 (라디안으로만 바꿈). 팔 x 음수 = 앞/위로 들기
 *   position: 픽셀. Blockbench 의 +y(위) 는 게임 모델의 -y 라서 y 만 뒤집음
 *   scale   : 배율
 *
 * 뼈대 관계도 Blockbench 틀과 같습니다: 두 팔은 몸통의 자식 (몸통을 돌리면 팔이 따라 돔),
 * 머리 · 다리는 따로 (머리는 시선, 다리는 걷기 동작을 유지하기 위해).
 * 키프레임이 없는 뼈대 · 채널은 바닐라 자세를 그대로 둡니다.
 */
public final class DataPose {
	public static final String HEAD = "head";
	public static final String BODY = "body";
	public static final String RIGHT_ARM = "right_arm";
	public static final String LEFT_ARM = "left_arm";
	public static final String RIGHT_LEG = "right_leg";
	public static final String LEFT_LEG = "left_leg";
	/** 1인칭 손에 든 아이템 (카메라 공간). */
	public static final String FIRST_PERSON_ITEM = "firstperson_item";
	/** 1인칭 받치는 손: 총 앞 손잡이 자리에서 옮김 (총 기준 픽셀). */
	public static final String FIRST_PERSON_LEFT_HAND = "firstperson_left_hand";
	/** 1인칭 손에 든 탄창 기울기 (도). */
	public static final String FIRST_PERSON_MAGAZINE = "firstperson_magazine";

	private static final float DEG = Mth.DEG_TO_RAD;

	private DataPose() {}

	/**
	 * 3인칭 모델에 애니메이션을 입힙니다. 바닐라 setupAnim 이 끝난 뒤 호출.
	 * @param seconds 애니메이션 시간 (초)
	 * @param w       상체 · 위치 가중치
	 * @param legW    다리 회전 가중치 (걷는 중이면 0)
	 */
	public static void apply(HumanoidModel<?> m, PlayerAnimation anim, double seconds, float ageInTicks, float w, float legW) {
		if (w <= 0.0F) {
			return;
		}
		Molang.Context c = new Molang.Context();
		c.lifeTime = ageInTicks * kr.overbreak.core.tick.Ticks.step() / 20.0;
		c.headX = m.head.xRot / DEG;
		c.headY = m.head.yRot / DEG;

		// 몸통 — 팔이 따라갈 부모 회전: 몸통 키프레임이 있으면 그 값, 없으면 바닐라의 좌우 비틀기만 (웅크리기 기울기는 팔에 안 넘김)
		float vbx = m.body.xRot, vby = m.body.yRot, vbz = m.body.zRot;
		float vpx = m.body.x, vpy = m.body.y, vpz = m.body.z;
		double[] bodyRot = anim.sample(BODY, PlayerAnimation.ROTATION, seconds, c);
		double[] bodyPos = anim.sample(BODY, PlayerAnimation.POSITION, seconds, c);
		Quaternionf parentVanilla = new Quaternionf().rotationZYX(0.0F, vby, 0.0F);
		Quaternionf parentTarget = bodyRot == null ? parentVanilla
				: new Quaternionf().rotationZYX((float) bodyRot[2] * DEG, (float) bodyRot[1] * DEG, (float) bodyRot[0] * DEG);
		float tbx = vpx, tby = vpy, tbz = vpz;
		if (bodyPos != null) {
			tbx += (float) bodyPos[0];
			tby -= (float) bodyPos[1];
			tbz += (float) bodyPos[2];
		}
		if (bodyRot != null) {
			m.body.xRot = lerpAngle(w, vbx, (float) bodyRot[0] * DEG);
			m.body.yRot = lerpAngle(w, vby, (float) bodyRot[1] * DEG);
			m.body.zRot = lerpAngle(w, vbz, (float) bodyRot[2] * DEG);
		}
		if (bodyPos != null) {
			m.body.x = Mth.lerp(w, vpx, tbx);
			m.body.y = Mth.lerp(w, vpy, tby);
			m.body.z = Mth.lerp(w, vpz, tbz);
		}
		scale(m.body, anim, BODY, seconds, c, w);

		boolean bodyMoved = bodyRot != null || bodyPos != null;
		arm(m.rightArm, anim, RIGHT_ARM, seconds, c, w, bodyMoved, parentVanilla, parentTarget, vpx, vpy, vpz, tbx, tby, tbz);
		arm(m.leftArm, anim, LEFT_ARM, seconds, c, w, bodyMoved, parentVanilla, parentTarget, vpx, vpy, vpz, tbx, tby, tbz);

		free(m.head, anim, HEAD, seconds, c, w, w);
		free(m.rightLeg, anim, RIGHT_LEG, seconds, c, legW, w);
		free(m.leftLeg, anim, LEFT_LEG, seconds, c, legW, w);
	}

	/** 몸통의 자식 뼈대 (팔). */
	private static void arm(ModelPart part, PlayerAnimation anim, String bone, double seconds, Molang.Context c, float w, boolean bodyMoved,
			Quaternionf parentVanilla, Quaternionf parentTarget, float vpx, float vpy, float vpz, float tbx, float tby, float tbz) {
		double[] rot = anim.sample(bone, PlayerAnimation.ROTATION, seconds, c);
		double[] pos = anim.sample(bone, PlayerAnimation.POSITION, seconds, c);
		if (rot != null || pos != null || bodyMoved) {
			Quaternionf inverse = new Quaternionf(parentVanilla).conjugate();
			Quaternionf local = rot != null
					? new Quaternionf().rotationZYX((float) rot[2] * DEG, (float) rot[1] * DEG, (float) rot[0] * DEG)
					: new Quaternionf(inverse).mul(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
			Quaternionf world = new Quaternionf(parentTarget).mul(local);
			Vector3f e = world.getEulerAnglesZYX(new Vector3f());
			float[] t = nearest(e, part.xRot, part.yRot, part.zRot);

			// 어깨 위치: 바닐라에서 몸통 기준 자리를 구해 새 몸통 회전으로 돌림
			Vector3f offset = inverse.transform(new Vector3f(part.x - vpx, part.y - vpy, part.z - vpz));
			if (pos != null) {
				offset.add((float) pos[0], (float) -pos[1], (float) pos[2]);
			}
			parentTarget.transform(offset);
			part.xRot = Mth.lerp(w, part.xRot, t[0]);
			part.yRot = Mth.lerp(w, part.yRot, t[1]);
			part.zRot = Mth.lerp(w, part.zRot, t[2]);
			part.x = Mth.lerp(w, part.x, tbx + offset.x);
			part.y = Mth.lerp(w, part.y, tby + offset.y);
			part.z = Mth.lerp(w, part.z, tbz + offset.z);
		}
		scale(part, anim, bone, seconds, c, w);
	}

	/** 부모 없는 뼈대 (머리 · 다리). */
	private static void free(ModelPart part, PlayerAnimation anim, String bone, double seconds, Molang.Context c, float rotW, float posW) {
		double[] rot = anim.sample(bone, PlayerAnimation.ROTATION, seconds, c);
		if (rot != null && rotW > 0.0F) {
			part.xRot = lerpAngle(rotW, part.xRot, (float) rot[0] * DEG);
			part.yRot = lerpAngle(rotW, part.yRot, (float) rot[1] * DEG);
			part.zRot = lerpAngle(rotW, part.zRot, (float) rot[2] * DEG);
		}
		double[] pos = anim.sample(bone, PlayerAnimation.POSITION, seconds, c);
		if (pos != null) {
			part.x += (float) pos[0] * posW;
			part.y -= (float) pos[1] * posW;
			part.z += (float) pos[2] * posW;
		}
		scale(part, anim, bone, seconds, c, posW);
	}

	private static void scale(ModelPart part, PlayerAnimation anim, String bone, double seconds, Molang.Context c, float w) {
		double[] s = anim.sample(bone, PlayerAnimation.SCALE, seconds, c);
		if (s != null) {
			part.xScale *= Mth.lerp(w, 1.0F, (float) s[0]);
			part.yScale *= Mth.lerp(w, 1.0F, (float) s[1]);
			part.zScale *= Mth.lerp(w, 1.0F, (float) s[2]);
		}
	}

	/**
	 * 1인칭 손 아이템 자세. firstperson_item 뼈대가 없으면 false.
	 * 바닐라 기본 손 자리 (0.56, -0.52, -0.72) 에서 position(픽셀/16) 만큼 옮기고 Y → X → Z 순서로 돌림.
	 * 본편이 끝나면(end) fade 동안 기본 자리로 돌아옵니다.
	 */
	public static boolean firstPerson(PoseStack pose, int invert, PlayerAnimation anim, float e, float end, float fade, float ageInTicks) {
		if (!anim.has(FIRST_PERSON_ITEM)) {
			return false;
		}
		Molang.Context c = new Molang.Context();
		c.lifeTime = ageInTicks * kr.overbreak.core.tick.Ticks.step() / 20.0;
		double seconds = Math.min(e, end) / 20.0;
		double[] pos = anim.sample(FIRST_PERSON_ITEM, PlayerAnimation.POSITION, seconds, c);
		double[] rot = anim.sample(FIRST_PERSON_ITEM, PlayerAnimation.ROTATION, seconds, c);
		double[] scl = anim.sample(FIRST_PERSON_ITEM, PlayerAnimation.SCALE, seconds, c);
		float k = 1.0F;
		if (e > end) {
			float q = Mth.clamp((e - end) / fade, 0.0F, 1.0F);
			k = 1.0F - q * q * (3.0F - 2.0F * q);
		}
		float px = pos == null ? 0.0F : (float) pos[0] / 16.0F * k;
		float py = pos == null ? 0.0F : (float) pos[1] / 16.0F * k;
		float pz = pos == null ? 0.0F : (float) pos[2] / 16.0F * k;
		pose.translate(invert * (0.56F + px), -0.52F + py, -0.72F + pz);
		if (rot != null) {
			pose.mulPose(Axis.YP.rotationDegrees(invert * (float) rot[1] * k));
			pose.mulPose(Axis.XP.rotationDegrees((float) rot[0] * k));
			pose.mulPose(Axis.ZP.rotationDegrees(invert * (float) rot[2] * k));
		}
		if (scl != null) {
			pose.scale(Mth.lerp(k, 1.0F, (float) scl[0]), Mth.lerp(k, 1.0F, (float) scl[1]), Mth.lerp(k, 1.0F, (float) scl[2]));
		}
		return true;
	}

	/** 목표 각을 지금 각에서 가장 가까운 한 바퀴 안으로 (예: 350도 → -10도). */
	private static float lerpAngle(float w, float from, float to) {
		return Mth.lerp(w, from, near(to, from));
	}

	private static float near(float v, float ref) {
		while (v - ref > Mth.PI) {
			v -= Mth.TWO_PI;
		}
		while (v - ref < -Mth.PI) {
			v += Mth.TWO_PI;
		}
		return v;
	}

	/** 같은 회전을 뜻하는 두 각도 묶음 중 지금 자세에 가까운 쪽 (섞는 도중 팔이 한 바퀴 돌지 않게). */
	private static float[] nearest(Vector3f e, float rx, float ry, float rz) {
		float[] a = {near(e.x, rx), near(e.y, ry), near(e.z, rz)};
		float[] b = {near(e.x + Mth.PI, rx), near(Mth.PI - e.y, ry), near(e.z + Mth.PI, rz)};
		float da = Math.abs(a[0] - rx) + Math.abs(a[1] - ry) + Math.abs(a[2] - rz);
		float db = Math.abs(b[0] - rx) + Math.abs(b[1] - ry) + Math.abs(b[2] - rz);
		return da <= db ? a : b;
	}
}
