package kr.overbreak.client.fx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.net.SkillAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * 그림자 가르기 · 그림자 걸음 잔상 — 사이버펑크 산데비스탄처럼 지나간 자리에 내 캐릭터와 같은 모습의 어두운 파란 잔상이 남습니다.
 *
 *   돌진하는 동안 반 틱마다 그 순간의 모습(자세 · 팔다리 · 스킬 동작)을 그대로 복사해 두고
 *   그 자리에 스킨을 파랗게 물들인 반투명 모델로 다시 그림. 0.6초에 걸쳐 짙은 파랑으로 식으며 사라짐
 *   원래 캐릭터를 그릴 때 함께 그리므로, 1인칭에서 내 잔상은 보이지 않습니다 (앞으로 돌진해 잔상은 어차피 등 뒤)
 */
public final class Afterimages {
	/** 잔상이 사라지기까지 (틱). */
	private static final float LIFE = 12.0F;
	private static final int MAX = 48;
	/** 막 남은 잔상 → 사라지기 직전 색. */
	private static final int FRESH = 0x3A64E0;
	private static final int OLD = 0x0C1A52;
	private static final float ALPHA = 0.62F;

	private record Ghost(AvatarRenderState state, float born) {}

	private static final Map<Integer, Deque<Ghost>> GHOSTS = new HashMap<>();
	private static float ticks;

	private Afterimages() {}

	/** 클라이언트 틱 (SkillAnims.tick 뒤) — 돌진 중인 플레이어의 모습을 남기고, 다 사라진 잔상을 지웁니다. */
	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			GHOSTS.clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		ticks = (float) kr.overbreak.client.ClientClock.now();
		for (AbstractClientPlayer p : mc.level.players()) {
			if (!(SkillAnims.playing(p.getId(), SkillAnimPayload.SD_REND) || SkillAnims.playing(p.getId(), SkillAnimPayload.SD_STEP)) || p.isInvisible()) {
				continue;
			}
			EntityRenderer<? super AbstractClientPlayer, ?> r = mc.getEntityRenderDispatcher().getRenderer(p);
			if (!(r instanceof AvatarRenderer<?>)) {
				continue;
			}
			Deque<Ghost> list = GHOSTS.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
			// 반 틱마다 한 장 (60틱이면 틱이 이미 촘촘해 틱마다 한 장)
			for (float partial : kr.overbreak.core.tick.Ticks.k() > 1.5 ? new float[] {1.0F} : new float[] {0.5F, 1.0F}) {
				if (r.createRenderState(p, partial) instanceof AvatarRenderState state) {
					list.addLast(new Ghost(state, (float) (ticks - kr.overbreak.core.tick.Ticks.step() + kr.overbreak.client.ClientClock.partial(partial))));
				}
			}
			while (list.size() > MAX) {
				list.removeFirst();
			}
		}
		GHOSTS.values().forEach(list -> list.removeIf(g -> ticks - g.born >= LIFE));
		GHOSTS.values().removeIf(Deque::isEmpty);
	}

	/**
	 * 플레이어 모델을 그린 직후 (LivingEntityRendererMixin) — poseStack 은 지금 캐릭터 발밑.
	 * LivingEntityRenderer.submit 과 같은 변환(몸 방향 · 뒤집기 · 0.9375배)으로 잔상 자리에 모델을 다시 넣습니다.
	 */
	public static void submit(AvatarRenderer<?> renderer, AvatarRenderState current, PoseStack poseStack, SubmitNodeCollector collector) {
		Deque<Ghost> list = GHOSTS.get(current.id);
		if (list == null) {
			return;
		}
		float now = (float) (ticks + kr.overbreak.client.ClientClock.partial(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
		for (Ghost g : list) {
			float age = Mth.clamp((now - g.born) / LIFE, 0.0F, 1.0F);
			if (age >= 1.0F) {
				continue;
			}
			AvatarRenderState s = g.state;
			// 막 생긴 잔상은 캐릭터와 겹쳐 보이지 않게 옅게 시작
			float fadeIn = Mth.clamp((now - g.born) / 1.5F, 0.0F, 1.0F);
			float alpha = ALPHA * (1.0F - age) * (1.0F - age) * fadeIn;
			if (alpha <= 0.01F) {
				continue;
			}
			int rgb = lerpRgb(age, FRESH, OLD);
			int color = ((int) (alpha * 255.0F) << 24) | rgb;
			poseStack.pushPose();
			poseStack.translate(s.x - current.x, s.y - current.y, s.z - current.z);
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - s.bodyRot));
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.scale(0.9375F, 0.9375F, 0.9375F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			collector.submitModel(renderer.getModel(), s, poseStack, RenderTypes.entityTranslucent(renderer.getTextureLocation(s)),
					0xF000F0, OverlayTexture.NO_OVERLAY, color, null, 0, null);
			poseStack.popPose();
		}
	}

	private static int lerpRgb(float t, int a, int b) {
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}

	/** 시험용: 이 플레이어에게 남아 있는 잔상 수. */
	public static int count(int entityId) {
		Deque<Ghost> list = GHOSTS.get(entityId);
		return list == null ? 0 : list.size();
	}
}
