package kr.overbreak.input;

import kr.overbreak.classes.PvpClass;
import kr.overbreak.combat.MeleeCleave;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.item.SkillItems;
import kr.overbreak.util.Fx;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * 조작 → 스킬 슬롯. 데이터팩 skill/use · sneak · swap · drop · input_left 다섯 파일 대응.
 *
 * 차단 규칙은 여기 한 곳에만 둡니다 (기절 · 에어본 · 밀쳐내기 · 정신집중).
 * 튜토리얼 게이팅도 이 한 곳에 붙습니다.
 *
 * 데이터팩과 달라진 점:
 *   F — 비워 둔 왼손을 감시하지 않고 손 바꾸기 패킷을 가로채 취소합니다. 아이템이 움직이지 않습니다.
 *   Q — 떨어진 아이템을 지우고 복구하지 않고 버리기 패킷을 취소합니다. 아이템이 애초에 안 떨어집니다.
 */
public final class InputRouter {
	/** 블록을 캐는 동안의 연속 스윙을 걸러 내는 시간 (틱, 데이터팩 350ms). */
	private static final int BLOCK_SWING_FILTER = 7;

	/** 우클릭 홀드로 보는 창 (틱, 데이터팩 300ms). 클라이언트는 누르고 있으면 4틱마다 사용을 반복합니다. */
	public static final int HOLD_WINDOW = 6;

	/** 입력 게이트 — 튜토리얼 등이 슬롯별로 막을 때 씁니다. null 이면 전부 통과. */
	public interface Gate {
		boolean allow(ServerPlayer p, Slot slot);
	}

	public enum Slot { BASIC, PRIMARY, SECONDARY, TERTIARY, ULT }

	public static Gate gate = (p, s) -> true;

	private InputRouter() {}

