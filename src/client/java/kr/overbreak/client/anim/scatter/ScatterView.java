package kr.overbreak.client.anim.scatter;

import static kr.overbreak.classes.gunslinger.DashScatter.DASH_START;
import static kr.overbreak.classes.gunslinger.DashScatter.RECOVER_START;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import kr.overbreak.client.anim.AnimRenderState;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.client.anim.scatter.ScatterData.Ease;
import kr.overbreak.net.ScatterPayload;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;

/**
 * 돌진 난사 3인칭 — 렌더에 붙이는 자리 (레이어 합성 순서는 스펙 PART 9).
 *
 *   extract : 자세를 계산해 렌더 상태에 싣고, 모델이 바라보는 방향(몸통 yaw)을 대쉬 방향 + 루트 회전으로 바꿈
 *   pose    : 모델 팔 · 다리 · 상체 · 머리를 통째로 바꿈 (바닐라 흔들림 · 걷기 · 웅크리기 무시)
 *   root    : 엉덩이 높이를 축으로 모델 전체 pitch · roll · Y (setupRotations)
 *   gunRoll : 손에 든 총 기울기 · 스핀
 *
 * 실제 시선(yaw · pitch)은 건드리지 않습니다 — 렌더 표현만.
 */
public final class ScatterView {
	/** 시드 · 대쉬 방향 (엔티티별, 서버가 보냄). */
	private record Params(int seed, float dashYaw) {}

	private static final Map<Integer, Params> PARAMS = new HashMap<>();

	private ScatterView() {}

	public static void receive(ScatterPayload msg) {
		PARAMS.put(msg.entityId(), new Params(msg.seed(), msg.dashYaw()));
		ScatterBody.forget(msg.entityId());
	}

	/** 시드 · 대쉬 방향 — 아직 못 받았으면(본인 예측 재생 첫 순간) 엔티티 id · 지금 방향으로 임시. */
	static Params params(int id, float fallbackYaw) {
		Params p = PARAMS.get(id);
		return p != null ? p : new Params(id, fallbackYaw);
	}

	public static int seed(int id) {
		Params p = PARAMS.get(id);
		return p == null ? id : p.seed();
	}

	public static float dashYaw(int id, float fallback) {
		Params p = PARAMS.get(id);
		return p == null ? fallback : p.dashYaw();
	}

	/** 스킬 동안의 가중치 — 끝나거나 끊기면 0.1초에 걸쳐 바닐라로. */
	static float weight(SkillAnims.Play play, float e) {
		float in = Mth.clamp(e / 1.0F, 0.0F, 1.0F);
		float out = 1.0F - Mth.clamp((e - play.end()) / SkillAnims.fade(play.anim), 0.0F, 1.0F);
		return Math.min(in, out);
	}

	/** 모델이 바라볼 월드 yaw (대쉬로 돌아섬 · 루트 회전 · 휘청임 · 마무리에서 실제 방향으로). */
	static float bodyYaw(ScatterBody.Pose p, float t, float dashYaw, float actual) {
		if (t < DASH_START) {
			return actual;
		}
		float spun = dashYaw + p.rootYaw() + p.wobbleYaw();
		if (t < RECOVER_START) {
			float x = Ease.QUAD_OUT.apply((t - DASH_START) / ScatterData.units(ScatterData.dashSnap));
			return x >= 1.0F ? spun : actual + Mth.wrapDegrees(spun - actual) * x;
		}
		float x = Ease.CUBIC_OUT.apply((t - RECOVER_START) / ScatterData.units(ScatterData.recoverTurn));
		return spun + Mth.wrapDegrees(actual - spun) * x;
	}

