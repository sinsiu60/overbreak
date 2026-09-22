package kr.overbreak.client.hud;

import java.util.HashSet;
import java.util.Set;

import kr.overbreak.net.TeamPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 팀전 테두리 — 서버가 보낸 편 가르기를 들고 있다가, 그리기 직전에 개체마다 테두리 색을 정합니다.
 *
 *   우리 편  파란 테두리 (벽 너머로도 보임 — 어디 있는지 알아야 하니까)
 *   상대 편  빨간 테두리 (<b>시야에 들어와 있을 때만</b> — 벽 너머를 비추지 않습니다)
 *
 * 스코어보드 팀 색은 모두에게 같은 색으로 보이므로 (내가 홍팀이면 내 편이 빨강) 쓰지 않고,
 * 보는 사람마다 다르게 칠할 수 있도록 클라이언트에서 직접 색을 넣습니다.
 */
public final class MatchTeams {
	/** 우리 편 테두리 (파랑). */
	public static final int ALLY = 0xFF3FA2FF;
	/** 상대 편 테두리 (빨강). */
	public static final int FOE = 0xFFFF4B4B;
	/** 시야 판정을 다시 하는 간격 (클라이언트 틱). */
	private static final int LOOK_EVERY = 2;

	private static final Set<Integer> ALLIES = new HashSet<>();
	private static final Set<Integer> FOES = new HashSet<>();
	/** 지금 눈에 보이는 상대 (벽에 가리지 않은 쪽). */
	private static final Set<Integer> VISIBLE = new HashSet<>();
	private static int t;

	private MatchTeams() {}

	public static void receive(TeamPayload msg) {
		ALLIES.clear();
		FOES.clear();
		VISIBLE.clear();
		ALLIES.addAll(msg.allies());
		FOES.addAll(msg.foes());
	}

	public static void clear() {
		ALLIES.clear();
		FOES.clear();
		VISIBLE.clear();
	}

	/** 우리 편인가 (팀전이 아니면 늘 false — 나 말고는 모두 상대). */
	public static boolean ally(Entity e) {
		return ALLIES.contains(e.getId());
	}

	/** 경기 중인가 (테두리를 칠할 개체가 있는가). */
	public static boolean active() {
		return !ALLIES.isEmpty() || !FOES.isEmpty();
	}

	/** 이 개체의 테두리 색 (0 = 테두리 없음). */
	public static int outline(Entity e) {
		if (ALLIES.contains(e.getId())) {
			return ALLY;
		}
		if (FOES.contains(e.getId()) && VISIBLE.contains(e.getId())) {
			return FOE;
		}
		return 0;
	}

	/** 상대가 벽 뒤에 있는지 몇 틱마다 한 번 봅니다 (벽 너머로 빨간 테두리가 비치지 않게). */
	public static void tick(Minecraft mc) {
		if (FOES.isEmpty() || mc.level == null || mc.player == null) {
			VISIBLE.clear();
			return;
		}
		if (++t % LOOK_EVERY != 0) {
			return;
		}
		Vec3 eye = mc.player.getEyePosition();
		VISIBLE.clear();
		for (int id : FOES) {
			Entity e = mc.level.getEntity(id);
			if (e != null && sees(mc, eye, e)) {
				VISIBLE.add(id);
			}
		}
	}

	/** 눈에서 몸통 · 머리 · 발 중 한 곳이라도 막히지 않고 닿으면 보이는 것으로 봅니다. */
	private static boolean sees(Minecraft mc, Vec3 eye, Entity e) {
		double h = e.getBbHeight();
		for (double at : new double[] {h * 0.9, h * 0.5, h * 0.1}) {
			Vec3 to = new Vec3(e.getX(), e.getY() + at, e.getZ());
			HitResult hit = mc.level.clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}
}