	public static void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer p && hand == InteractionHand.MAIN_HAND) {
				onUse(p);
			}
			return InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
			if (player instanceof ServerPlayer p) {
				Attachments.profile(p).lastBlockHitTick = kr.overbreak.core.tick.GameClock.now();
			}
			return InteractionResult.PASS;
		});
	}

	// ── 입력 이벤트 ──────────────────────────────────────────

	/** 재장전 키 (모드 클라이언트의 {@link kr.overbreak.net.ReloadPayload}). 기절 등은 직업 쪽에서 거릅니다. */
	public static void onReload(ServerPlayer p) {
		PvpClass c = Attachments.profile(p).pvpClass;
		if (c != null) {
			p.resetLastActionTime();
			c.reload(p);
		}
	}

	public static void onUse(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.lastUseTick = kr.overbreak.core.tick.GameClock.now();
		if (prof.pvpClass != null && !prof.pvpClass.intercept(p, Slot.PRIMARY) && pass(p, Slot.PRIMARY)) {
			prof.pvpClass.primary(p);
		}
	}

	/**
	 * 좌클릭 (모드 클라이언트의 {@link kr.overbreak.net.LeftClickPayload}).
	 * 근접 직업은 앞 범위 기본 공격, 원거리 직업은 basic(). 막히면(공격속도 · 정신집중 · 기절) 아무 일도 없습니다.
	 */
	public static void onLeftClick(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		PvpClass c = prof.pvpClass;
		if (c == null) {
			return;
		}
		p.resetLastActionTime();
		prof.lastSwingTick = kr.overbreak.core.tick.GameClock.now();
		if (c.intercept(p, Slot.BASIC) || !pass(p, Slot.BASIC)) {
			return;
		}
		if (c.meleeAllowed()) {
			MeleeCleave.swing(p, c);
		} else {
			c.basic(p);
		}
	}

	/**
	 * 스윙 패킷 (팔 휘두르기).
	 * 모드 클라이언트는 좌클릭을 따로 보내고 스윙 자체를 하지 않으므로, 여기서는 모드 없는 클라이언트만 공격으로 칩니다.
	 *
	 * @return true 면 바닐라 팔 휘두르기 방송을 취소합니다 (직업이 있는 플레이어의 스윙 모션은 남에게 보이지 않음).
	 */
	public static boolean onSwing(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		PvpClass c = prof.pvpClass;
		long now = kr.overbreak.core.tick.GameClock.now();
		if (c == null) {
			return false;
		}
		if (kr.overbreak.net.InputModePayload.canSend(p)) {
			p.resetLastActionTime();
			return true;
		}
		if (now - prof.lastDropTick <= 1) {
			return c.meleeAllowed();
		}
		if (c.meleeAllowed()) {
			// 근접: 좌클릭 = 앞 범위 평타. 블록을 조준해도 휘두릅니다 (어깨 너머 시점에서는 땅을 보고 치는 일이 잦음).
			// 누르고 있으면 공격속도만큼 반복됩니다.
			prof.lastSwingTick = now;
			if (pass(p, Slot.BASIC)) {
				MeleeCleave.swing(p, c);
			}
			p.resetLastActionTime();
			return true;
		}
		if (now - prof.lastBlockHitTick < BLOCK_SWING_FILTER) {
			return false;
		}
		prof.lastSwingTick = now;
		if (pass(p, Slot.BASIC)) {
			c.basic(p);
		}
		return false;
	}

	/** @return true 면 바닐라 손 바꾸기를 취소합니다 */
	public static boolean onSwapHands(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (prof.pvpClass == null) {
			return false;
		}
		if (!prof.pvpClass.intercept(p, Slot.TERTIARY) && pass(p, Slot.TERTIARY)) {
			prof.pvpClass.tertiary(p);
		}
		resync(p);
		return true;
	}

	/** @return true 면 바닐라 버리기를 취소합니다 */
	public static boolean onDrop(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.lastDropTick = kr.overbreak.core.tick.GameClock.now();
		if (prof.pvpClass == null || !SkillItems.isClassItem(p.getInventory().getSelectedItem())) {
			return false;
		}
		if (!prof.pvpClass.intercept(p, Slot.ULT) && prof.ultHas && pass(p, Slot.ULT)) {
			prof.pvpClass.ult(p);
		}
		resync(p);
		return true;
	}

	/** 웅크리기는 누르는 순간만 (엣지). 매 틱 GameLoop 가 부릅니다. */
	public static void tickSneak(ServerPlayer p, PlayerProfile prof) {
		boolean now = p.isShiftKeyDown();
		boolean pressed = now && !prof.prevSneak;
		prof.prevSneak = now;
		if (pressed && prof.pvpClass != null && !prof.pvpClass.intercept(p, Slot.SECONDARY) && pass(p, Slot.SECONDARY)) {
			prof.pvpClass.secondary(p);
		}
	}

	/**
	 * 우클릭을 누르고 있는가.
	 * 모드 클라이언트는 누름 · 뗌을 그대로 알려 주므로({@link kr.overbreak.net.RightHoldPayload}) 떼는 순간이 정확합니다.
	 * 모드 없는 클라이언트는 4틱마다 반복되는 사용 패킷을 창(HOLD_WINDOW)으로 봅니다.
	 */
	public static boolean holdingRight(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (prof.rightKnown) {
			return prof.rightDown;
		}
		return kr.overbreak.core.tick.GameClock.now() - prof.lastUseTick <= HOLD_WINDOW;
	}

	/** 모드 클라이언트의 우클릭 누름 · 뗌. */
	public static void onRightHold(ServerPlayer p, boolean down) {
		PlayerProfile prof = Attachments.profile(p);
		prof.rightKnown = true;
		prof.rightDown = down;
	}

	// ── 차단 ────────────────────────────────────────────────

	/** 기절 · 에어본 · 밀쳐내기 · 정신집중 · 게이트. 막히면 거부음. */
	private static boolean pass(ServerPlayer p, Slot slot) {
		Combatant c = Attachments.combatant(p);
		if (c.hardCc() || c.casting || !gate.allow(p, slot)) {
			if (slot != Slot.BASIC) {
				Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.7F, 0.5F);
			}
			return false;
		}
		return true;
	}

	/** 클라이언트가 먼저 움직여 둔 인벤토리(손 바꾸기·버리기 예측)를 서버 상태로 되돌립니다. */
	private static void resync(ServerPlayer p) {
		p.containerMenu.sendAllDataToRemote();
	}
}
