package kr.overbreak.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import kr.overbreak.client.fx.IronFx;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * 참철 1인칭 — 대검 자세와 두 손 (스펙 PART 12~18).
 *
 * 대검은 입체 모델(models/item/ironcleaver_greatsword.json)이고 1인칭 display 는 손잡이 가운데를 손 기준점에 두고
 * 칼날을 +Y 로 세운 채 넓은 면을 옆으로 25° 돌려 둡니다. 나머지 자세는 전부 여기 키프레임입니다 (동작이 없을 때도 {@link #REST}).
 *
 * 키프레임 {시간, x, y, z, X축, Y축, Z축, 크기} — 카메라 기준 (+x 오른쪽 · +y 위 · -z 앞), 손잡이 가운데가 (x, y, z).
 *   Z축 + = 칼끝이 왼쪽으로 기움 (-90 이면 칼끝이 오른쪽 수평)
 *   X축 - = 칼끝이 앞으로 숙음 (+ 는 뒤로 젖힘) · 칼날이 옆으로 누운 뒤에는 칼날 축 굴림(날 방향)
 *   Y축 = 세로축 돌림 — 칼끝이 오른쪽 수평일 때 0 오른쪽 → 90 정면 → 180 왼쪽 (가로 베기)
 *
 * 두 손: 오른손이 위(코등이 쪽), 왼손이 아래(자루 끝 쪽). 팔은 화면 아래 어깨 자리에서 손을 향해 곧게 뻗습니다.
 *   검막은 왼손이 칼날 뒷면을 받치고, 어깨 박치기는 왼팔 · 어깨가 화면 왼쪽 아래에서 들어옵니다.
 * 역경직: 내 공격이 맞으면 {@link IronFx#lag} 만큼 동작 시간을 늦춰 그립니다 — 칼과 손이 그 자리에서 멈췄다가 빠르게 따라잡음.
 */
public final class IronAnim {
	/** 대기 — 캐릭터 키만 한 대검을 화면 오른쪽 끝에서 두 손으로 쥐고 칼끝을 위로 세움. */
	static final float[] REST = {0.0F, 0.80F, -0.60F, -0.95F, -6F, 10F, 8F, 1.0F};

	/** 1타 — 칼을 오른쪽으로 크게 젖혀 당겼다가 (칼끝 오른쪽 위) 화면 가운데를 수평으로 한 번에 쓸고 왼쪽 밖으로 넘어감. */
	private static final float[][] SWING_R = {
			REST,
			{5.5F, 0.50F, -0.46F, -0.95F, 15F, -10F, -55F, 1.0F},
			{7.0F, 0.52F, -0.45F, -0.93F, 18F, -12F, -60F, 1.0F},
			{8.0F, 0.20F, -0.46F, -1.00F, 40F, 70F, -90F, 1.0F},
			{9.0F, -0.25F, -0.50F, -0.98F, 40F, 150F, -90F, 1.0F},
			{14.0F, -0.40F, -0.56F, -0.92F, 40F, 170F, -95F, 1.0F},
			{18.0F, 0.80F, -0.60F, -0.95F, -6F, 10F, 8F, 1.0F}};
	/** 2타 — 1타의 좌우 반전: 칼을 왼쪽으로 젖혀 당겼다가 왼쪽 → 오른쪽. */
	private static final float[][] SWING_L = {
			REST,
			{5.5F, -0.12F, -0.46F, -0.95F, 15F, 10F, 55F, 1.0F},
			{7.0F, -0.14F, -0.45F, -0.93F, 18F, 12F, 60F, 1.0F},
			{8.0F, 0.15F, -0.46F, -1.00F, -40F, -70F, 90F, 1.0F},
			{9.0F, 0.60F, -0.50F, -0.98F, -40F, -150F, 90F, 1.0F},
			{14.0F, 0.70F, -0.56F, -0.92F, -40F, -170F, 95F, 1.0F},
			{18.0F, 0.80F, -0.60F, -0.95F, -6F, 10F, 8F, 1.0F}};
	/**
	 * 3타 — 벤데타 3타처럼: 칼을 곧게 세워 천천히 높이 끌어올려 힘을 모으고 (꼭대기에서 잠깐 멈칫) → 0.05초 만에 땅까지 내리꽂아
	 * 깊이 박혔다가 반동으로 살짝 튀고 → 칼끝이 땅에 박힌 채 멈춤.
	 */
	private static final float[][] OVERHEAD = {
			REST,
			{4.0F, 0.30F, -0.34F, -0.96F, 0F, 0F, 0F, 1.0F},
			{7.5F, 0.26F, -0.14F, -0.86F, 24F, 0F, -6F, 1.0F},
			{8.4F, 0.25F, -0.11F, -0.84F, 28F, 0F, -6F, 1.0F},
			{8.9F, 0.20F, -0.34F, -0.98F, -40F, 0F, 0F, 1.0F},
			{9.4F, 0.14F, -0.72F, -0.92F, -110F, 0F, 0F, 1.0F},
			{10.4F, 0.14F, -0.60F, -0.93F, -96F, 0F, 0F, 1.0F},
			{11.2F, 0.14F, -0.66F, -0.93F, -101F, 0F, 0F, 1.0F},
			{16.0F, 0.14F, -0.64F, -0.93F, -100F, 0F, 0F, 1.0F},
			{22.0F, 0.80F, -0.60F, -0.95F, -6F, 10F, 8F, 1.0F}};
	/**
	 * 참 모으기 — 칼을 오른쪽 어깨 쪽으로 젖혀 들고 (칼끝이 오른쪽 위 뒤) 단계마다 더 젖히고 더 떨림.
	 * 칼날과 오오라가 화면 오른쪽에 크게 보이게 둡니다.
	 */
	private static final float[][] CHARGE = {
			REST,
			{5.0F, 0.50F, -0.50F, -0.95F, 10F, -5F, -20F, 1.0F},
			{12.0F, 0.54F, -0.52F, -0.93F, 16F, -8F, -26F, 1.0F},
			{24.0F, 0.58F, -0.54F, -0.91F, 22F, -12F, -32F, 1.0F},
			{36.0F, 0.62F, -0.56F, -0.88F, 28F, -16F, -38F, 1.0F},
			{200.0F, 0.62F, -0.56F, -0.88F, 28F, -16F, -38F, 1.0F}};
	/** 모아 베기 — 젖힌 자세에서 대각선 위 → 아래로 화면을 가르며 내려벰 → 칼끝이 왼쪽 아래에 멈춤. */
	private static final float[][] RELEASE = {
			{0.0F, 0.62F, -0.56F, -0.88F, 28F, -16F, -38F, 1.0F},
			{1.0F, 0.35F, -0.36F, -0.98F, 20F, 40F, -60F, 1.0F},
			{2.4F, -0.30F, -0.60F, -0.98F, 10F, 150F, -115F, 1.0F},
			{8.0F, -0.36F, -0.64F, -0.94F, 10F, 160F, -118F, 1.0F},
			{16.0F, 0.80F, -0.60F, -0.95F, -6F, 10F, 8F, 1.0F}};
	/** 어깨 박치기 — 대검이 오른쪽 아래로 빠져 대부분 사라지고 (왼팔 · 어깨가 화면 왼쪽 아래에서 들어옴). */
	private static final float[][] BASH = {
			REST,
			{1.0F, 0.70F, -0.98F, -0.80F, 30F, -20F, -30F, 1.0F},
			{8.0F, 0.70F, -0.98F, -0.80F, 30F, -20F, -30F, 1.0F}};
	/**
	 * 검막 — 벤데타식: 대검을 수평으로 눕혀 화면 아래 1/3 높이에 가로로 걸침. 칼끝 왼쪽 · 손잡이 오른쪽 · 날은 위 · 넓은 면이 정면.
	 * 오른손이 손잡이, 왼손이 칼날 가운데 뒷면을 받침.
	 */
	private static final float[][] GUARD = {
			REST,
			{2.0F, 0.60F, -0.46F, -0.95F, 0F, -25F, 90F, 1.0F},
			{20.0F, 0.60F, -0.46F, -0.95F, 0F, -25F, 90F, 1.0F}};
	/** 대지 가르기 (0.25초) — 칼을 오른쪽 뒤 아래로 확 내려 칼끝을 땅에 대고 → 화면 아래를 오른쪽 뒤 → 정면으로 긁어 → 위로 튕겨 올림. */
	private static final float[][] REND = {
			REST,
			{1.5F, 0.66F, -0.82F, -0.78F, -20F, -35F, -115F, 1.0F},
			{3.0F, 0.62F, -0.86F, -0.80F, -40F, -35F, -120F, 1.0F},
			{4.2F, 0.36F, -0.78F, -1.00F, -112F, -10F, -15F, 1.0F},
			{5.0F, 0.26F, -0.44F, -1.02F, -40F, 0F, 10F, 1.0F},
			{6.5F, 0.30F, -0.28F, -0.98F, -5F, 0F, 8F, 1.0F},
			{11.0F, 0.80F, -0.60F, -0.95F, -6F, 10F, 8F, 1.0F}};
	/** 천참 — 대검을 곧게 세워 높이 들어 올려 칼끝이 화면 위로 사라짐 (1.2초) → 화면 가운데를 위 → 아래로 내려벰 → 칼끝이 땅에 박힌 채 멈춤. */
	private static final float[][] ULT = {
			REST,
			{8.0F, 0.20F, -0.20F, -0.95F, 8F, 15F, 0F, 1.0F},
			{23.0F, 0.18F, -0.14F, -0.93F, 12F, 15F, 0F, 1.0F},
			{24.5F, 0.12F, -0.36F, -1.00F, -50F, 15F, 0F, 1.0F},
			{25.5F, 0.10F, -0.62F, -0.95F, -100F, 15F, 0F, 1.0F},
			{33.0F, 0.10F, -0.62F, -0.93F, -100F, 15F, 0F, 1.0F},
			{40.0F, 0.80F, -0.60F, -0.95F, -6F, 10F, 8F, 1.0F}};

	/** 대검 모델 기준 손 자리 (모델 y, 픽셀) — 손잡이 -4.5 ~ 1.5. 오른손 위 · 왼손 아래. */
	private static final float RIGHT_GRIP = 0.0F;
	private static final float LEFT_GRIP = -3.3F;
	/** 검막: 왼손이 받치는 칼날 자리 (모델 y). */
	private static final float BLADE_SUPPORT = 15.0F;
	/** 모델 손잡이 가운데 (모델 y) — 1인칭 display 가 이 자리를 손 기준점에 둠. */
	private static final float GRIP_CENTER = -1.5F;
	/** 1인칭 display 의 넓은 면 돌림 (도) — 모델 json firstperson_righthand rotation y 와 같게. */
	private static final float DISPLAY_YAW = 25.0F;

	/** 어깨 자리 (카메라 기준, 오른손잡이) — 팔은 여기서 손을 향해 곧게. */
	private static final Vector3f RIGHT_SHOULDER = new Vector3f(0.46F, -1.10F, -0.30F);
	private static final Vector3f LEFT_SHOULDER = new Vector3f(-0.06F, -1.12F, -0.34F);
	private static final float ARM_SCALE = 1.0F;
	/** 손을 높이 들면 어깨 자리를 눈 아래 · 몸 뒤쪽으로 (팔뚝이 아래 뒤에서 손으로 올라오게 — 화면을 가로지르지 않음). */
	private static final float HIGH_SHOULDER_Y = -0.45F;
	private static final float HIGH_SHOULDER_Z = 0.15F;
	/** 어깨 박치기 — 왼손이 가는 자리 (카메라 기준): 화면 왼쪽 아래에서 팔뚝이 앞으로. */
	private static final Vector3f BASH_LEFT = new Vector3f(-0.42F, -0.56F, -0.70F);
	private static final Vector3f BASH_LEFT_SHOULDER = new Vector3f(-0.50F, -1.10F, -0.12F);

	private static final java.util.Map<Integer, ItemStack> AURA = new java.util.HashMap<>();

	private IronAnim() {}

	public static boolean handles(int anim) {
		return anim >= SkillAnimPayload.IC_SWING_R && anim <= SkillAnimPayload.IC_ULT;
	}

	private static float[][] keys(int anim) {
		return switch (anim) {
			case SkillAnimPayload.IC_SWING_R -> SWING_R;
			case SkillAnimPayload.IC_SWING_L -> SWING_L;
			case SkillAnimPayload.IC_OVERHEAD -> OVERHEAD;
			case SkillAnimPayload.IC_CHARGE -> CHARGE;
			case SkillAnimPayload.IC_RELEASE -> RELEASE;
			case SkillAnimPayload.IC_BASH -> BASH;
			case SkillAnimPayload.IC_GUARD -> GUARD;
			case SkillAnimPayload.IC_REND -> REND;
			default -> ULT;
		};
	}

	/** 동작 시간 — 내 동작이면 역경직만큼 늦춤. */
	public static float time(SkillAnims.Play play, int entityId, float partial) {
		float e = play.elapsed(partial);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && entityId == mc.player.getId()) {
			e -= IronFx.lag(play.startTime(), partial);
		}
		return e;
	}

	/** 지금 1인칭 참철 동작 (없으면 null — 끝난 재생은 fade 뒤 SkillAnims 가 지움). */
	private static SkillAnims.@Nullable Play current(int id) {
		return SkillAnims.latestOf(id, IronFx.IRON_ANIMS);
	}

	/** 지금 자세 값 {시간, x, y, z, X, Y, Z, 크기} — 동작이 끝나면 fade 동안 대기 자세로. */
	private static float[] sample(int id, float partial) {
		SkillAnims.Play play = current(id);
		if (play == null) {
			return REST.clone();
		}
		float e = time(play, id, partial);
		float end = play.end();
		float[] v = FirstPersonAnim.sampleKeys(keys(play.anim), Math.min(e, end));
		if (e > end) {
			float q = Mth.clamp((e - end) / SkillAnims.fade(play.anim), 0.0F, 1.0F);
			q = q * q * (3.0F - 2.0F * q);
			for (int c = 1; c < v.length; c++) {
				v[c] = Mth.lerp(q, v[c], REST[c]);
			}
		}
		return v;
	}

	/** 대검을 들고 있으면 늘 이 자세 (동작이 없으면 대기 자세). 호출 전 포즈는 손 변환이 없는 기본 상태. */
	public static void firstPerson(PoseStack pose, int invert, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int id = mc.player == null ? -1 : mc.player.getId();
		SkillAnims.Play play = current(id);
		float e = play == null ? 0.0F : time(play, id, partial);
		if (IronFx.frozen(partial)) {
			// 역경직 — 멈춘 칼이 부르르 떨림
			float t = (float) (kr.overbreak.client.ClientClock.at(partial) * 7.0);
			pose.translate(Mth.sin(t * 3.1F) * 0.008F, Mth.cos(t * 2.3F) * 0.006F, 0.0F);
		}
		if (play != null && play.anim == SkillAnimPayload.IC_CHARGE && e < play.end()) {
			// 단계가 오를수록 칼이 떨림 (1단 ±0.3px · 2단 ±0.6px · 3단 ±1.0px 초당 18회 · 진 참 ±1.2px)
			int stage = IronFx.stage(id);
			float px = IronFx.perfect(id) ? 1.2F : stage >= 3 ? 1.0F : stage == 2 ? 0.6F : stage == 1 ? 0.3F : 0.0F;
			float amp = px / 16.0F;
			float t = e * Mth.TWO_PI * 18.0F / 20.0F;
			pose.translate(Mth.sin(t) * amp, Mth.sin(t * 1.3F + 1.1F) * amp, 0.0F);
		}
		if (play != null && play.anim == SkillAnimPayload.IC_ULT && e < 24.0F) {
			float amp = 0.004F + 0.008F * Mth.clamp(e / 24.0F, 0.0F, 1.0F);
			pose.translate(Mth.sin(e * 5.1F) * amp, Mth.cos(e * 4.3F) * amp, 0.0F);
		}
		apply(pose, invert, sample(id, partial));
		// 칼날 자체의 세로축(가운데)을 축으로 비틂 — 위에서 볼 때 + 가 반시계
		float twist = twist(play, e);
		if (twist != 0.0F) {
			pose.mulPose(Axis.YP.rotationDegrees(invert * twist));
		}
	}

	/** 3타 내려찍기 · 천참 — 칼을 곧게 세운 채 칼날 축으로 반시계 50° 비틀어 들고 내려침 (들 때 0.25초에 걸쳐 · 돌아올 때 풀림). */
	private static final float OVERHEAD_TWIST = 50.0F;

	private static float twist(SkillAnims.@Nullable Play play, float e) {
		if (play == null) {
			return 0.0F;
		}
		if (play.anim == SkillAnimPayload.IC_OVERHEAD) {
			float in = Mth.clamp(e / 5.0F, 0.0F, 1.0F);
			float out = 1.0F - Mth.clamp((e - 16.0F) / 6.0F, 0.0F, 1.0F);
			return OVERHEAD_TWIST * Math.min(in, out);
		}
		if (play.anim == SkillAnimPayload.IC_ULT) {
			// 천참도 내려찍기와 같은 비틀기 — 들어 올리는 0.4초에 걸쳐 · 박힌 채 멈췄다가 돌아올 때 풀림
			float in = Mth.clamp(e / 8.0F, 0.0F, 1.0F);
			float out = 1.0F - Mth.clamp((e - 33.0F) / 7.0F, 0.0F, 1.0F);
			return OVERHEAD_TWIST * Math.min(in, out);
		}
		return 0.0F;
	}

	private static void apply(PoseStack pose, int invert, float[] v) {
		pose.translate(invert * v[1], v[2], v[3]);
		pose.mulPose(Axis.YP.rotationDegrees(invert * v[5]));
		pose.mulPose(Axis.XP.rotationDegrees(v[4]));
		pose.mulPose(Axis.ZP.rotationDegrees(invert * v[6]));
		pose.scale(v[7], v[7], v[7]);
	}

	// ── 두 손 ───────────────────────────────────────────

	/** 그릴 두 팔 (손 기준점이 없는 기본 포즈 위에 곱함). */
	public record Hands(Matrix4f right, Matrix4f left) {}

	/**
	 * 대검 자세에서 두 손 자리를 구해 팔 포즈를 만듭니다.
	 * @param rel 기본 포즈 → 대검 포즈 (손잡이 가운데가 원점, display 적용 전)
	 */
	public static Hands hands(Matrix4f rel, int invert, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int id = mc.player == null ? -1 : mc.player.getId();
		SkillAnims.Play play = current(id);
		int anim = play != null && SkillAnims.playing(id, play.anim) ? play.anim : 0;
		Vector3f mainHand = rel.transformPosition(modelPoint(RIGHT_GRIP));
		Matrix4f main = armTo(mainHand, mirror(RIGHT_SHOULDER, invert), -invert);
		Vector3f offHand;
		Vector3f offShoulder = mirror(LEFT_SHOULDER, invert);
		if (anim == SkillAnimPayload.IC_BASH) {
			offHand = mirror(BASH_LEFT, invert);
			offShoulder = mirror(BASH_LEFT_SHOULDER, invert);
		} else if (anim == SkillAnimPayload.IC_GUARD) {
			// 칼날 뒷면 (몸 쪽) — 넓은 면 법선 반대쪽으로 팔 두께 반만큼
			Vector3f back = displayNormal(invert).mul(-0.9F / 16.0F);
			offHand = rel.transformPosition(modelPoint(BLADE_SUPPORT).add(back));
		} else {
			offHand = rel.transformPosition(modelPoint(LEFT_GRIP));
		}
		Matrix4f off = armTo(offHand, offShoulder, invert);
		return new Hands(main, off);
	}

	/** 모델 칼날 축 위 한 점 (모델 y, 픽셀) → 대검 포즈 좌표 (display 적용). */
	private static Vector3f modelPoint(float modelY) {
		return new Vector3f(0.0F, (modelY - GRIP_CENTER) / 16.0F, 0.0F);
	}

	/** display 뒤 넓은 면 법선 (+Z 를 Y축으로 돌린 것). */
	private static Vector3f displayNormal(int invert) {
		float r = invert * DISPLAY_YAW * Mth.DEG_TO_RAD;
		return new Vector3f(Mth.sin(r), 0.0F, Mth.cos(r));
	}

	private static Vector3f mirror(Vector3f v, int invert) {
		return new Vector3f(invert * v.x, v.y, v.z);
	}

	/**
	 * 어깨 쪽에서 손 자리로 곧게 뻗은 팔 (FirstPersonAnim.gunHands 와 같은 방식).
	 * @param side 모델 왼팔이면 +1, 오른팔이면 -1
	 */
	private static Matrix4f armTo(Vector3f hand, Vector3f low, int side) {
		// 손 높이에 따라 어깨를 화면 아래 → 머리 위 뒤쪽으로 옮김
		float up = Mth.clamp((hand.y + 0.40F) / 0.45F, 0.0F, 1.0F);
		Vector3f shoulder = new Vector3f(low.x, Mth.lerp(up, low.y, HIGH_SHOULDER_Y), Mth.lerp(up, low.z, HIGH_SHOULDER_Z));
		Vector3f dir = new Vector3f(hand).sub(shoulder).normalize();
		Vector3f across = new Vector3f(0.0F, 1.0F, 0.0F).sub(new Vector3f(dir).mul(dir.y));
		if (across.lengthSquared() < 1.0E-6F) {
			across.set(1.0F, 0.0F, 0.0F);
		}
		across.normalize().mul(-side);
		Vector3f depth = new Vector3f(across).cross(dir);
		Matrix3f target = new Matrix3f(across, dir, depth);
		Matrix3f lean = new Matrix3f().rotationZ(-0.1F * side);
		Matrix3f turn = new Matrix3f(target).mul(new Matrix3f(lean).transpose());
		Vector3f palm = lean.transform(new Vector3f(side, 8.5F, 0.0F)).add(5.0F * side, 2.0F, 0.0F).div(16.0F);
		return new Matrix4f().translation(hand).mul(new Matrix4f().set(turn)).scale(ARM_SCALE).translate(-palm.x, -palm.y, -palm.z);
	}

	// ── 3인칭 · 공통 ─────────────────────────────────────

	/**
	 * 3인칭 손 아이템 굴림 (도) — 가로 베기 · 모아 베기 · 검막은 칼날을 팔 축으로 눕혀 수평으로.
	 * 들어갈 때는 0.15초에 걸쳐 눕히고, 동작 가중치(w)로 끝날 때 다시 세웁니다.
	 */
	public static float itemRoll(int anim, float t, float w) {
		float r = switch (anim) {
			case SkillAnimPayload.IC_SWING_R, SkillAnimPayload.IC_RELEASE -> 90.0F;
			case SkillAnimPayload.IC_SWING_L -> -90.0F;
			case SkillAnimPayload.IC_GUARD -> 90.0F;
			default -> 0.0F;
		};
		if (r == 0.0F) {
			return 0.0F;
		}
		float in = anim == SkillAnimPayload.IC_RELEASE ? 1.0F : Mth.clamp(t / 3.0F, 0.0F, 1.0F);
		return r * in * w;
	}

	/**
	 * 칼날 오오라 — 대검과 같은 자세로 겹쳐 그리는 빛 껍질 모델 (overbreak:ironcleaver_aura, 흰 반투명 → 단계 색으로 염색).
	 * 같은 display 를 써서 칼날에 정확히 붙습니다. 최대 밝기로 그려 어두운 곳에서도 빛남.
	 */
	public static ItemStack auraStack(int argb) {
		int rgb = argb & 0xFFFFFF;
		return AURA.computeIfAbsent(rgb, c -> {
			ItemStack s = new ItemStack(net.minecraft.world.item.Items.STICK);
			s.set(DataComponents.ITEM_MODEL, kr.overbreak.Overbreak.id("ironcleaver_aura"));
			s.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(c));
			return s;
		});
	}

	/** 들고 있는 것이 참철 대검인가. */
	public static boolean greatsword(ItemStack stack) {
		return kr.overbreak.Overbreak.id("ironcleaver_greatsword").equals(stack.get(DataComponents.ITEM_MODEL));
	}
}