	/** 렌더 상태 추출 — 돌진 난사가 재생 중이면 true (다른 스킬 동작 처리를 건너뜀). */
	public static boolean extract(Avatar entity, AvatarRenderState state, float partial) {
		SkillAnims.Play play = SkillAnims.find(entity.getId(), SkillAnimPayload.GS_SCATTER);
		AnimRenderState a = (AnimRenderState) state;
		if (play == null || SkillAnims.latest(entity.getId()) != play) {
			a.overbreak$setScatter(null, 0.0F);
			return false;
		}
		float e = play.elapsed(partial);
		float w = weight(play, e);
		if (w <= 0.0F) {
			a.overbreak$setScatter(null, 0.0F);
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean lod = mc.gameRenderer.mainCamera().isInitialized()
				&& mc.gameRenderer.mainCamera().position().distanceTo(entity.position()) >= ScatterData.lodDistance;
		Params prm = params(entity.getId(), state.bodyRot);
		ScatterBody.Pose p = ScatterBody.pose(entity.getId(), prm.seed(), e, entity.onGround(), lod);
		float t = Math.min(e, play.end());
		float actual = state.bodyRot;
		float target = bodyYaw(p, t, prm.dashYaw(), actual);
		float yaw = actual + Mth.wrapDegrees(target - actual) * w;
		if (w >= 1.0F) {
			yaw = target;
		}
		// 머리의 바닐라 값(몸통 기준 시선)이 실제 시선을 계속 가리키도록 몸을 돌린 만큼 되돌려 둠
		state.yRot += actual - yaw;
		state.bodyRot = yaw;
		a.overbreak$setScatter(p, w);
		a.overbreak$set(SkillAnimPayload.GS_SCATTER, e, play.end(), w);
		a.overbreak$setSpin(0.0F);
		a.overbreak$setItemScale(1.0F);
		return true;
	}

	/** 모델 자세 (setupAnim 끝). @return 돌진 난사 자세를 넣었으면 true */
	public static boolean pose(HumanoidModel<?> m, AvatarRenderState state) {
		AnimRenderState a = (AnimRenderState) state;
		ScatterBody.Pose p = a.overbreak$scatter();
		if (p == null) {
			return false;
		}
		float w = a.overbreak$scatterWeight();
		float d = Mth.DEG_TO_RAD;
		float hw = w * p.headWeight();
		m.head.xRot = Mth.lerp(hw, m.head.xRot, p.headPitch() * d);
		m.head.yRot = Mth.lerp(hw, m.head.yRot, p.headYaw() * d);
		m.head.zRot = Mth.lerp(w, m.head.zRot, 0.0F);
		float tw = p.twist() * d;
		m.body.xRot = Mth.lerp(w, m.body.xRot, 0.0F);
		m.body.yRot = Mth.lerp(w, m.body.yRot, tw);
		m.body.zRot = Mth.lerp(w, m.body.zRot, 0.0F);
		// 팔: 어깨 자리를 상체 비틀림만큼 돌림 (바닐라 휘두르기와 같은 식) · 팔 방향에도 비틀림을 얹음
		float ax = -Mth.cos(tw) * 5.0F;
		float az = Mth.sin(tw) * 5.0F;
		m.rightArm.x = Mth.lerp(w, m.rightArm.x, ax);
		m.rightArm.y = Mth.lerp(w, m.rightArm.y, 2.0F);
		m.rightArm.z = Mth.lerp(w, m.rightArm.z, az);
		m.leftArm.x = Mth.lerp(w, m.leftArm.x, -ax);
		m.leftArm.y = Mth.lerp(w, m.leftArm.y, 2.0F);
		m.leftArm.z = Mth.lerp(w, m.leftArm.z, -az);
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, p.rPitch() * d);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -p.rYaw() * d + tw);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, p.rAbd() * d);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, p.lPitch() * d);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, -p.lYaw() * d + tw);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, p.lAbd() * d);
		// 다리: 바닐라 걷기 흔들림을 쓰지 않고 통째로 (스펙 PART 8-4)
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, p.rLeg() * d);
		m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, 0.0F);
		m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, p.rLegAbd() * d);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, p.lLeg() * d);
		m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, 0.0F);
		m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, -p.lLegAbd() * d);
		return true;
	}

	/** 모델 전체 — 엉덩이 높이를 축으로 pitch(+ 앞으로) · roll(+ 오른쪽) · Y (setupRotations 끝). */
	public static void root(AnimRenderState a, PoseStack pose, float entityScale) {
		ScatterBody.Pose p = a.overbreak$scatter();
		if (p == null) {
			return;
		}
		float w = a.overbreak$scatterWeight();
		float hip = 0.70F * entityScale;
		pose.translate(0.0F, p.rootY() / 16.0F * 0.9375F * entityScale * w, 0.0F);
		pose.translate(0.0F, hip, 0.0F);
		pose.mulPose(Axis.XP.rotationDegrees(p.rootPitch() * w));
		pose.mulPose(Axis.ZP.rotationDegrees(p.rootRoll() * w));
		pose.translate(0.0F, -hip, 0.0F);
	}

	/** 손에 든 총 기울기 (도, 총열 축) — 없으면 0. */
	public static float gunRoll(AnimRenderState a, HumanoidArm arm) {
		ScatterBody.Pose p = a.overbreak$scatter();
		if (p == null) {
			return 0.0F;
		}
		return (arm == HumanoidArm.RIGHT ? p.rGun() : p.lGun()) * a.overbreak$scatterWeight();
	}

	/** 월드를 나가면 비웁니다. */
	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			PARAMS.clear();
		}
	}

	static @Nullable Params paramsOrNull(int id) {
		return PARAMS.get(id);
	}
}
