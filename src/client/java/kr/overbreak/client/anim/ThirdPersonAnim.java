package kr.overbreak.client.anim;

import kr.overbreak.classes.warrior.ChainSpin;
import kr.overbreak.client.anim.data.DataPose;
import kr.overbreak.client.anim.data.PlayerAnimation;
import kr.overbreak.client.anim.data.PlayerAnimations;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * 3인칭 플레이어 모델 애니메이션 — 팔 · 몸통 · 머리 · 다리 각도를 키프레임으로 정합니다.
 *
 * 각도는 라디안, 모델 기준입니다.
 *   팔 xRot 음수 = 앞/위로 들기 (-π 가 머리 위), 오른팔 zRot 양수 · 왼팔 zRot 음수 = 옆으로 벌리기
 *   오른팔 yRot 양수 = 오른쪽으로, 음수 = 왼쪽으로 (팔을 앞으로 뻗었을 때)
 *   몸통 xRot 양수 = 앞으로 숙이기, 몸통 yRot 양수 = 오른쪽 어깨가 뒤로, 머리 xRot 음수 = 위를 봄
 *
 * 상체(팔 · 몸통 · 머리)와 하체(다리)는 따로 섞습니다.
 *   상체: 시작 · 끝 fade 동안 바닐라 자세와 가중치로 섞음
 *   다리: 걷는 중이면 바닐라 걷기 동작을 그대로 두고, 멈춰 있을 때만 스킬 자세(스탠스)를 씀
 *   예외: 살육의 회전은 다리까지 몸 전체가 돕니다 (걷는 중이면 걷기 동작을 하면서 회전).
 *
 * Blockbench 애니메이션 파일에 스킬 이름({@link PlayerAnimations#name})이 있으면 아래 코드 동작 대신 파일 동작을 씁니다.
 * 발키리는 전부 파일입니다 (assets/overbreak/player_animations/valkyrie.animation.json, 사용법은 docs/ANIMATION.md).
 */
public final class ThirdPersonAnim {
	private static final float K = Float.NaN; // 머리: 바닐라(시선) 유지

	/** 걷기 동작 세기가 이 값 이상이면 다리는 걷기 동작만 씁니다. */
	/** 돌개바람 회전 속도 (1/20초 단위당 도) — 1초에 세 바퀴. */
	private static final float WHIRL_SPIN = 54.0F;

	private static final float WALK_FULL = 0.25F;

	private static final int RAX = 0, RAY = 1, RAZ = 2, LAX = 3, LAY = 4, LAZ = 5, BX = 6, BY = 7, HX = 8,
			RLX = 9, RLZ = 10, LLX = 11, LLZ = 12;

	private record Key(float t, float[] v) {}

	private static Key key(float t, float... v) {
		return new Key(t, v);
	}

	//                              오른팔 x/y/z               왼팔 x/y/z                 몸통 x/y     머리 x   오른다리 x/z   왼다리 x/z
	private static final float[] SLAY_CHARGE = {-2.75F, 0F, 0.15F,  -2.55F, 0F, -0.25F,  -0.12F, 0F,  K,      0F, 0.12F,    0F, -0.12F};
	private static final float[] SLAY_SPIN   = {-0.35F, 0F, 1.45F,  -0.40F, 0F, -0.70F,   0.12F, 0F,  K,      0F, 0.15F,    0F, -0.15F};
	/** 살육: 도끼를 머리 위로 치켜들고 기를 모았다가, 팔을 뻗은 채 상체가 회전. */
	private static final Key[] SLAY = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, K, 0, 0, 0, 0),
			key(4, SLAY_CHARGE), key(13, SLAY_CHARGE),
			key(14.5F, SLAY_SPIN), key(21, SLAY_SPIN)};

	private static final float[] FURY_ROAR = {-0.55F, 0F, 1.95F,  -0.55F, 0F, -1.95F,  -0.25F, 0F, -0.6F,   0F, 0.12F,    0F, -0.12F};
	/** 광란의 포효: 몸을 웅크렸다가 두 팔을 위로 벌리며 포효. */
	private static final Key[] FURY = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			key(2, 0.45F, 0, 0.35F, 0.45F, 0, -0.35F, 0.3F, 0, 0.3F, 0, 0, 0, 0),
			key(5, FURY_ROAR), key(12, FURY_ROAR)};

	private static final float[] CHAIN_WIND  = {-0.35F, 0F, 0.2F,   -2.3F, 0F, -0.55F,   0F, -0.3F,    K,  0F, 0.12F,     0F, -0.12F};
	private static final float[] CHAIN_THROW = {-0.15F, 0F, 0.35F,  -1.7F, -0.15F, 0F,   0.18F, 0.4F,  K,  0.35F, 0.05F,  -0.4F, -0.05F};
	private static final float[] CHAIN_HOLD  = {-0.45F, 0F, 0.25F,  -1.5F, 0.05F, 0F,    0.05F, 0.2F,  K,  0.2F, 0.05F,   -0.2F, -0.05F};
	/**
	 * 피의 사슬: 왼팔을 들어 사슬을 돌리고(0~13, 팔 끝이 갈고리와 같은 박자로 원을 그림),
	 * 몸을 틀며 왼손을 앞으로 뻗어 던지고(13~15), 뻗은 채 박자에 맞춰 잡아당김. 오른손 도끼는 편하게 내림.
	 */
	private static final Key[] CHAIN = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, K, 0, 0, 0, 0),
			key(3, CHAIN_WIND), key(13, CHAIN_WIND),
			key(14.5F, CHAIN_THROW), key(17, CHAIN_HOLD), key(80, CHAIN_HOLD)};

	private static final float[] ULT_ROAR = {0.35F, 0F, 0.95F,  0.35F, 0F, -0.95F,  -0.25F, 0F, -0.7F,  -0.2F, 0.2F,  0.2F, -0.2F};
	/** 광란의 처형장: 두 팔을 치켜들었다가 내리찍고, 팔을 벌려 포효. */
	private static final Key[] ULT = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			key(5, -3.0F, 0, 0.25F, -3.0F, 0, -0.25F, -0.2F, 0, -0.45F, 0, 0.1F, 0, -0.1F),
			key(7, -0.7F, 0, 0.2F, -0.7F, 0, -0.2F, 0.35F, 0, 0.25F, -0.4F, 0.15F, 0.25F, -0.15F),
			key(9, ULT_ROAR), key(22, ULT_ROAR)};

	private static final float[] BASIC_WIND   = {-1.35F, 1.0F, 0F,     0.2F, 0F, -0.2F,    0F, 0.45F,     K,  0F, 0.1F,     0F, -0.1F};
	private static final float[] BASIC_STRIKE = {-1.45F, -0.9F, 0F,   -0.2F, 0F, -0.15F,  0.12F, -0.5F,  K,  -0.2F, 0.1F,  0.2F, -0.1F};
	private static final float[] BASIC_FOLLOW = {-1.0F, -1.1F, 0.15F,  0.1F, 0F, -0.1F,   0.08F, -0.35F, K,  -0.2F, 0.1F,  0.2F, -0.1F};
	/** 기본 공격 (정방향): 오른쪽 뒤로 당겼다가 왼쪽으로 가로 베기. */
	private static final Key[] BASIC = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, K, 0, 0, 0, 0),
			key(1.5F, BASIC_WIND), key(3, BASIC_STRIKE), key(6, BASIC_FOLLOW)};

	private static final float[] BACK_WIND   = {-1.4F, -1.0F, 0F,     -0.2F, 0F, -0.15F,  0.05F, -0.45F, K,  0F, 0.1F,     0F, -0.1F};
	private static final float[] BACK_STRIKE = {-1.45F, 0.9F, 0.1F,    0.2F, 0F, -0.2F,   0.12F, 0.5F,   K,  0.2F, 0.1F,  -0.2F, -0.1F};
	private static final float[] BACK_FOLLOW = {-1.0F, 1.1F, 0.35F,    0.1F, 0F, -0.1F,   0.08F, 0.35F,  K,  0.2F, 0.1F,  -0.2F, -0.1F};
	/** 기본 공격 (역방향): 왼쪽으로 당겼다가 오른쪽으로 되베기. */
	private static final Key[] BASIC_BACK = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, K, 0, 0, 0, 0),
			key(1.5F, BACK_WIND), key(3, BACK_STRIKE), key(6, BACK_FOLLOW)};

	//                                오른팔 x/y/z               왼팔(방패) x/y/z           몸통 x/y      머리 x  오른다리 x/z  왼다리 x/z
	private static final float[] HK_RAISE = {-2.95F, 0F, 0.15F,   -0.6F, 0.35F, -0.1F,    -0.15F, 0.15F,  K,  0F, 0.1F,     0F, -0.1F};
	private static final float[] HK_DOWN  = {-0.75F, -0.15F, 0.05F, -0.4F, 0.3F, -0.1F,   0.45F, -0.2F,   K,  -0.35F, 0.12F, 0.3F, -0.12F};
	/** 햄머나이트 지면 분쇄 · 중력 파쇄: 망치를 머리 위로 들었다가(0~10) 내려찍음. */
	private static final Key[] HK_SMASH = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, K, 0, 0, 0, 0),
			key(4, HK_RAISE), key(10, HK_RAISE), key(11.5F, HK_DOWN), key(16, HK_DOWN)};
	/** 돌진 뒤 예약 분쇄: 곧바로 들었다가 내려찍음. */
	private static final Key[] HK_SLAM = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, K, 0, 0, 0, 0),
			key(2, HK_RAISE), key(3.5F, HK_DOWN), key(9, HK_DOWN)};

	private static final float[] HK_BRACE = {-0.2F, 0F, 0.25F,   -1.35F, 0.6F, 0F,   0.35F, -0.25F,  K,  0.35F, 0.05F, -0.35F, -0.05F};
	private static final float[] HK_RUSH  = {0.35F, 0F, 0.2F,    -1.5F, 0.65F, 0F,   0.5F, -0.3F,    K,  0F, 0F,        0F, 0F};
	/** 돌진 충격: 몸을 낮추고 방패를 앞으로 세웠다가(0~6) 방패를 내민 채 돌진. */
	private static final Key[] HK_CHARGE = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, K, 0, 0, 0, 0),
			key(3, HK_BRACE), key(6, HK_BRACE), key(7.5F, HK_RUSH), key(14, HK_RUSH)};

	private static final float[] HK_PULL = {0.95F, 0.25F, 0.45F,   -0.7F, 0.3F, -0.2F,   0.15F, 0.8F,   K,     0.3F, 0.15F,  -0.35F, -0.15F};
	private static final float[] HK_BLOW = {-0.55F, -0.35F, 0F,   -0.3F, 0F, -0.4F,    0.75F, -0.55F, 0.3F,  -0.45F, 0.25F, 0.45F, -0.25F};
	/** 대지 진동파: 망치를 뒤로 크게 빼 힘을 모으고(0~16) 몸 전체를 실어 강하게 내려침. */
	private static final Key[] HK_ULT = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			key(6, HK_PULL), key(16, HK_PULL), key(17.2F, HK_BLOW), key(26, HK_BLOW)};

	//                                    오른팔(건틀릿) x/y/z        왼팔(철권포) x/y/z         몸통 x/y        머리 x   오른다리 x/z    왼다리 x/z
	private static final float[] IF_PULL   = {0.75F, 0.25F, 0.30F,   -1.05F, -0.35F, 0F,    0.30F, 0.50F,   K,       0.40F, 0.06F,  -0.45F, -0.06F};
	private static final float[] IF_EXT    = {-1.62F, 0.45F, 0F,     0.55F, 0.35F, -0.25F,  0.28F, -0.55F,  K,       0.55F, 0.05F,  -0.65F, -0.05F};
	/** 파쇄권 로켓 펀치 충전: 몸을 낮추고 건틀릿을 허리 뒤로 당겨 기를 모음 (왼손은 앞으로 겨눔). */
	private static final Key[] IF_CHARGE = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, K, 0, 0, 0, 0),
			key(4, IF_PULL), key(40, IF_PULL)};
	/** 로켓 펀치 돌진: 오른쪽 어깨를 앞으로 틀며 건틀릿을 곧게 내지름. */
	private static final Key[] IF_PUNCH = {
			key(0, IF_PULL), key(1.5F, IF_EXT), key(16, IF_EXT)};

	private static final float[] IF_GUARD  = {-1.95F, -0.70F, 0.10F,  -0.55F, 0.45F, 0F,    0.22F, 0.20F,   K,       0.25F, 0.08F,  -0.30F, -0.08F};
	/** 파워 블록: 건틀릿을 얼굴 앞으로 세워 막고 왼팔은 몸에 붙임. */
	private static final Key[] IF_BLOCK = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, K, 0, 0, 0, 0),
			key(3, IF_GUARD), key(40, IF_GUARD)};

	private static final float[] IF_AIR    = {-2.85F, 0.10F, 0.35F,   -0.35F, 0F, -1.05F,   -0.12F, 0.15F,  K,       -0.75F, 0.05F,  0.35F, -0.05F};
	private static final float[] IF_HIT    = {-0.55F, 0.15F, 0.05F,   0.65F, 0F, -0.55F,    0.85F, -0.35F,  -0.55F,  -1.05F, 0.08F,  0.75F, -0.08F};
	/** 지진 강타 도약: 건틀릿을 머리 위로 치켜들고 왼팔은 옆으로 벌려 균형 (둠피스트 지진 강타). */
	private static final Key[] IF_SLAM_AIR = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, K, 0, 0, 0, 0),
			key(3, IF_AIR), key(200, IF_AIR)};
	/** 지진 강타 착지: 몸을 깊이 숙이며 건틀릿을 땅에 내리꽂음. */
	private static final Key[] IF_SLAM_HIT = {
			key(0, IF_AIR), key(1.5F, IF_HIT), key(12, IF_HIT)};

	private static final float[] IF_CROUCH = {0.45F, 0F, 0.15F,     0.45F, 0F, -0.15F,     0.55F, 0F,      -0.4F,   -0.9F, 0.1F,    -0.9F, -0.1F};
	private static final float[] IF_UP     = {-3.05F, -0.1F, 0.08F,  0.25F, 0F, -0.25F,    -0.08F, 0F,     -0.6F,   0.2F, 0.02F,    0.05F, -0.02F};
	/** 파멸의 일격 솟구침: 웅크렸다가 건틀릿을 하늘로 뻗으며 뛰어오름 (둠피스트 파멸의 일격). */
	private static final Key[] IF_ULT_RISE = {
			key(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			key(3, IF_CROUCH), key(6, IF_UP), key(20, IF_UP)};

	private static final float[] IF_DIVE   = {-3.0F, 0F, 0.1F,      -2.6F, 0F, -0.3F,      -0.1F, 0F,      0.3F,    0.1F, 0F,       -0.1F, 0F};
	private static final float[] IF_LAND   = {-0.30F, 0.10F, 0F,    0.95F, 0F, -0.75F,     0.95F, -0.25F,  -0.7F,   -1.35F, 0.1F,   1.1F, -0.1F};
	/** 파멸의 일격 낙하: 건틀릿을 치켜든 채 떨어지다가 주먹부터 땅에 꽂는 착지 자세. */
	private static final Key[] IF_ULT_DROP = {
			key(0, IF_DIVE), key(4, IF_DIVE), key(5, IF_LAND), key(20, IF_LAND)};

	private ThirdPersonAnim() {}

	/** 렌더 상태 추출 단계 — 시간 · 가중치 · 상체 회전 · 손 아이템 크기. */
	public static void extract(int entityId, AvatarRenderState state, float partial) {
		AnimRenderState a = (AnimRenderState) state;
		SkillAnims.Play play = SkillAnims.latest(entityId);
		if (play == null) {
			a.overbreak$set(0, 0, 0, 0);
			a.overbreak$setItemScale(1.0F);
			a.overbreak$setSpin(0.0F);
			return;
		}
		float e = play.elapsed(partial);
		float end = play.end();
		float fade = SkillAnims.fade(play.anim);
		boolean basic = play.anim == SkillAnimPayload.BASIC || play.anim == SkillAnimPayload.BASIC_BACK;
		// 기본 공격은 짧아서 들어가는 시간을 1틱으로
		float fadeIn = basic ? 1.0F : chained(play.anim) ? 0.01F : play.anim == SkillAnimPayload.IF_SHOT ? 0.5F : 2.0F;
		float w = Math.min(Mth.clamp(e / fadeIn, 0.0F, 1.0F), 1.0F - Mth.clamp((e - end) / fade, 0.0F, 1.0F));
		a.overbreak$set(play.anim, e, end, w);

		float itemScale = 1.0F;
		float spin = 0.0F;
		float headSpin = 0.0F;
		if (play.anim == SkillAnimPayload.HK_ULT) {
			// 망치: 힘을 모으며 1.0 → 1.35배, 내려치는 순간 1.6배 → 1.3배
			float s = e < 16.0F ? 1.0F + 0.2F * Mth.clamp((e - 4.0F) / 12.0F, 0.0F, 1.0F)
					: 1.3F + 0.3F * Math.max(0.0F, 1.0F - (e - 16.0F) / 4.0F);
			itemScale = 1.0F + (s - 1.0F) * w;
		}
		if (play.anim == SkillAnimPayload.SLAY) {
			// 발동 후 틱당 72도 (1인칭 · 서버 도끼와 같은 속도). 오른손 휘두르기 방향 = 오른쪽 어깨가 앞으로 (음수).
			float spinEnd = -72.0F * Math.max(0.0F, Math.min(e, end) - 13.0F);
			spin = spinEnd;
			if (e > end && spinEnd != 0.0F) {
				// 끝나면 다음 한 바퀴 지점까지 감속하며 마저 돌아 원래 방향으로
				float target = (float) Math.floor(spinEnd / 360.0F) * 360.0F;
				float q = Mth.clamp((e - end) / fade, 0.0F, 1.0F);
				spin = Mth.lerp(1.0F - (1.0F - q) * (1.0F - q), spinEnd, target);
			}
			// 도끼 크기: 기 모으며 1.0→1.5, 발동 순간 2.5→2.0 (서버 디스플레이 0.9→1.5, 2.6 에 맞춤)
			float s = e < 13.0F ? 1.0F + 0.5F * (e / 13.0F) : 2.0F + 0.5F * Math.max(0.0F, 1.0F - (e - 13.0F) / 2.0F);
			itemScale = 1.0F + (s - 1.0F) * w;
		}
		if (play.anim == SkillAnimPayload.BR_WHIRL) {
			// 돌개바람: 1초에 세 바퀴. 몸통만 돌면 머리 · 다리가 제자리에 남아 기괴하므로 몸 전체가 돕니다 (0.2a)
			float turn = -WHIRL_SPIN * Math.min(e, end);
			if (e > end) {
				// 끝나면 가장 가까운 한 바퀴 지점까지 감속하며 마저 돌아 원래 방향으로
				float target = (float) Math.round(turn / 360.0F) * 360.0F;
				float q = Mth.clamp((e - end) / fade, 0.0F, 1.0F);
				turn = Mth.lerp(1.0F - (1.0F - q) * (1.0F - q), turn, target);
			}
			spin = turn;
			headSpin = turn;
		}
		if (play.anim == SkillAnimPayload.GS_ACRO) {
			// 공예 난사: 0.8초(16) 동안 정확히 한 바퀴 — 난사 방향과 몸 방향이 같아야 합니다
			float turn = -(360.0F / kr.overbreak.classes.gunslinger.AeroAcrobatics.DURATION) * Math.min(e, end);
			if (e > end) {
				float target = (float) Math.round(turn / 360.0F) * 360.0F;
				float q = Mth.clamp((e - end) / fade, 0.0F, 1.0F);
				turn = Mth.lerp(1.0F - (1.0F - q) * (1.0F - q), turn, target);
			}
			spin = turn;
			headSpin = turn;
		}
		a.overbreak$setItemScale(itemScale);
		// 살육 · 돌개바람은 몸 전체(다리 포함)가 돕니다. 상체만 도는 동작이 필요하면 overbreak$setSpin 을 씁니다.
		state.bodyRot += spin;
		// 머리 각도는 몸통 기준으로 계산되므로, 같이 돌려 줘야 머리도 함께 돕니다
		state.yRot += headSpin;
		a.overbreak$setSpin(0.0F);
	}

	/**
	 * 두 손 총(발키리 연사 포탑)을 든 기본 자세 — 파일의 valkyrie.ready (반복). 걸어도 팔은 그대로.
	 * 스킬 동작보다 먼저 깔아 두므로, 동작은 이 자세에서 시작해 이 자세로 돌아옵니다.
	 */
	private static void readyPose(HumanoidModel<?> m, AvatarRenderState state) {
		ItemStack main = state.mainArm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
		if (main == null || !FirstPersonAnim.twoHandedGun(main)) {
			return;
		}
		PlayerAnimation ready = PlayerAnimations.get(PlayerAnimations.VALKYRIE_READY);
		if (ready != null) {
			DataPose.apply(m, ready, state.ageInTicks * kr.overbreak.core.tick.Ticks.step() / 20.0, state.ageInTicks, 1.0F, 1.0F - walking(state));
		}
	}

	private static float walking(AvatarRenderState state) {
		return Mth.clamp(state.walkAnimationSpeed / WALK_FULL, 0.0F, 1.0F);
	}

	/** 앞 동작의 끝 자세에서 곧바로 이어지는 동작 — 바닐라 자세를 거쳐 섞어 들어가지 않습니다. */
	private static boolean chained(int anim) {
		return anim == SkillAnimPayload.IF_PUNCH || anim == SkillAnimPayload.IF_SLAM_HIT || anim == SkillAnimPayload.IF_ULT_DROP
				|| anim == SkillAnimPayload.VK_SHOT || anim == SkillAnimPayload.VK_ROCKET
				|| anim == SkillAnimPayload.SH_SHOT || anim == SkillAnimPayload.SH_FAN || anim == SkillAnimPayload.SH_DEADEYE_FIRE
				|| anim == SkillAnimPayload.SD_STRIKE || anim == SkillAnimPayload.TH_CAST;
	}

	/** 지금(바닐라) 자세 그대로 — 일부 부위만 바꾸는 동작용. */
	private static float[] current(HumanoidModel<?> m) {
		return new float[] {m.rightArm.xRot, m.rightArm.yRot, m.rightArm.zRot, m.leftArm.xRot, m.leftArm.yRot, m.leftArm.zRot,
				m.body.xRot, m.body.yRot, K, m.rightLeg.xRot, m.rightLeg.zRot, m.leftLeg.xRot, m.leftLeg.zRot};
	}

	/** 모델 자세 단계 — 바닐라 setupAnim 이 끝난 뒤 덮어씁니다. */
	public static void pose(HumanoidModel<?> m, AvatarRenderState state) {
		AnimRenderState a = (AnimRenderState) state;
		readyPose(m, state);
		int anim = a.overbreak$anim();
		float w = a.overbreak$weight();
		float spin = a.overbreak$spin();
		if (anim == 0 || (w <= 0.0F && spin == 0.0F)) {
			return;
		}
		float e = a.overbreak$time();
		float t = Math.min(e, a.overbreak$end());
		// 걷는 중에는 다리를 걷기 동작에 맡깁니다
		float legW = w * (1.0F - walking(state));
		PlayerAnimation data = PlayerAnimations.forSkill(anim);
		if (data != null) {
			DataPose.apply(m, data, t / 20.0, state.ageInTicks, w, legW);
			return;
		}
		float[] v;
		switch (anim) {
			case SkillAnimPayload.SLAY -> {
				v = sample(SLAY, t);
				if (t < 13.0F) {
					float shake = Mth.sin(e * 3.1F) * 0.06F * Mth.clamp((t - 4.0F) / 9.0F, 0.0F, 1.0F);
					v[RAX] += shake;
					v[LAX] -= shake;
				}
			}
			case SkillAnimPayload.FURY -> {
				v = sample(FURY, t);
				if (t > 5.0F) {
					float shake = Mth.sin(e * 6.0F) * 0.08F * (1.0F - (t - 5.0F) / 7.0F);
					v[RAZ] += shake;
					v[LAZ] -= shake;
				}
			}
			case SkillAnimPayload.CHAIN -> {
				v = sample(CHAIN, t);
				if (t < ChainSpin.WINDUP_TICKS) {
					// 서버 갈고리와 같은 각도로 왼팔 끝이 원을 그림 (패킷 도착 = 서버 준비 0틱 → +1)
					float ramp = Mth.clamp((t - 1.0F) / 3.0F, 0.0F, 1.0F);
					double ph = Math.toRadians(ChainSpin.angleDeg(e + (float) kr.overbreak.core.tick.Ticks.step()));
					v[LAX] += 0.45F * (float) Math.sin(ph) * ramp;
					v[LAZ] += 0.30F * (float) Math.cos(ph) * ramp;
				} else if (t >= 26.0F) {
					float s = Math.max(0.0F, Mth.sin((t - 26.0F) * Mth.PI / 5.0F));
					float pull = 0.35F * s * s;
					v[LAX] += pull;
					v[BX] -= pull * 0.3F;
					v[BY] -= pull * 0.5F;
				}
			}
			case SkillAnimPayload.ULT -> {
				v = sample(ULT, t);
				if (t > 9.0F) {
					float shake = Mth.sin(e * 7.0F) * 0.07F * (1.0F - (t - 9.0F) / 13.0F);
					v[RAZ] += shake;
					v[LAZ] -= shake;
					v[BX] += shake * 0.3F;
				}
			}
			case SkillAnimPayload.HK_SMASH -> {
				v = sample(HK_SMASH, t);
				if (t > 4.0F && t < 10.0F) {
					v[RAX] += Mth.sin(e * 3.3F) * 0.05F;
				}
			}
			case SkillAnimPayload.HK_SLAM -> v = sample(HK_SLAM, t);
			case SkillAnimPayload.HK_CHARGE -> v = sample(HK_CHARGE, t);
			case SkillAnimPayload.HK_ULT -> {
				v = sample(HK_ULT, t);
				if (t > 6.0F && t < 16.0F) {
					float shake = Mth.sin(e * 3.7F) * 0.09F * ((t - 6.0F) / 10.0F);
					v[RAX] += shake;
					v[BY] += shake * 0.5F;
				}
			}
			case SkillAnimPayload.IF_SHOT -> {
				// 왼팔을 시선(머리) 방향으로 곧게 겨누고 쏜 순간 위로 튐. 나머지 부위는 지금 자세 그대로
				v = current(m);
				float kick = t < 1.0F ? t : Math.max(0.0F, 1.0F - (t - 1.0F) / 4.0F);
				v[LAX] = -Mth.HALF_PI + m.head.xRot - 0.45F * kick;
				v[LAY] = 0.1F + m.head.yRot;
				v[LAZ] = 0.0F;
			}
			case SkillAnimPayload.IF_CHARGE -> {
				v = sample(IF_CHARGE, t);
				if (t > 4.0F) {
					float shake = Mth.sin(e * 4.2F) * 0.05F * Mth.clamp((t - 4.0F) / 20.0F, 0.0F, 1.0F);
					v[RAX] += shake;
					v[BY] += shake * 0.3F;
				}
			}
			case SkillAnimPayload.IF_PUNCH -> v = sample(IF_PUNCH, t);
			case SkillAnimPayload.IF_BLOCK -> {
				v = sample(IF_BLOCK, t);
				v[BX] += Mth.sin(e * 0.3F) * 0.02F;
			}
			case SkillAnimPayload.IF_SLAM_AIR -> v = sample(IF_SLAM_AIR, t);
			case SkillAnimPayload.IF_SLAM_HIT -> v = sample(IF_SLAM_HIT, t);
			case SkillAnimPayload.IF_ULT_RISE -> v = sample(IF_ULT_RISE, t);
			case SkillAnimPayload.IF_ULT_DROP -> v = sample(IF_ULT_DROP, t);
			case SkillAnimPayload.BASIC -> v = sample(BASIC, t);
			case SkillAnimPayload.BASIC_BACK -> v = sample(BASIC_BACK, t);
			default -> {
				return;
			}
		}
		apply(m, v, w, legW, spin);
	}

	private static float[] sample(Key[] keys, float t) {
		if (t <= keys[0].t) {
			return keys[0].v.clone();
		}
		for (int i = 0; i < keys.length - 1; i++) {
			Key a = keys[i];
			Key b = keys[i + 1];
			if (t < b.t) {
				float x = (t - a.t) / (b.t - a.t);
				float s = x * x * (3.0F - 2.0F * x);
				float[] out = new float[a.v.length];
				for (int c = 0; c < out.length; c++) {
					out[c] = Mth.lerp(s, a.v[c], b.v[c]);
				}
				return out;
			}
		}
		return keys[keys.length - 1].v.clone();
	}

	/**
	 * @param w    상체 가중치
	 * @param legW 다리 가중치 (걷는 중이면 0)
	 * @param spin 상체만 도는 회전 (라디안)
	 */
	private static void apply(HumanoidModel<?> m, float[] v, float w, float legW, float spin) {
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, v[RAX]);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, v[RAY]);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, v[RAZ]);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, v[LAX]);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, v[LAY]);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, v[LAZ]);

		float leanBefore = m.body.xRot;
		m.body.xRot = Mth.lerp(w, m.body.xRot, v[BX]);
		// 몸통을 숙이면 엉덩이가 뒤로 빠지므로 다리를 따라 옮깁니다 (바닐라 웅크리기와 같은 방식)
		float legShift = 8.5F * (Mth.sin(m.body.xRot) - Mth.sin(leanBefore));
		m.rightLeg.z += legShift;
		m.leftLeg.z += legShift;

		// 몸통 비틀기 + 상체 회전. 어깨도 같이 돌아갑니다 (바닐라 공격 모션과 같은 방식)
		float by = Mth.lerp(w, m.body.yRot, v[BY]) + spin;
		m.body.yRot = by;
		m.rightArm.z = Mth.sin(by) * 5.0F;
		m.rightArm.x = -Mth.cos(by) * 5.0F;
		m.leftArm.z = -Mth.sin(by) * 5.0F;
		m.leftArm.x = Mth.cos(by) * 5.0F;
		m.rightArm.yRot += by;
		m.leftArm.yRot += by;
		if (spin != 0.0F) {
			m.head.yRot += spin;
		}

		if (!Float.isNaN(v[HX])) {
			m.head.xRot = Mth.lerp(w, m.head.xRot, v[HX]);
		}
		m.rightLeg.xRot = Mth.lerp(legW, m.rightLeg.xRot, v[RLX]);
		m.rightLeg.zRot = Mth.lerp(legW, m.rightLeg.zRot, v[RLZ]);
		m.leftLeg.xRot = Mth.lerp(legW, m.leftLeg.xRot, v[LLX]);
		m.leftLeg.zRot = Mth.lerp(legW, m.leftLeg.zRot, v[LLZ]);
	}
}
