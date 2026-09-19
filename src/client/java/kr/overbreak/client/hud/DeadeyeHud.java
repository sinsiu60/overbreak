package kr.overbreak.client.hud;

import java.util.List;

import kr.overbreak.Overbreak;
import kr.overbreak.net.DeadeyePayload;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 황야의 무법자 화면 (오버워치 캐서디 궁극기처럼).
 *
 *   가장자리가 어둡게 조여 들고 화면 전체에 옅은 황혼빛 — 조준 시작 0.25초에 걸쳐 들어옴
 *   조준한 적 머리 위: 해골 표식 + 둘레 12칸. 시간이 지날수록 둘레가 차오르고(피해가 쌓이는 만큼),
 *     발사 피해가 남은 체력 이상이 되면 해골이 빨갛게 고동침 (처치 가능)
 *   시야는 살짝 좁아짐 (AbstractClientPlayerFovMixin)
 *   서버가 매 틱 {@link DeadeyePayload} 로 조준 상태를 보냅니다.
 */
public final class DeadeyeHud {
	private static final Identifier SKULL = Overbreak.id("hud/deadeye_skull");
	private static final int BANDS = 18;
	private static final int SEGMENTS = 12;

	private static int elapsed;
	private static int total;
	private static int damage;
	private static List<Integer> targets = List.of();
	private static float ticks;
	private static float startedAt;

	private DeadeyeHud() {}

	public static void receive(DeadeyePayload msg) {
		if (msg.total() > 0 && total <= 0) {
			startedAt = ticks;
		}
		elapsed = msg.elapsed();
		total = msg.total();
		damage = msg.damage();
		targets = msg.targets();
	}

	public static boolean active() {
		return total > 0;
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			total = 0;
			targets = List.of();
			return;
		}
		ticks = (float) kr.overbreak.client.ClientClock.now();
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (total <= 0 || mc.player == null || mc.level == null) {
			return;
		}
		float partial = dt.getGameTimeDeltaPartialTick(false);
		float in = Mth.clamp((ticks - startedAt + kr.overbreak.client.ClientClock.partial(partial)) / 5.0F, 0.0F, 1.0F);
		float progress = Mth.clamp((elapsed + kr.overbreak.client.ClientClock.partial(partial)) / total, 0.0F, 1.0F);
		int w = g.guiWidth();
		int h = g.guiHeight();

		// 가장자리 어둠 — 바깥 띠일수록 진함
		g.fill(0, 0, w, h, alpha(0x16FF9A3C, in));
		int depth = Math.min(w, h) / 3;
		for (int i = 0; i < BANDS; i++) {
			float f = 1.0F - i / (float) BANDS;
			int color = ((Math.round(170.0F * f * f * in) & 255) << 24) | 0x140A04;
			int a = i * depth / BANDS;
			int b = (i + 1) * depth / BANDS;
			g.fill(a, a, w - a, b, color);
			g.fill(a, h - b, w - a, h - a, color);
			g.fill(a, b, b, h - b, color);
			g.fill(w - b, b, w - a, h - b, color);
		}

		Camera camera = mc.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Matrix4f viewProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
		Vec3 camPos = camera.position();
		for (int id : targets) {
			Entity entity = mc.level.getEntity(id);
			if (!(entity instanceof LivingEntity e) || !e.isAlive()) {
				continue;
			}
			Vec3 top = new Vec3(Mth.lerp(partial, e.xo, e.getX()),
					Mth.lerp(partial, e.yo, e.getY()) + e.getBbHeight() + 0.7,
					Mth.lerp(partial, e.zo, e.getZ()));
			Vector4f clip = new Vector4f((float) (top.x - camPos.x), (float) (top.y - camPos.y), (float) (top.z - camPos.z), 1.0F)
					.mul(viewProjection);
			if (clip.w <= 0.05F) {
				continue;
			}
			float sx = (clip.x / clip.w + 1.0F) * 0.5F * w;
			float sy = (1.0F - clip.y / clip.w) * 0.5F * h;
			float hp = Math.max(1.0F, e.getHealth() + e.getAbsorptionAmount());
			float fill = Mth.clamp(progress * damage / hp, 0.0F, 1.0F);
			marker(g, sx, sy, fill, partial);
		}
	}

	private static void marker(GuiGraphicsExtractor g, float sx, float sy, float fill, float partial) {
		boolean lethal = fill >= 1.0F;
		float pulse = lethal ? 1.0F + 0.18F * Math.abs(Mth.sin((ticks + kr.overbreak.client.ClientClock.partial(partial)) * 0.5F)) : 1.0F;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(sx, sy);
		pose.scale(pulse, pulse);
		int filled = Math.round(fill * SEGMENTS);
		for (int i = 0; i < SEGMENTS; i++) {
			double a = Math.toRadians(-90.0 + i * 360.0 / SEGMENTS);
			int x = (int) Math.round(Math.cos(a) * 11.0);
			int y = (int) Math.round(Math.sin(a) * 11.0);
			int color = i < filled ? 0xFFFF3B2E : 0x90FFFFFF;
			g.fill(x - 1, y - 1, x + 2, y + 2, 0xA0000000);
			g.fill(x - 1, y - 1, x + 1, y + 1, color);
		}
		g.blitSprite(RenderPipelines.GUI_TEXTURED, SKULL, -7, -7, 14, 14, lethal ? 0xFFFF3B2E : 0xF0FFF2DC);
		pose.popMatrix();
	}

	private static int alpha(int argb, float k) {
		int a = Math.round(((argb >>> 24) & 255) * Mth.clamp(k, 0.0F, 1.0F));
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
