package kr.overbreak.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import kr.overbreak.client.anim.data.DataPose;
import kr.overbreak.client.anim.data.PlayerAnimation;
import kr.overbreak.client.anim.data.PlayerAnimations;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 1인칭 손 애니메이션. 시간은 {@link SkillAnims} 에서 읽습니다.
 *
 * 좌표는 카메라 공간입니다: +x 오른쪽, +y 위, -z 앞. 바닐라 기본 손 위치는 (0.56, -0.52, -0.72).
 * Blockbench 애니메이션 파일에 firstperson_item 뼈대가 있으면 아래 표 대신 그 값을 씁니다 (발키리).
 */
public final class FirstPersonAnim {
	// 살육 — 서버 Slay 기준: 시전 틱 t=1, 발동 t=14, 종료 t=22 → 도착 기준 13 / 21
	private static final float SLAY_IMPACT = 13.0F;

	/** 기본 공격 키프레임: 시간, 카메라 축 회전(도, + = 오른쪽→왼쪽), 손 위치 x/y/z, 기울기(도). 마지막은 기본 손 자세. */
	private static final float[][] BASIC = {
			{0.0F, 0.0F, 0.56F, -0.52F, -0.72F, 0.0F},
			{1.5F, -30.0F, 0.45F, -0.40F, -0.80F, 60.0F},
			{3.0F, 55.0F, 0.35F, -0.45F, -0.85F, 75.0F},
			{6.0F, 70.0F, 0.30F, -0.55F, -0.80F, 80.0F},
			{10.0F, 0.0F, 0.56F, -0.52F, -0.72F, 0.0F}};
	/** 역방향: 왼쪽으로 당겼다가 오른쪽으로. */
	private static final float[][] BASIC_BACK = {
			{0.0F, 0.0F, 0.56F, -0.52F, -0.72F, 0.0F},
			{1.5F, 35.0F, 0.40F, -0.42F, -0.80F, -40.0F},
			{3.0F, -50.0F, 0.40F, -0.45F, -0.85F, -60.0F},
			{6.0F, -65.0F, 0.38F, -0.55F, -0.80F, -65.0F},
			{10.0F, 0.0F, 0.56F, -0.52F, -0.72F, 0.0F}};

