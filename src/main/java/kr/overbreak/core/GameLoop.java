package kr.overbreak.core;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.combat.AttackSpeed;
import kr.overbreak.input.InputModeSync;
import kr.overbreak.input.InputRouter;
import kr.overbreak.skill.Effects;
import kr.overbreak.skill.HudSync;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 한 틱의 순서를 한 곳에서 고정합니다 (데이터팩 tick.mcfunction 대응).
 *
 * 데이터팩에서 순서가 버그를 막던 곳은 그대로 지킵니다:
 *   - 입력 → CC → 쿨타임 → 진행 중 스킬 → 게이지 → HUD
 *   - 진행 중 스킬은 직업 tick 안에서 "회복 잔량 처리가 채널링보다 먼저"
 */
public final class GameLoop {
	private GameLoop() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GameLoop::tick);
	}

	private static void tick(MinecraftServer server) {
		// 0) 되감기 판정용 위치 기록 — 서버 틱마다 (60틱이면 17ms 간격)
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			kr.overbreak.combat.HitboxRewind.record(p);
		}

		// 1) 입력 — 웅크리기 엣지 (F·Q·좌·우클릭은 패킷이 들어온 순간 이미 처리됨). 서버 틱마다 봐서 늦지 않게
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			InputRouter.tickSneak(p, Attachments.profile(p));
		}

		// 시간 단위(1/20초) 박자 — 안내 문구 양보 시간 · 입력 창 · 바닐라 무적 시간에만 씀. 스킬은 서버 틱마다 (Ticks 로 변환된 수치)
		boolean timeUnit = kr.overbreak.core.tick.GameClock.advance();
		kr.overbreak.core.tick.GameClock.holdInvulnerability(timeUnit);

		// 2) 군중 제어 · 돌진
		CrowdControl.tick();

		// 3) 공격속도 잠금 · 직업 틱(쿨타임 · 진행 중 스킬 · 패시브)
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerProfile prof = Attachments.profile(p);
			AttackSpeed.tick(prof);
			if (timeUnit && prof.msgT > 0) {
				prof.msgT--;
			}
			PvpClass c = prof.pvpClass;
			if (c != null) {
				// 달리기 없음 (기본 속도가 달리기 속도) — 모드 없는 클라이언트가 달리기를 보내도 여기서 끔
				if (p.isSprinting()) {
					p.setSprinting(false);
				}
				c.tick(p);
			}
			InputModeSync.tick(p, prof);
		}

		// 4) 시전자와 무관하게 스스로 도는 것 (투사체 · 장판 · 파면)
		Effects.tick();

		// 5) 궁극기 게이지 · 아이템 · HUD
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerProfile prof = Attachments.profile(p);
			UltGauge.tick(p, prof);
			HudSync.tick(p, prof);
		}
	}
}
