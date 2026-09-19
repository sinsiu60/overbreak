package kr.overbreak;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.classes.Classes;
import kr.overbreak.combat.AttackSpeed;
import kr.overbreak.command.ObCommand;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.GameLoop;
import kr.overbreak.input.InputRouter;
import kr.overbreak.net.AimPayload;
import kr.overbreak.net.HitPayload;
import kr.overbreak.net.HudPayload;
import kr.overbreak.net.InputModePayload;
import kr.overbreak.net.LeftClickPayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Overbreak implements ModInitializer {
	public static final String MOD_ID = "overbreak";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 순서가 중요한 것은 GameLoop 한 곳에서만 정합니다. 여기서는 등록만 합니다.
		Attachments.init();
		SkillAnimPayload.init();
		AimPayload.init();
		InputModePayload.init();
		LeftClickPayload.init();
		kr.overbreak.net.RightHoldPayload.init();
		kr.overbreak.net.ReloadPayload.init();
		kr.overbreak.net.SkillInputPayload.init();
		kr.overbreak.net.DeadeyePayload.init();
		kr.overbreak.net.TracerPayload.init();
		HudPayload.init();
		kr.overbreak.net.ScorePayload.init();
		kr.overbreak.net.TeamPayload.init();
		kr.overbreak.net.TeamDraftPayload.init();
		HitPayload.init();
		kr.overbreak.net.HurtPayload.init();
		Classes.init();
		InputRouter.init();
		AttackSpeed.init();
		UltGauge.init();
		CrowdControl.init();
		GameLoop.init();
		ObCommand.init();
		kr.overbreak.net.MenuPayload.init();
		kr.overbreak.net.DialoguePayload.init();
		kr.overbreak.net.TutorialCuePayload.init();
		kr.overbreak.net.TutorialAckPayload.init();
		kr.overbreak.net.MenuActionPayload.init();
		// 메인 화면 · 튜토리얼 · 1대1 매치
		kr.overbreak.game.Game.init();
		kr.overbreak.core.tick.TickRate.init();
		LOGGER.info("OVERBREAK 로드 - 직업 {}개", Classes.all().size());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
