package kr.overbreak.client.tutorial;

import kr.overbreak.Overbreak;
import kr.overbreak.net.DialoguePayload;
import kr.overbreak.net.TutorialAckPayload;
import kr.overbreak.net.TutorialCuePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * 튜토리얼 연출 묶음 (클라이언트) — 대사창 · 부팅 화면 · HUD 화살표를 서버 지시대로 켭니다.
 * 그리기 순서: 화살표 → 대사창 → 부팅 화면 (부팅 화면이 가장 위).
 */
public final class TutorialClient {
	private TutorialClient() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DialoguePayload.TYPE, (payload, context) -> CoreDialogue.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(TutorialCuePayload.TYPE, (payload, context) -> cue(payload));
		ClientTickEvents.END_CLIENT_TICK.register(CoreDialogue::tick);
		ClientTickEvents.END_CLIENT_TICK.register(BootSequence::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(TutorialClient::clear));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Overbreak.id("tutorial_pointer"), HudPointer::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Overbreak.id("tutorial_dialogue"), CoreDialogue::render);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Overbreak.id("tutorial_boot"), BootSequence::render);
	}

	private static void cue(TutorialCuePayload p) {
		switch (p.cue()) {
			// text 가 "match" 면 경기 투입판 (시점을 건드리지 않고 문구도 전장용)
			case TutorialCuePayload.BOOT -> BootSequence.start(p.arg(), !kr.overbreak.game.Opening.MATCH.equals(p.text()));
			case TutorialCuePayload.GLITCH -> BootSequence.glitch(p.arg());
			case TutorialCuePayload.POINT -> HudPointer.point(p.arg(), p.text());
			case TutorialCuePayload.CLEAR -> HudPointer.clear();
			// 튜토리얼을 벗어남 — 부팅 연출 · 대사창 · 화살표를 모두 걷습니다 (0.1a)
			case TutorialCuePayload.OFF -> clear();
			default -> {
			}
		}
	}

	private static void clear() {
		CoreDialogue.clear();
		HudPointer.clear();
		BootSequence.stop();
	}

	/** F8 규격 정보 화면을 열었다고 서버에 알림 (튜토리얼의 그 단계에서만 씁니다). */
	public static void infoScreenOpened() {
		if (ClientPlayNetworking.canSend(TutorialAckPayload.TYPE)) {
			ClientPlayNetworking.send(new TutorialAckPayload(TutorialAckPayload.INFO_SCREEN));
		}
	}
}