	/**
	 * 햄머나이트 키프레임: {시간, x, y, z, X축 회전(도, + = 머리가 뒤로 젖혀짐), Y축 회전, Z축 기울기, 크기}.
	 * 첫 줄이 바닐라 기본 손 자세이고, 끝나면 마지막 자세에서 기본 자세로 돌아옵니다.
	 */
	private static final float[][] HK_SMASH = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{4.0F, 0.42F, -0.02F, -0.62F, 70F, 0F, 12F, 1.0F},
			{10.0F, 0.40F, 0.02F, -0.60F, 78F, 0F, 12F, 1.0F},
			{11.5F, 0.22F, -0.66F, -0.95F, -85F, 0F, 0F, 1.0F},
			{16.0F, 0.26F, -0.64F, -0.92F, -80F, 0F, 0F, 1.0F}};
	private static final float[][] HK_SLAM = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{2.0F, 0.42F, -0.02F, -0.62F, 70F, 0F, 12F, 1.0F},
			{3.5F, 0.22F, -0.66F, -0.95F, -85F, 0F, 0F, 1.0F},
			{9.0F, 0.26F, -0.64F, -0.92F, -80F, 0F, 0F, 1.0F}};
	private static final float[][] HK_ULT = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{6.0F, 0.82F, -0.20F, -0.38F, 45F, -40F, -25F, 1.15F},
			{16.0F, 0.86F, -0.16F, -0.34F, 52F, -45F, -28F, 1.25F},
			{17.2F, 0.18F, -0.78F, -1.02F, -95F, 10F, 0F, 1.45F},
			{26.0F, 0.22F, -0.74F, -0.98F, -88F, 8F, 0F, 1.3F}};
	/** 돌진 충격 1인칭 왼손 방패: 가로로 눕혀 아래쪽에 들고 정면이 보이도록. */
	private static final float[][] HK_SHIELD = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{3.0F, 0.16F, -0.50F, -0.72F, -62F, 0F, 90F, 1.0F},
			{6.0F, 0.14F, -0.52F, -0.70F, -64F, 0F, 90F, 1.0F},
			{7.5F, 0.12F, -0.46F, -0.88F, -58F, 0F, 90F, 1.0F},
			{14.0F, 0.12F, -0.46F, -0.88F, -58F, 0F, 90F, 1.0F}};

	/**
	 * 파쇄권 오른손 건틀릿 {시간, x, y, z, X축(도, + = 주먹이 위로), Y축, Z축, 크기}. 건틀릿 모델은 주먹이 앞(-Z)입니다.
	 * 둠피스트 동작: 충전 = 허리 뒤로 당김, 돌진 = 가운데로 곧게 내지름, 파워 블록 = 주먹을 세워 얼굴 앞,
	 * 지진 강타 = 머리 위로 들었다가 땅에 내리꽂음, 파멸의 일격 = 위로 뻗고 솟구쳤다가 주먹부터 착지.
	 */
	private static final float[][] IF_CHARGE = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{4.0F, 0.62F, -0.56F, -0.50F, 12F, -18F, -8F, 1.0F},
			{40.0F, 0.62F, -0.56F, -0.50F, 12F, -18F, -8F, 1.0F}};
	private static final float[][] IF_PUNCH = {
			{0.0F, 0.62F, -0.56F, -0.50F, 12F, -18F, -8F, 1.0F},
			{1.5F, 0.26F, -0.40F, -1.18F, -4F, 10F, 0F, 1.12F},
			{12.0F, 0.28F, -0.42F, -1.12F, -4F, 10F, 0F, 1.1F},
			{16.0F, 0.30F, -0.45F, -1.05F, -2F, 8F, 0F, 1.05F}};
	private static final float[][] IF_BLOCK = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{3.0F, 0.14F, -0.46F, -0.56F, 72F, 14F, -6F, 1.15F},
			{40.0F, 0.14F, -0.46F, -0.56F, 72F, 14F, -6F, 1.15F}};
	private static final float[][] IF_SLAM_AIR = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{3.0F, 0.48F, -0.10F, -0.62F, 70F, -8F, 12F, 1.0F},
			{200.0F, 0.48F, -0.10F, -0.62F, 70F, -8F, 12F, 1.0F}};
	private static final float[][] IF_SLAM_HIT = {
			{0.0F, 0.48F, -0.10F, -0.62F, 70F, -8F, 12F, 1.0F},
			{1.5F, 0.20F, -0.62F, -0.98F, -70F, 6F, 0F, 1.1F},
			{12.0F, 0.22F, -0.60F, -0.95F, -68F, 6F, 0F, 1.08F}};
	private static final float[][] IF_ULT_RISE = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{3.0F, 0.60F, -0.78F, -0.55F, -18F, 0F, 0F, 1.0F},
			{6.0F, 0.30F, 0.05F, -0.72F, 82F, 0F, 6F, 1.1F},
			{20.0F, 0.30F, 0.08F, -0.72F, 84F, 0F, 6F, 1.1F}};
	private static final float[][] IF_ULT_DROP = {
			{0.0F, 0.30F, 0.08F, -0.72F, 84F, 0F, 6F, 1.1F},
			{4.0F, 0.30F, 0.02F, -0.72F, 80F, 0F, 6F, 1.1F},
			{5.0F, 0.18F, -0.66F, -1.00F, -74F, 0F, 0F, 1.2F},
			{20.0F, 0.20F, -0.64F, -0.98F, -72F, 0F, 0F, 1.18F}};
	/** 왼손 철권포 장갑: 발사 반동 · 충전 중 앞으로 겨눔 · 돌진 때 뒤로 · 막을 때 · 착지 때 내림. */
	private static final float[][] IF_SHOT_OFF = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{0.8F, 0.54F, -0.44F, -0.60F, 16F, 0F, 0F, 1.0F},
			{6.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};
	private static final float[][] IF_AIM_OFF = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{4.0F, 0.46F, -0.46F, -0.80F, 0F, 10F, 0F, 1.0F},
			{40.0F, 0.46F, -0.46F, -0.80F, 0F, 10F, 0F, 1.0F}};
	private static final float[][] IF_PUNCH_OFF = {
			{0.0F, 0.46F, -0.46F, -0.80F, 0F, 10F, 0F, 1.0F},
			{1.5F, 0.62F, -0.70F, -0.55F, 0F, 0F, 0F, 1.0F},
			{16.0F, 0.62F, -0.70F, -0.55F, 0F, 0F, 0F, 1.0F}};
	private static final float[][] IF_LOWER_OFF = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{3.0F, 0.62F, -0.86F, -0.60F, 0F, 0F, 0F, 1.0F},
			{40.0F, 0.62F, -0.86F, -0.60F, 0F, 0F, 0F, 1.0F}};

	/**
	 * 투귀 강화 포션 (왼손) — 병을 입으로 올려 들이켰다가 발밑으로 내던집니다 (0.2a).
	 * {시각, x, y, z, 가로축 회전, 세로축 회전, 화면축 회전, 크기}
	 *
	 * 팔은 그리지 않습니다 (0.2b) — 바닐라 왼팔 자리가 병에 통째로 가려 손만 어색하게 비어져 나왔습니다.
	 */
	private static final float[][] BR_POTION_OFF = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{4.0F, 0.44F, -0.28F, -0.66F, 38F, -12F, -10F, 1.0F},
			{8.0F, 0.38F, -0.18F, -0.60F, 74F, -14F, -12F, 1.0F},
			{15.0F, 0.37F, -0.15F, -0.58F, 92F, -14F, -12F, 1.0F},
			{18.0F, 0.48F, -0.38F, -0.66F, 24F, -10F, -6F, 1.0F},
			{20.0F, 0.58F, -1.00F, -0.56F, -62F, 0F, 34F, 1.0F}};

	/**
	 * 건슬링어 쌍권총 {시각, x, y, z, 가로축 회전, 세로축 회전, 화면축 회전, 크기}.
	 * 양손에 한 자루씩 들고 있어 같은 표를 주 손 · 왼손에 그대로 씁니다 (invert 가 좌우를 뒤집어 대칭이 됩니다).
	 *
	 * 모델은 총열이 -z(앞) 이라 가로축 회전이 양수면 총구가 위로 들립니다.
	 * 사격 반동은 총구를 들어 올렸다가 제자리로 내려오는 모양입니다 (0.2d).
	 */
	private static final float[][] GS_SHOT = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{0.7F, 0.56F, -0.46F, -0.63F, 22F, 0F, 3F, 1.02F},
			{2.4F, 0.57F, -0.53F, -0.73F, -5F, 0F, -1F, 1.0F},
			{6.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};
	/**
	 * 공중 재장전 (25틱) — 손목 털기 → 앞으로 한 바퀴 돌리기 → 받아 끼우기 → 반동.
	 * 16.0 → 16.1 은 360도 = 0도 를 바꾸는 자리입니다 (끝난 뒤 fade 가 한 바퀴 더 돌지 않게).
	 */
	private static final float[][] GS_RELOAD = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{2.0F, 0.54F, -0.34F, -0.66F, -30F, 4F, -8F, 1.0F},
			{5.0F, 0.52F, -0.30F, -0.64F, -36F, 6F, -10F, 1.0F},
			{11.0F, 0.50F, -0.40F, -0.72F, 160F, 4F, -6F, 1.0F},
			{16.0F, 0.52F, -0.36F, -0.70F, 360F, 2F, -4F, 1.0F},
			{16.1F, 0.52F, -0.36F, -0.70F, 0F, 2F, -4F, 1.0F},
			{18.0F, 0.55F, -0.10F, -0.60F, -26F, 0F, 0F, 1.05F},
			{19.5F, 0.56F, -0.24F, -0.66F, -8F, 0F, 0F, 1.0F},
			{22.0F, 0.60F, -0.64F, -0.80F, 14F, -3F, 5F, 0.97F},
			{25.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};
	/** 반동 도약 — 조준한 쪽으로 내질렀다가 크게 뒤로 튀김. */
	private static final float[][] GS_BOOST = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{1.5F, 0.48F, -0.40F, -0.95F, -18F, 8F, 0F, 1.06F},
			{3.0F, 0.62F, -0.62F, -0.55F, 26F, -6F, 0F, 1.0F},
			{8.0F, 0.58F, -0.55F, -0.70F, 8F, -2F, 0F, 1.0F},
			{12.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};
	/** 곡예 난사 — 두 총을 밖으로 벌리고 한 바퀴 (세로축 -360도). */
	private static final float[][] GS_ACRO = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{2.0F, 0.40F, -0.46F, -0.80F, -10F, -40F, -14F, 1.05F},
			{8.0F, 0.40F, -0.46F, -0.80F, -10F, -180F, -14F, 1.05F},
			{14.0F, 0.42F, -0.46F, -0.78F, -10F, -320F, -14F, 1.05F},
			{15.9F, 0.52F, -0.50F, -0.74F, -4F, -360F, -6F, 1.0F},
			{16.0F, 0.52F, -0.50F, -0.74F, -4F, 0F, -6F, 1.0F}};
	/** 사선 앵커 — 오른쪽 총을 바깥으로 치우고 왼손이 와이어를 쎏다. */
	private static final float[][] GS_ANCHOR = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{2.0F, 0.70F, -0.58F, -0.60F, -12F, 18F, 10F, 1.0F},
			{7.0F, 0.66F, -0.56F, -0.64F, -6F, 14F, 8F, 1.0F},
			{12.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F}};
	/** 차원 회전 포격 — 두 총을 아래로 겨눈 채 유지. */
	private static final float[][] GS_ULT = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{5.0F, 0.44F, -0.30F, -0.66F, 46F, 6F, -8F, 1.06F},
			{60.0F, 0.44F, -0.28F, -0.64F, 50F, 6F, -8F, 1.06F},
			{68.0F, 0.52F, -0.44F, -0.70F, 18F, 2F, -3F, 1.0F}};
	/** 체공 훈풍 활공 — 팔을 느슬하게 벌린 자세. */
	private static final float[][] GS_GLIDE = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F},
			{4.0F, 0.66F, -0.60F, -0.66F, -6F, 16F, 12F, 1.0F},
			{200.0F, 0.66F, -0.60F, -0.66F, -6F, 16F, 12F, 1.0F}};

	/**
	 * 직업별 1인칭 평타 — {시각, x, y, z, 가로축 회전, 세로축 회전, 화면축 회전, 크기}.
	 * 공용 {@link #BASIC} 은 손만 좌우로 흔들어 무기 무게가 전혀 느껴지지 않았습니다 (0.2c).
	 * 본편 6틱 + 되돌아오는 4틱 — 무기가 무거울수록 늦게 떨어지고 더 깊이 내려갑니다.
	 *
	 * 워리어 철도끼: 오른쪽 위로 치켜들었다가 왼쪽 아래로 내려베기.
	 */
	private static final float[][] WR_BASIC = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F},
			{1.2F, 0.78F, -0.30F, -0.62F, -40F, 36F, -52F, 1.02F},
			{3.0F, 0.26F, -0.68F, -0.86F, 46F, -48F, 70F, 1.10F},
			{4.2F, 0.38F, -0.62F, -0.80F, 28F, -28F, 46F, 1.05F},
			{6.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F}};
	/** 워리어 역방향: 왼쪽 위에서 오른쪽 아래로. */
	private static final float[][] WR_BASIC_BACK = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F},
			{1.2F, 0.41F, -0.30F, -0.62F, -40F, -36F, 52F, 1.02F},
			{3.0F, 0.77F, -0.68F, -0.86F, 46F, 48F, -70F, 1.10F},
			{4.2F, 0.69F, -0.62F, -0.80F, 28F, 28F, -46F, 1.05F},
			{6.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F}};

	/** 햄머나이트 철퇴: 머리 위로 들었다가 곧장 내리찍고 반동으로 한 번 튐 (가장 무거움). */
	private static final float[][] HK_BASIC = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F},
			{1.5F, 0.60F, -0.26F, -0.56F, -52F, 10F, -14F, 1.06F},
			{3.2F, 0.44F, -0.60F, -0.88F, 44F, -8F, 14F, 1.18F},
			{4.4F, 0.48F, -0.58F, -0.82F, 32F, -6F, 10F, 1.08F},
			{6.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F}};
	/** 햄머나이트 역방향: 왼쪽 위에서 비스듬히 내리찍음. */
	private static final float[][] HK_BASIC_BACK = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F},
			{1.5F, 0.53F, -0.26F, -0.56F, -52F, -10F, 14F, 1.06F},
			{3.2F, 0.64F, -0.60F, -0.88F, 44F, 8F, -14F, 1.18F},
			{4.4F, 0.62F, -0.58F, -0.82F, 32F, 6F, -10F, 1.08F},
			{6.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F}};

	/** 셰이드 그림자 검: 짧게 뒤로 뺐다가 화면을 가로로 훑는 빠른 베기 (가장 가벼움). */
	private static final float[][] SD_BASIC = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F},
			{0.8F, 0.76F, -0.46F, -0.64F, -16F, 48F, -30F, 1.00F},
			{2.2F, 0.18F, -0.56F, -0.86F, 14F, -62F, 52F, 1.08F},
			{3.4F, 0.30F, -0.54F, -0.80F, 8F, -38F, 34F, 1.03F},
			{6.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F}};
	/** 셰이드 역방향: 왼쪽에서 오른쪽으로 되돌려 베기. */
	private static final float[][] SD_BASIC_BACK = {
			{0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F},
			{0.8F, 0.42F, -0.46F, -0.64F, -16F, -48F, 30F, 1.00F},
			{2.2F, 0.83F, -0.56F, -0.86F, 14F, 62F, -52F, 1.08F},
			{3.4F, 0.74F, -0.54F, -0.80F, 8F, 38F, -34F, 1.03F},
			{6.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.00F}};

	/** 바닐라 기본 손 자세 — 키프레임이 끝나면 여기로 돌아옵니다. */
	private static final float[] BASE = {0.0F, 0.56F, -0.52F, -0.72F, 0F, 0F, 0F, 1.0F};

	private FirstPersonAnim() {}

	private static SkillAnims.@Nullable Play offhandPlay() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null
				: SkillAnims.latestOf(mc.player.getId(), SkillAnimPayload.HK_CHARGE, SkillAnimPayload.IF_SHOT, SkillAnimPayload.IF_CHARGE,
						SkillAnimPayload.IF_PUNCH, SkillAnimPayload.IF_BLOCK, SkillAnimPayload.IF_SLAM_HIT,
						SkillAnimPayload.BR_REGROUP,
						// 건슬링어는 양손에 한 자루씩 — 왼손 총도 같은 키프레임을 그대로 따릅니다 (invert 가 좌우를 뒤집음)
						SkillAnimPayload.GS_SHOT_L, SkillAnimPayload.GS_RELOAD, SkillAnimPayload.GS_BOOST, SkillAnimPayload.GS_ACRO,
						SkillAnimPayload.GS_ANCHOR, SkillAnimPayload.GS_ULT, SkillAnimPayload.GS_GLIDE);
	}

	private static final net.minecraft.resources.Identifier VALKYRIE_RIFLE = kr.overbreak.Overbreak.id("valkyrie_rifle");

	/** 두 손으로 잡는 총인가 (1인칭에서 양팔을 같이 그림). */
	public static boolean twoHandedGun(net.minecraft.world.item.ItemStack stack) {
		return VALKYRIE_RIFLE.equals(stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL));
	}

	/**
	 * 총을 그린 자세(손 기준점)에서 팔을 그릴 자리로. 바닐라 팔 그리기는 화면 원점 기준이라 먼저 손 기준점만큼 되돌립니다.
	 *   오른손: 바닐라 빈손 자리보다 조금 뒤 · 아래 — 뒤쪽 손잡이를 쥠
	 *   왼손  : 화면 가운데 아래로 옮겨 앞쪽 총열 아래를 받침 (팔이 오른쪽 위로 뻗어 총열에 닿음)
	 */
	public static void gunGrip(PoseStack pose, int invert, boolean support) {
		pose.translate(-invert * 0.56F, 0.52F, 0.72F);
		if (!support) {
			pose.translate(invert * 0.06F, -0.08F, 0.06F);
			return;
		}
		pose.translate(invert * 0.98F, 0.0F, -0.18F);
	}

	/** 받치는 손이 잡는 자리 — 총 몸통 앞쪽 밑면 (총 기준 블록, 주 손이 오른손일 때). 팔 두께 반만큼 아래. */
	private static final Vector3f GRIP = new Vector3f(-0.05F, -0.04F, -0.30F);
	/** 손에 든 탄창을 붙이는 자리 = 총에 끼운 탄창 밑면에서 팔 두께 반만큼 아래 (총 기준). 손이 여기 오면 탄창을 잡은 것. */
	private static final Vector3f MAG_HOLD = new Vector3f(-0.021F, -0.131F, -0.202F);
	/** 받치는 팔의 어깨 쪽 (카메라 기준) — 팔은 화면 왼쪽 아래에서 손을 향해 뻗습니다. */
	private static final Vector3f SHOULDER = new Vector3f(-0.3F, -0.8F, -0.75F);
	private static final float ARM_SCALE = 0.8F;

	/** 받치는 팔 · 손에 든 탄창의 포즈 (손 기준점이 없는 기본 포즈 위에 곱함). 탄창이 총에 끼워져 있으면 magazine 은 null. */
	public record GunHands(Matrix4f arm, @Nullable Matrix4f magazine) {}

	/**
	 * 두 손 총의 받치는 팔. 손 자리를 정하고 어깨 쪽에서 그 자리로 곧게 뻗은 팔을 만듭니다 (팔 안쪽이 위 — 총 밑을 받침).
	 * 재장전 중에는 애니메이션 파일의 firstperson_left_hand · firstperson_magazine · v.magazine_out 을 따릅니다.
	 * @param rel 기본 포즈 → 총 포즈
	 */
	public static GunHands gunHands(Matrix4f rel, int invert, int playerId, float partial) {
		Vector3f offset = new Vector3f();
		Vector3f magRot = new Vector3f();
		boolean out = false;
		float seconds = ReloadAnim.seconds(playerId, partial);
		PlayerAnimation anim = seconds < 0.0F ? null : ReloadAnim.animation();
		if (anim != null) {
			kr.overbreak.client.anim.data.Molang.Context c = new kr.overbreak.client.anim.data.Molang.Context();
			double[] p = anim.sample(DataPose.FIRST_PERSON_LEFT_HAND, PlayerAnimation.POSITION, seconds, c);
			if (p != null) {
				offset.set((float) p[0] / 16.0F, (float) p[1] / 16.0F, (float) p[2] / 16.0F);
			}
			double[] r = anim.sample(DataPose.FIRST_PERSON_MAGAZINE, PlayerAnimation.ROTATION, seconds, c);
			if (r != null) {
				magRot.set((float) r[0], (float) r[1], (float) r[2]);
			}
			out = anim.variable(ReloadAnim.MAGAZINE_OUT, seconds, 0.0) >= 0.5;
		}
		Vector3f hand = rel.transformPosition(new Vector3f(invert * (GRIP.x + offset.x), GRIP.y + offset.y, GRIP.z + offset.z));
		// 손을 내리면 어깨도 조금 따라 내려 팔이 뒤집히지 않게
		Vector3f shoulder = new Vector3f(invert * SHOULDER.x, SHOULDER.y + Math.min(0.0F, offset.y) * 0.5F, SHOULDER.z);
		Vector3f dir = new Vector3f(hand).sub(shoulder).normalize();
		// 팔 안쪽(몸 쪽) 면이 위를 보게: 왼팔은 모델 -X, 오른팔은 +X 가 안쪽
		Vector3f side = new Vector3f(0.0F, 1.0F, 0.0F).sub(new Vector3f(dir).mul(dir.y));
		if (side.lengthSquared() < 1.0E-6F) {
			side.set(1.0F, 0.0F, 0.0F);
		}
		side.normalize().mul(-invert);
		Vector3f depth = new Vector3f(side).cross(dir);
		Matrix3f target = new Matrix3f(side, dir, depth);
		// 바닐라 손 그리기가 팔을 살짝 기울임 (왼팔 zRot -0.1, 오른팔 +0.1)
		Matrix3f lean = new Matrix3f().rotationZ(-0.1F * invert);
		Matrix3f turn = new Matrix3f(target).mul(new Matrix3f(lean).transpose());
		// 모델 팔의 손바닥 자리: 어깨 기준점(±5, 2) 에서 팔 방향으로 8.5 픽셀
		Vector3f palm = lean.transform(new Vector3f(invert, 8.5F, 0.0F)).add(5.0F * invert, 2.0F, 0.0F).div(16.0F);
		Matrix4f arm = new Matrix4f().translation(hand).mul(new Matrix4f().set(turn)).scale(ARM_SCALE).translate(-palm.x, -palm.y, -palm.z);

		Matrix4f magazine = null;
		if (out) {
			Matrix3f gunTurn = rel.get3x3(new Matrix3f());
			magazine = new Matrix4f().translation(hand).mul(new Matrix4f().set(gunTurn))
					.rotateY(invert * magRot.y * Mth.DEG_TO_RAD).rotateX(magRot.x * Mth.DEG_TO_RAD).rotateZ(invert * magRot.z * Mth.DEG_TO_RAD)
					.translate(-invert * MAG_HOLD.x, -MAG_HOLD.y, -MAG_HOLD.z);
		}
		return new GunHands(arm, magazine);
	}

	/** 왼손 아이템(방패 · 철권포 · 강화 포션) 애니메이션 중인가. */
	public static boolean offhandItemActive() {
		return offhandPlay() != null;
	}

	/** 왼손 아이템 자세를 통째로 정합니다 (돌진 충격 방패 · 철권포 장갑 · 강화 포션). */
	public static boolean applyOffhandItem(PoseStack pose, HumanoidArm arm, float partial) {
		SkillAnims.Play play = offhandPlay();
		if (play == null) {
			return false;
		}
		float[][] keys = switch (play.anim) {
			case SkillAnimPayload.GS_SHOT_L -> GS_SHOT;
			case SkillAnimPayload.GS_RELOAD -> GS_RELOAD;
			case SkillAnimPayload.GS_BOOST -> GS_BOOST;
			case SkillAnimPayload.GS_ACRO -> GS_ACRO;
			case SkillAnimPayload.GS_ANCHOR -> GS_ANCHOR;
			case SkillAnimPayload.GS_ULT -> GS_ULT;
			case SkillAnimPayload.GS_GLIDE -> GS_GLIDE;
			case SkillAnimPayload.BR_REGROUP -> BR_POTION_OFF;
			case SkillAnimPayload.HK_CHARGE -> HK_SHIELD;
			case SkillAnimPayload.IF_SHOT -> IF_SHOT_OFF;
			case SkillAnimPayload.IF_CHARGE -> IF_AIM_OFF;
			case SkillAnimPayload.IF_PUNCH -> IF_PUNCH_OFF;
			default -> IF_LOWER_OFF;
		};
		keyed(pose, arm == HumanoidArm.RIGHT ? 1 : -1, keys, play.elapsed(partial), play.end(), SkillAnims.fade(play.anim));
		return true;
	}

	/** 키프레임 자세. 본편이 끝나면(end) fade 동안 기본 자세로 돌아옵니다. */
	private static void keyed(PoseStack pose, int invert, float[][] keys, float e, float end, float fade) {
		float[] v = sampleKeys(keys, Math.min(e, end));
		if (e > end) {
			float q = Mth.clamp((e - end) / fade, 0.0F, 1.0F);
			q = q * q * (3.0F - 2.0F * q);
			float[] base = BASE;
			for (int c = 1; c < v.length; c++) {
				v[c] = Mth.lerp(q, v[c], base[c]);
			}
		}
		pose.translate(invert * v[1], v[2], v[3]);
		pose.mulPose(Axis.YP.rotationDegrees(invert * v[5]));
		pose.mulPose(Axis.XP.rotationDegrees(v[4]));
		pose.mulPose(Axis.ZP.rotationDegrees(invert * v[6]));
		pose.scale(v[7], v[7], v[7]);
	}

	private static float[] sampleKeys(float[][] keys, float t) {
		if (t <= keys[0][0]) {
			return keys[0].clone();
		}
		for (int i = 0; i < keys.length - 1; i++) {
			if (t < keys[i + 1][0]) {
				float x = (t - keys[i][0]) / (keys[i + 1][0] - keys[i][0]);
				float s = x * x * (3.0F - 2.0F * x);
				float[] out = new float[keys[i].length];
				out[0] = t;
				for (int c = 1; c < out.length; c++) {
					out[c] = Mth.lerp(s, keys[i][c], keys[i + 1][c]);
				}
				return out;
			}
		}
		return keys[keys.length - 1].clone();
	}

	private static SkillAnims.@Nullable Play current() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null
				: SkillAnims.latestOf(mc.player.getId(), SkillAnimPayload.SLAY, SkillAnimPayload.BASIC, SkillAnimPayload.BASIC_BACK,
						SkillAnimPayload.HK_SMASH, SkillAnimPayload.HK_SLAM, SkillAnimPayload.HK_ULT,
						SkillAnimPayload.IF_CHARGE, SkillAnimPayload.IF_PUNCH, SkillAnimPayload.IF_BLOCK, SkillAnimPayload.IF_SLAM_AIR,
						SkillAnimPayload.IF_SLAM_HIT, SkillAnimPayload.IF_ULT_RISE, SkillAnimPayload.IF_ULT_DROP,
						SkillAnimPayload.VK_SHOT, SkillAnimPayload.VK_ROCKET, SkillAnimPayload.VK_FLOAT, SkillAnimPayload.VK_OVERHEAT,
						SkillAnimPayload.VK_BARRAGE, SkillAnimPayload.VK_RELOAD,
						SkillAnimPayload.SH_SHOT, SkillAnimPayload.SH_FAN, SkillAnimPayload.SH_RELOAD, SkillAnimPayload.SH_ROLL,
						SkillAnimPayload.SH_FLASH, SkillAnimPayload.SH_DEADEYE, SkillAnimPayload.SH_DEADEYE_FIRE,
						SkillAnimPayload.SD_REND, SkillAnimPayload.SD_EVADE, SkillAnimPayload.SD_KUNAI, SkillAnimPayload.SD_STRIKE, SkillAnimPayload.SD_STEP,
						SkillAnimPayload.TH_CAST, SkillAnimPayload.TH_DASH, SkillAnimPayload.TH_FIELD, SkillAnimPayload.TH_SMITE, SkillAnimPayload.TH_ULT,
						SkillAnimPayload.BR_BASIC, SkillAnimPayload.BR_BASIC_BACK, SkillAnimPayload.BR_BLOW,
						SkillAnimPayload.BR_WHIRL, SkillAnimPayload.BR_REGROUP, SkillAnimPayload.BR_ULT,
						SkillAnimPayload.GS_SHOT, SkillAnimPayload.GS_RELOAD, SkillAnimPayload.GS_BOOST, SkillAnimPayload.GS_ACRO,
						SkillAnimPayload.GS_ANCHOR, SkillAnimPayload.GS_ULT, SkillAnimPayload.GS_GLIDE);
	}

	/** 지금 1인칭 동작의 firstperson_item_spin (총 기준, 손끝 축) — 없으면 null. */
	public static @Nullable Matrix4f itemSpin(int invert, float partial) {
		SkillAnims.Play play = current();
		return play == null ? null : RevolverHands.spin(play, invert, partial);
	}

	public static boolean active() {
		return current() != null;
	}

	/**
	 * 손에 든 아이템의 자세를 통째로 정합니다. 호출 전 poseStack 은 손 변환이 없는 기본 상태여야 합니다.
	 * @return 자세를 정했으면 true
	 */
	public static boolean apply(PoseStack pose, HumanoidArm arm, float partial) {
		SkillAnims.Play play = current();
		if (play == null) {
			return false;
		}
		int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
		float e = play.elapsed(partial);
		float end = play.end();
		PlayerAnimation data = PlayerAnimations.forSkill(play.anim);
		Minecraft mc = Minecraft.getInstance();
		float age = mc.player == null ? 0.0F : (float) ((mc.player.tickCount + partial) * kr.overbreak.core.tick.Ticks.step());
		if (data != null && DataPose.firstPerson(pose, invert, data, e, end, SkillAnims.fade(play.anim), age)) {
			Matrix4f spin = RevolverHands.spin(play, invert, partial);
			if (spin != null) {
				pose.mulPose(spin);
			}
			return true;
		}
		float[][] hk = switch (play.anim) {
			case SkillAnimPayload.HK_SMASH -> HK_SMASH;
			case SkillAnimPayload.HK_SLAM -> HK_SLAM;
			case SkillAnimPayload.HK_ULT -> HK_ULT;
			case SkillAnimPayload.IF_CHARGE -> IF_CHARGE;
			case SkillAnimPayload.IF_PUNCH -> IF_PUNCH;
			case SkillAnimPayload.IF_BLOCK -> IF_BLOCK;
			case SkillAnimPayload.IF_SLAM_AIR -> IF_SLAM_AIR;
			case SkillAnimPayload.IF_SLAM_HIT -> IF_SLAM_HIT;
			case SkillAnimPayload.IF_ULT_RISE -> IF_ULT_RISE;
			case SkillAnimPayload.IF_ULT_DROP -> IF_ULT_DROP;
			case SkillAnimPayload.GS_SHOT -> GS_SHOT;
			case SkillAnimPayload.GS_RELOAD -> GS_RELOAD;
			case SkillAnimPayload.GS_BOOST -> GS_BOOST;
			case SkillAnimPayload.GS_ACRO -> GS_ACRO;
			case SkillAnimPayload.GS_ANCHOR -> GS_ANCHOR;
			case SkillAnimPayload.GS_ULT -> GS_ULT;
			case SkillAnimPayload.GS_GLIDE -> GS_GLIDE;
			default -> null;
		};
		if (play.anim == SkillAnimPayload.IF_CHARGE && e < end) {
			// 기가 찰수록 건틀릿이 떨림
			float power = Mth.clamp((e - 4.0F) / 20.0F, 0.0F, 1.0F);
			pose.translate(Mth.sin(e * 3.1F) * 0.012F * power, Mth.sin(e * 3.9F + 1.1F) * 0.012F * power, 0.0F);
		}
		if (hk != null) {
			keyed(pose, invert, hk, e, end, SkillAnims.fade(play.anim));
			return true;
		}
		if (play.anim == SkillAnimPayload.BASIC || play.anim == SkillAnimPayload.BASIC_BACK) {
			boolean back = play.anim == SkillAnimPayload.BASIC_BACK;
			float[][] cls = classBasic(back);
			if (cls != null) {
				keyed(pose, invert, cls, e, end, SkillAnims.fade(play.anim));
				return true;
			}
			basic(pose, invert, back ? BASIC_BACK : BASIC, e);
			return true;
		}
		if (play.anim != SkillAnimPayload.SLAY) {
			// 파일에 1인칭 뼈대가 없는 동작: 기본 손 자리
			pose.translate(invert * 0.56F, -0.52F, -0.72F);
			return true;
		}
		if (e >= end) {
			// 끝(또는 끊김): 아래에서 원래 손 위치로 올라옴
			float q = Mth.clamp((e - end) / SkillAnims.fade(play.anim), 0.0F, 1.0F);
			float up = 1.0F - cube(1.0F - q);
			pose.translate(invert * 0.56F, -0.52F - 0.6F * (1.0F - up), -0.72F);
			return true;
		}
		slay(pose, invert, e);
		return true;
	}

	/**
	 * 지금 직업의 평타 표 — 없으면 null (공용 {@link #BASIC} 으로 갑니다).
	 * 투귀는 자기 애니메이션 번호(BR_BASIC)를 따로 쓰므로 여기에 없습니다.
	 */
	private static float[] @Nullable [] classBasic(boolean back) {
		return switch (kr.overbreak.client.hud.HudState.classId()) {
			case "warrior" -> back ? WR_BASIC_BACK : WR_BASIC;
			case "hammer_knight" -> back ? HK_BASIC_BACK : HK_BASIC;
			case "shade" -> back ? SD_BASIC_BACK : SD_BASIC;
			default -> null;
		};
	}

	/** 기본 공격 — 카메라를 축으로 가로 베기 후 기본 손 자세로 부드럽게 복귀. */
	private static void basic(PoseStack pose, int invert, float[][] keys, float e) {
		float[] v = keys[keys.length - 1];
		for (int i = 0; i < keys.length - 1; i++) {
			if (e < keys[i + 1][0]) {
				float x = Mth.clamp((e - keys[i][0]) / (keys[i + 1][0] - keys[i][0]), 0.0F, 1.0F);
				float s = x * x * (3.0F - 2.0F * x);
				v = new float[6];
				for (int c = 1; c < 6; c++) {
					v[c] = Mth.lerp(s, keys[i][c], keys[i + 1][c]);
				}
				break;
			}
		}
		pose.mulPose(Axis.YP.rotationDegrees(invert * v[1]));
		pose.translate(invert * v[2], v[3], v[4]);
		pose.mulPose(Axis.ZP.rotationDegrees(invert * v[5]));
	}

	/**
	 * 살육.
	 *   기 모으기 (0~13): 도끼가 가운데로 들려 올라와 점점 빨라지며 자전 (서버 디스플레이와 같은 가속: 틱당 3t 도),
	 *                   점점 커지고 떨림이 강해짐
	 *   발동 (13~21):    카메라를 축으로 오른쪽 → 왼쪽으로 틱당 72도 회전 (주위를 휘도는 도끼와 같은 속도)
	 */
	private static void slay(PoseStack pose, int invert, float e) {
		if (e < SLAY_IMPACT) {
			float r = 1.0F - cube(1.0F - Mth.clamp(e / 5.0F, 0.0F, 1.0F));
			float x = Mth.lerp(r, 0.56F, 0.20F);
			float y = Mth.lerp(r, -0.52F, -0.36F);
			float z = Mth.lerp(r, -0.72F, -0.80F);
			float power = Mth.clamp((e - 4.0F) / (SLAY_IMPACT - 4.0F), 0.0F, 1.0F);
			float shake = 0.018F * power;
			x += Mth.sin(e * 2.9F) * shake;
			y += Mth.sin(e * 3.7F + 1.3F) * shake;
			pose.translate(invert * x, y, z);
			pose.mulPose(Axis.XP.rotationDegrees(-20.0F * r));
			pose.mulPose(Axis.YP.rotationDegrees(invert * 1.5F * e * e));
			float s = 1.0F + 0.3F * (e / SLAY_IMPACT);
			pose.scale(s, s, s);
		} else {
			float u = e - SLAY_IMPACT;
			float angle = -70.0F + u * 72.0F;
			pose.mulPose(Axis.YP.rotationDegrees(invert * angle));
			// 손잡이 끝이 원점이라 그대로 두면 도끼가 궤도점 왼쪽으로 쏠립니다. 반 길이만큼 되돌려 가운데를 맞춥니다.
			pose.translate(invert * 0.35F, -0.36F, -1.0F);
			// 비스듬히 베는 각도 (도끼머리가 진행 방향 위쪽)
			pose.mulPose(Axis.ZP.rotationDegrees(invert * 62.0F));
			float s = 1.2F + 0.3F * Math.max(0.0F, 1.0F - u / 2.0F);
			pose.scale(s, s, s);
		}
	}

	/**
	 * 피의 사슬 1인칭 왼팔 — 바닐라 빈손 팔 그리기(renderPlayerArm) 앞에 카메라 공간 이동을 얹습니다.
	 *   0~3   : 아래에서 올라옴
	 *   0~13  : 왼쪽 위에 들고 서버 갈고리와 같은 박자로 원을 그리며 돌림
	 *   13~16 : 어깨 쪽은 화면 왼쪽에 두고 팔 끝을 안쪽(조준점 쪽)으로 틀어 뻗어 던짐
	 *   16~   : 뻗은 채 유지, 견인 박자에 맞춰 잡아당김
	 *   끝    : 아래로 내려감
	 * @return 그려야 하면 true
	 */
	public static boolean applyOffhandArm(PoseStack pose, HumanoidArm arm, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
		SkillAnims.Play kunai = SkillAnims.find(mc.player.getId(), SkillAnimPayload.SD_KUNAI);
		if (kunai != null) {
			kunaiArm(pose, invert, kunai.elapsed(partial), kunai.end(), SkillAnims.fade(kunai.anim));
			return true;
		}
		SkillAnims.Play play = SkillAnims.find(mc.player.getId(), SkillAnimPayload.CHAIN);
		if (play == null) {
			return false;
		}
		float e = play.elapsed(partial);
		float end = play.end();
		float t = Math.min(e, end);

		float rise = 1.0F - cube(1.0F - Mth.clamp(e / 3.0F, 0.0F, 1.0F));
		float drop = Mth.clamp((e - end) / SkillAnims.fade(play.anim), 0.0F, 1.0F);
		float x = 0.02F;
		float y = 0.32F;
		float z = 0.0F;
		float roll = 0.0F;
		float inward = 0.0F;
		if (t < 13.0F) {
			float ramp = Mth.clamp((t - 1.0F) / 3.0F, 0.0F, 1.0F);
			double ph = Math.toRadians(kr.overbreak.classes.warrior.ChainSpin.angleDeg(e + (float) kr.overbreak.core.tick.Ticks.step()));
			x += 0.09F * (float) Math.cos(ph) * ramp;
			y += 0.09F * (float) Math.sin(ph) * ramp;
			roll = 12.0F * (float) Math.sin(ph) * ramp;
		} else {
			float s = Mth.clamp((t - 13.0F) / 2.5F, 0.0F, 1.0F);
			s = s * s * (3.0F - 2.0F * s);
			x = Mth.lerp(s, x, -0.16F);
			y = Mth.lerp(s, y, 0.24F);
			z = Mth.lerp(s, z, -0.18F);
			inward = 24.0F * s;
			if (t >= 26.0F) {
				float p = Math.max(0.0F, Mth.sin((t - 26.0F) * Mth.PI / 5.0F));
				z += 0.10F * p * p;
				y -= 0.03F * p * p;
			}
		}
		y -= 0.7F * (1.0F - rise) + 0.8F * drop;
		// 왼팔 기준 좌표: 화면 가운데 쪽이 +x 이므로 팔 방향(invert)을 곱합니다
		pose.translate(-invert * x, y, z);
		if (inward != 0.0F) {
			// 바닐라 팔 기준점(어깨 쪽)을 축으로 돌려 팔의 시작은 왼쪽에 남기고 팔 끝만 안쪽으로
			pose.translate(invert * 0.64F, -0.6F, -0.72F);
			pose.mulPose(Axis.YP.rotationDegrees(invert * inward));
			pose.translate(-invert * 0.64F, 0.6F, 0.72F);
		}
		pose.mulPose(Axis.ZP.rotationDegrees(-invert * roll));
		return true;
	}

	/**
	 * 그림자 표창 1인칭 왼팔 (틱, 카메라 기준 · 바닐라 빈손 팔 자리에서 옮기는 양):
	 *   0~2 아래에서 올라오며 뒤로 젖힘 → 2~4 안쪽(조준점 쪽) 앞으로 뿌림 (3틱에 손을 떠남) → 4~8 뻗은 채 → 끝나면 아래로
	 *   {x (피의 사슬과 같은 방향), y, z(- 앞), 팔 끝을 안쪽(조준점 쪽)으로 트는 각도}
	 */
	private static final float[][] KUNAI_ARM = {
			{0.0F, 0.00F, -0.30F, 0.10F, 0.0F},
			{2.0F, 0.04F, 0.34F, 0.16F, -6.0F},
			{3.0F, -0.10F, 0.32F, -0.06F, 16.0F},
			{4.0F, -0.18F, 0.26F, -0.22F, 28.0F},
			{8.0F, -0.14F, 0.20F, -0.16F, 24.0F}};
	/** 표창이 손을 떠나는 틱 (서버는 누른 순간 던지지만 화면에서는 뿌리는 동작 한가운데). */
	public static final float KUNAI_RELEASE = 3.0F;

	private static void kunaiArm(PoseStack pose, int invert, float e, float end, float fade) {
		float[] v = new float[5];
		float t = Math.min(e, end);
		float[] a = KUNAI_ARM[KUNAI_ARM.length - 1];
		float[] b = a;
		float s = 1.0F;
		for (int i = 0; i < KUNAI_ARM.length - 1; i++) {
			if (t < KUNAI_ARM[i + 1][0]) {
				a = KUNAI_ARM[i];
				b = KUNAI_ARM[i + 1];
				float x = Mth.clamp((t - a[0]) / (b[0] - a[0]), 0.0F, 1.0F);
				s = x * x * (3.0F - 2.0F * x);
				break;
			}
		}
		for (int c = 1; c < 5; c++) {
			v[c] = Mth.lerp(s, a[c], b[c]);
		}
		float drop = Mth.clamp((e - end) / fade, 0.0F, 1.0F);
		v[2] -= 0.8F * drop * drop;
		pose.translate(-invert * v[1], v[2], v[3]);
		if (v[4] != 0.0F) {
			pose.translate(invert * 0.64F, -0.6F, -0.72F);
			pose.mulPose(Axis.YP.rotationDegrees(invert * v[4]));
			pose.translate(-invert * 0.64F, 0.6F, 0.72F);
		}
	}

	/** 왼손에 표창을 쥐고 있는가 (던지기 전). */
	public static boolean kunaiInHand(float partial) {
		Minecraft mc = Minecraft.getInstance();
		SkillAnims.Play p = mc.player == null ? null : SkillAnims.find(mc.player.getId(), SkillAnimPayload.SD_KUNAI);
		return p != null && p.elapsed(partial) < KUNAI_RELEASE;
	}

	/**
	 * 바닐라 1인칭 빈손 팔 그리기(renderPlayerArm, 휘두르기 0) 뒤의 손끝 자리 — 팔 자세 위에 곱하면 손에 쥔 물건 자리.
	 * 팔 모델: 어깨 기준점 (∓5, 2) · zRot ±0.1 · 손끝 (±1, 10) 픽셀.
	 */
	public static Vector3f handTip(int invert) {
		return new Matrix4f()
				.translation(invert * 0.64F, -0.6F, -0.72F)
				.rotateY(invert * (float) Math.toRadians(45.0))
				.translate(invert * -1.0F, 3.6F, 3.5F)
				.rotateZ(invert * (float) Math.toRadians(120.0))
				.rotateX((float) Math.toRadians(200.0))
				.rotateY(invert * (float) Math.toRadians(-135.0))
				.translate(invert * 5.6F, 0.0F, 0.0F)
				.translate(invert * -5.0F / 16.0F, 2.0F / 16.0F, 0.0F)
				.rotateZ(invert * 0.1F)
				.transformPosition(new Vector3f(invert * -1.0F / 16.0F, 9.0F / 16.0F, 0.0F));
	}

	private static float cube(float v) {
		return v * v * v;
	}
}
