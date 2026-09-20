package kr.overbreak.client.hud;

import java.util.Arrays;

import kr.overbreak.client.input.InputMode;
import kr.overbreak.net.HudPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * 스킬 HUD 상태 (서버가 보낸 값) + 연출용 순간 기록.
 *   사용 순간  — 쿨타임이 0 에서 돌기 시작한 틱 (칸이 하얗게 번쩍)
 *   거부 순간  — 쿨타임 중에 그 키를 누른 틱 (칸이 빨갛게 흔들림)
 *   궁극기 준비 · 사용 · 거부 순간
 */
public final class HudState {
	static String classId = "";
	static int[] remaining = new int[0];
	static int[] total = new int[0];
	static boolean[] active = new boolean[0];
	static int ultCharge;
	static int ultState;
	static int ammo = -1;
	static int ammoMax;
	static int meter = -1;
	static int meterKind;
	static int stacks;
	static int stacksMax;
	static int flags;
	/** 공격속도 증가가 켜진 순간 (화면 가장자리 연출이 번쩍이며 들어옵니다). */
	static float hasteAt = -1000;
	/** 스택이 다 차서 터진 순간 (칸이 주황빛으로 번쩍). */
	static float stackBurstAt = -1000;

	static float ticks;
	static float[] usedAt = new float[0];
	static float[] deniedAt = new float[0];
	static float ultReadyAt = -1000;
	static float ultUsedAt = -1000;
	static float ultDeniedAt = -1000;
	static float shotAt = -1000;

	private static final boolean[] PREV_KEYS = new boolean[4];

	private HudState() {}

	/** 공격속도가 올라간 상태인가 (화면 가장자리 연출). */
	public static boolean haste() {
		return (flags & kr.overbreak.skill.HudExtra.FLAG_HASTE) != 0;
	}

	/** 기절 중인가 — 그동안은 마우스로 화면을 돌릴 수 없습니다 ({@link kr.overbreak.client.mixin.StunLookMixin}). */
	public static boolean stunned() {
		return (flags & kr.overbreak.skill.HudExtra.FLAG_STUN) != 0;
	}

	public static void receive(HudPayload msg) {
		int n = msg.remaining().size();
		if (!msg.classId().equals(classId) || n != remaining.length) {
			classId = msg.classId();
			remaining = new int[n];
			total = new int[n];
			active = new boolean[n];
			usedAt = new float[n];
			deniedAt = new float[n];
			Arrays.fill(usedAt, -1000);
			Arrays.fill(deniedAt, -1000);
		}
		for (int i = 0; i < n; i++) {
			int now = msg.remaining().get(i);
			if (remaining[i] == 0 && now > 0) {
				usedAt[i] = ticks;
			}
			remaining[i] = now;
			total[i] = msg.total().get(i);
			active[i] = msg.active().get(i);
		}
		if (msg.ultState() == 2 && ultState != 2) {
			ultReadyAt = ticks;
		} else if (ultState == 2 && msg.ultState() != 2) {
			ultUsedAt = ticks;
		}
		ultCharge = msg.ultCharge();
		ultState = msg.ultState();
		if (msg.ammo() >= 0 && msg.ammo() < ammo) {
			shotAt = ticks;
		}
		ammo = msg.ammo();
		ammoMax = msg.ammoMax();
		meter = msg.meter();
		meterKind = msg.meterKind();
		if (msg.stacksMax() > 0 && stacks == msg.stacksMax() - 1 && msg.stacks() == 0) {
			stackBurstAt = ticks;
		}
		stacks = msg.stacks();
		stacksMax = msg.stacksMax();
		boolean haste = (msg.flags() & kr.overbreak.skill.HudExtra.FLAG_HASTE) != 0;
		if (haste && !haste()) {
			hasteAt = ticks;
		}
		flags = msg.flags();
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			classId = "";
			remaining = new int[0];
			ultState = 0;
			ammo = -1;
			meter = -1;
			stacksMax = 0;
			flags = 0;
			return;
		}
		ticks = (float) kr.overbreak.client.ClientClock.now();
		if (!InputMode.active()) {
			return;
		}
		// 칸 순서: 우클릭 · 웅크리기 · E, 그리고 Q = 궁극기 (0.2 이후 액티브3 키는 E)
		KeyMapping[] keys = {mc.options.keyUse, mc.options.keyShift, mc.options.keyInventory, mc.options.keyDrop};
		// 웅크리기 + 우클릭으로 쓰는 칸(건슬링어 곡예 난사)은 둘 다 눌렸을 때만 사용불가로 친다
		HudLayouts.Layout layout = HudLayouts.get(classId);
		boolean comboSlot1 = layout != null && layout.slots().size() > 1 && layout.slots().get(1).key().contains("RMB");
		for (int k = 0; k < keys.length; k++) {
			boolean down = keys[k].isDown();
			if (k == 1 && comboSlot1) {
				down = down && mc.options.keyUse.isDown();
			}
			if (down && !PREV_KEYS[k]) {
				if (k < 3 && k < remaining.length && remaining[k] > 0) {
					deniedAt[k] = ticks;
				} else if (k == 3 && ultState == 1) {
					ultDeniedAt = ticks;
				}
			}
			PREV_KEYS[k] = down;
		}
	}
}
