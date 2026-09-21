package kr.overbreak.client;

import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.client.anim.data.PlayerAnimations;
import kr.overbreak.client.camera.AimTracker;
import kr.overbreak.client.camera.Recoil;
import kr.overbreak.client.fx.BulletTrails;
import kr.overbreak.client.hud.HitMarker;
import kr.overbreak.client.hud.HudState;
import kr.overbreak.client.hud.SkillHud;
import kr.overbreak.client.hud.SkillInfoScreen;
import kr.overbreak.Overbreak;
import kr.overbreak.client.input.InputMode;
import kr.overbreak.client.item.ChargingProperty;
import kr.overbreak.net.HitPayload;
import kr.overbreak.net.HudPayload;
import kr.overbreak.net.InputModePayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.net.TracerPayload;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;

public final class OverbreakClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 전장에서는 웅크리기 키가 자세를 낮추지 않습니다 (웅크리기 = 액티븃2 스킬 키)
		kr.overbreak.core.Crouch.clientCheck = p -> p == net.minecraft.client.Minecraft.getInstance().player && InputMode.active();
		// 연출 시계 (1/20초 단위) — 다른 모든 클라이언트 틱 처리보다 먼저
		ClientTickEvents.END_CLIENT_TICK.register(kr.overbreak.client.ClientClock::tick);
		ClientPlayNetworking.registerGlobalReceiver(SkillAnimPayload.TYPE, (payload, context) -> {
			SkillAnims.receive(payload);
			Recoil.onAnim(payload);
			kr.overbreak.client.camera.MeleePunch.onAnim(payload);
		});
		ClientTickEvents.END_CLIENT_TICK.register(Recoil::tick);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> kr.overbreak.client.camera.MeleePunch.tick());
		// 총알 궤적: 서버가 알려 준 줄을 화면을 향한 2D 빛줄기로 짧게 그림
		ClientPlayNetworking.registerGlobalReceiver(TracerPayload.TYPE, (payload, context) -> BulletTrails.receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(BulletTrails::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> BulletTrails.submit(context.poseStack(), context.submitNodeCollector()));
		// 스킬 키 서브틱: 프레임마다 키 눌림을 보고 바뀐 순간의 틱 안 위치를 서버로
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> kr.overbreak.client.input.InputTimingTracker.frame(net.minecraft.client.Minecraft.getInstance()));
		// 틱레이트: 서버가 바닐라 패킷으로 알려 준 값을 공용 TickRateConfig 에 반영 (틱 → 초 변환이 클라이언트에서도 맞게)
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (mc.level != null) {
				kr.overbreak.core.tick.ClientTickRate.sync(Math.round(mc.level.tickRateManager().tickrate()));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(SkillAnims::tick);
		ClientTickEvents.END_CLIENT_TICK.register(kr.overbreak.client.fx.Afterimages::tick);
		ClientTickEvents.END_CLIENT_TICK.register(kr.overbreak.client.fx.ScatterSounds::tick);
		ClientTickEvents.END_CLIENT_TICK.register(kr.overbreak.client.anim.scatter.ScatterShots::tick);
		ClientTickEvents.END_CLIENT_TICK.register(kr.overbreak.client.anim.scatter.ScatterView::tick);
		ClientTickEvents.END_CLIENT_TICK.register(kr.overbreak.client.anim.scatter.ScatterClone::tick);
		kr.overbreak.client.anim.scatter.ScatterData.init();
		ClientTickEvents.END_CLIENT_TICK.register(kr.overbreak.client.audio.MatchMusicPlayer::tick);
		// Blockbench 애니메이션: 모드 기본값 + 게임 폴더 overbreak/animations (저장하면 1초 안에 다시 읽음)
		PlayerAnimations.reload(false);
		ClientTickEvents.END_CLIENT_TICK.register(PlayerAnimations::tick);
		ClientPlayNetworking.registerGlobalReceiver(InputModePayload.TYPE, (payload, context) -> InputMode.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(kr.overbreak.net.ScatterPayload.TYPE,
				(payload, context) -> kr.overbreak.client.anim.scatter.ScatterView.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(kr.overbreak.net.MusicPayload.TYPE,
				(payload, context) -> kr.overbreak.client.audio.MatchMusicPlayer.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(kr.overbreak.net.ViewPayload.TYPE,
				(payload, context) -> kr.overbreak.client.camera.ViewLock.receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(InputMode::tick);
		ClientPlayNetworking.registerGlobalReceiver(HudPayload.TYPE, (payload, context) -> HudState.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(HitPayload.TYPE, (payload, context) -> HitMarker.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(kr.overbreak.net.HealPayload.TYPE,
				(payload, context) -> kr.overbreak.client.hud.HealScreen.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(kr.overbreak.net.HurtPayload.TYPE,
				(payload, context) -> kr.overbreak.client.hud.DamageFeedback.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(kr.overbreak.net.ScorePayload.TYPE,
				(payload, context) -> kr.overbreak.client.hud.ScoreHud.receive(payload));
		// 팀전 테두리 (우리 편 파랑 · 상대 빨강) — 보는 사람마다 다르게 칠합니다
		ClientPlayNetworking.registerGlobalReceiver(kr.overbreak.net.TeamPayload.TYPE,
				(payload, context) -> kr.overbreak.client.hud.MatchTeams.receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(kr.overbreak.client.hud.MatchTeams::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> kr.overbreak.client.hud.MatchTeams.clear());
		ClientPlayNetworking.registerGlobalReceiver(kr.overbreak.net.DeadeyePayload.TYPE, (payload, context) -> kr.overbreak.client.hud.DeadeyeHud.receive(payload));
		ClientTickEvents.END_CLIENT_TICK.register(HudState::tick);
		ClientTickEvents.END_CLIENT_TICK.register(HitMarker::tick);
		SkillHud.register();
		SkillInfoScreen.register();
		// 메인 화면 (서버가 MenuPayload 로 상태를 보냄)
		kr.overbreak.client.lobby.LobbyClient.register();
		// 튜토리얼 연출 (대사창 · 부팅 화면 · HUD 화살표)
		kr.overbreak.client.tutorial.TutorialClient.register();
		kr.overbreak.client.input.Keys.register();
		// 입력 처리보다 먼저 조준점을 보내서, 같은 틱의 스킬 · 평타가 새 조준을 쓰게 합니다
		ClientTickEvents.START_CLIENT_TICK.register(AimTracker::tick);
		ClientTickEvents.START_CLIENT_TICK.register(InputMode::tickRightHold);
		ClientTickEvents.START_CLIENT_TICK.register(InputMode::tickJumpHold);
		ClientTickEvents.START_CLIENT_TICK.register(kr.overbreak.client.camera.ViewLock::tick);
		// 아이템 모델 조건: 로켓 펀치 충전 중이면 건틀릿이 파랗게 빛나는 모델로 (assets/overbreak/items/gauntlet.json)
		ConditionalItemModelProperties.ID_MAPPER.put(Overbreak.id("charging"), ChargingProperty.MAP_CODEC);
		// 재장전 중 탄창이 빠진 구간이면 연사 포탑이 탄창 없는 모델로 (assets/overbreak/items/valkyrie_rifle.json)
		ConditionalItemModelProperties.ID_MAPPER.put(Overbreak.id("magazine_out"), kr.overbreak.client.item.MagazineOutProperty.MAP_CODEC);
		// 1인칭 재장전 중 탄창이 젖혀진 구간이면 리볼버가 탄창 없는 몸통으로 (assets/overbreak/items/revolver.json)
		ConditionalItemModelProperties.ID_MAPPER.put(Overbreak.id("cylinder_open"), kr.overbreak.client.item.CylinderOpenProperty.MAP_CODEC);
		// 기본 시점: 어깨 너머 3인칭 (F5 로 1인칭 · 정면 3인칭 전환 가능)
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				client.execute(() -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK)));
	}
}
