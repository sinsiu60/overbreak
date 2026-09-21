package kr.overbreak.test.client;

import java.util.function.Consumer;

import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.gunslinger.DualPistols;
import kr.overbreak.classes.gunslinger.Gunslinger;
import kr.overbreak.client.anim.GunslingerAnim;
import kr.overbreak.client.hud.HudLayouts;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;

/**
 * 궤적의 깃털 — 쌍권총 모델과 1인칭 동작을 구간별로 찍습니다. 이름: 시점_동작_경과틱.
 *
 * 공중 재장전은 0 · 8 · 16 · 18 · 22 틱을 찍습니다 (튕겨 올림 → 총 돌리기 → 받아 끼움 → 반동).
 */
public final class GunslingerClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		// HUD 배치가 등록돼 있어야 스킬 칸이 그려집니다
		ctx.runOnClient(mc -> {
			if (HudLayouts.get(Gunslinger.ID) == null) {
				throw new AssertionError("HudLayouts 에 gunslinger 가 없음");
			}
			// 3인칭 동작 파일 (뼈대 이름 · 채널 오류는 여기서 걸립니다)
			for (String name : new String[] {
					"gunslinger.shot", "gunslinger.shot_left", "gunslinger.reload", "gunslinger.boost",
					"gunslinger.anchor", "gunslinger.glide", "gunslinger.release_twirl", "gunslinger.release_snap"}) {
				if (kr.overbreak.client.anim.data.PlayerAnimations.get(name) == null) {
					throw new AssertionError("애니메이션 파일에 없음: " + name);
				}
			}
		});
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			onServer(sp, p -> Classes.give(p, gs()));
			ctx.getInput().lookAt(0.0F, 4.0F);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(Ticks.of(20));

			for (CameraType view : new CameraType[] {CameraType.THIRD_PERSON_BACK, CameraType.FIRST_PERSON}) {
				String tag = view == CameraType.FIRST_PERSON ? "fp" : "tp";
				ctx.runOnClient(mc -> mc.options.setCameraType(view));
				ctx.waitTicks(Ticks.of(6));
				// 가만히 든 모습 — 쌍권총 모델이 제대로 읽혔는지
				ctx.takeScreenshot("gs_" + tag + "_idle");

				onServer(sp, p -> gs().basic(p));
				shots(ctx, "gs_" + tag + "_shot", 1, 3);
				reset(sp);

				onServer(sp, p -> gs().primary(p));
				shots(ctx, "gs_" + tag + "_boost", 2, 6);
				reset(sp);

				// 돌진 난사는 웅크리기 단독 · 쓰는 동안 3인칭으로 잡힙니다
				onServer(sp, p -> gs().secondary(p));
				shots(ctx, "gs_" + tag + "_scatter", 1, 4, 8, 14, 22, 30);
				reset(sp);

				onServer(sp, p -> gs().tertiary(p));
				shots(ctx, "gs_" + tag + "_anchor", 3, 8);
				reset(sp);

				// 공중 재장전 — 탄창 두 개가 떠 있는 구간과 맞물리는 순간
				onServer(sp, p -> {
					Gunslinger.state(p).ammo = 3;
					gs().reload(p);
				});
				shots(ctx, "gs_" + tag + "_reload", 2, 8, 16, 18, 22);
				ctx.waitTicks(Ticks.of(8));
				reset(sp);

				// 궤적 해방 — 이리저리 쏘아 궤적을 남기고 (적 팀에게도 보이는 선), 예고 → 폭발
				onServer(sp, p -> {
					UltGauge.fill(p);
					gs().ult(p);
				});
				for (int i = 0; i < 10; i++) {
					float yaw = -40.0F + i * 9.0F;
					float pitch = (i % 3 - 1) * 6.0F;
					onServer(sp, p -> {
						p.setYRot(yaw);
						p.setXRot(pitch);
						gs().basic(p);
					});
					ctx.waitTicks(Ticks.of(4));
				}
				ctx.runOnClient(mc -> {
					int n = kr.overbreak.client.fx.TrailView.count(mc.player.getId());
					if (n != 10) {
						throw new AssertionError("궤적 10줄이어야 함: " + n);
					}
				});
				ctx.takeScreenshot("gs_" + tag + "_ult_trails");
				onServer(sp, p -> Gunslinger.state(p).release.forceTelegraph());
				shots(ctx, "gs_" + tag + "_ult_telegraph", 3, 8);
				ctx.waitTicks(Ticks.of(3));
				ctx.takeScreenshot("gs_" + tag + "_ult_burst");
				ctx.runOnClient(mc -> mc.player.setYRot(0.0F));
				onServer(sp, p -> {
					p.setYRot(0.0F);
					p.setXRot(4.0F);
				});
				reset(sp);
				ctx.waitTicks(Ticks.of(20));
			}

			// 재장전 한가운데에 탄창 두 개가 실제로 떠 있는지 (1인칭 렌더 경로)
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			onServer(sp, p -> {
				Gunslinger.state(p).ammo = 3;
				gs().reload(p);
			});
			ctx.waitTicks(Ticks.of(8));
			ctx.runOnClient(mc -> {
				if (mc.player == null || !GunslingerAnim.magsInAir(mc.player.getId(), 1.0F)) {
					throw new AssertionError("재장전 중반에 탄창이 공중에 없음");
				}
			});
			ctx.waitTicks(Ticks.of(DualPistols.RELOAD));
			ctx.runOnClient(mc -> {
				if (mc.player != null && GunslingerAnim.magsInAir(mc.player.getId(), 1.0F)) {
					throw new AssertionError("재장전이 끝났는데 탄창이 아직 떠 있음");
				}
			});
			// 돌진 난사 — 쓰는 동안 3인칭으로 잡혔다가 끝나면 1인칭으로 돌아와야 합니다
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			onServer(sp, p -> {
				Attachments.profile(p).cooldowns.clear();
				gs().secondary(p);
			});
			ctx.waitTicks(Ticks.of(6));
			ctx.takeScreenshot("gs_scatter_view");
			ctx.runOnClient(mc -> {
				if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
					throw new AssertionError("돌진 난사 중인데 3인칭이 아님: " + mc.options.getCameraType());
				}
			});
			ctx.waitTicks(Ticks.of(kr.overbreak.classes.gunslinger.DashScatter.LENGTH));
			ctx.runOnClient(mc -> {
				if (mc.options.getCameraType() != CameraType.FIRST_PERSON) {
					throw new AssertionError("끝났는데 1인칭으로 돌아오지 않음: " + mc.options.getCameraType());
				}
			});
			// 돌진 난사 중에는 F5 로도 1인칭이 되지 않음 (0.2e)
			onServer(sp, p -> {
				Attachments.profile(p).cooldowns.clear();
				gs().secondary(p);
			});
			ctx.waitTicks(Ticks.of(4));
			ctx.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
					throw new AssertionError("돌진 난사 중 F5 로 1인칭이 됨");
				}
			});
			ctx.waitTicks(Ticks.of(kr.overbreak.classes.gunslinger.DashScatter.LENGTH + 5));

			// 3인칭 → 1인칭 전환 0.3초 — 카메라가 뒤에서 당겨 들어옴
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(Ticks.of(10));
			ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			ctx.waitTicks(1);
			ctx.runOnClient(mc -> {
				if (!kr.overbreak.client.camera.ViewTransition.active()) {
					throw new AssertionError("1인칭으로 바꾼 직후 전환 중이 아님");
				}
			});
			ctx.takeScreenshot("gs_view_transition_mid");
			ctx.waitTicks(Ticks.of(12));
			ctx.runOnClient(mc -> {
				if (kr.overbreak.client.camera.ViewTransition.active() || mc.gameRenderer.mainCamera().isDetached()) {
					throw new AssertionError("0.3초 뒤에도 전환이 끝나지 않음");
				}
			});
			onServer(sp, Classes::clear);
		}
	}

	/** 지정한 경과 틱마다 한 장씩. */
	private static void shots(ClientGameTestContext ctx, String label, int... at) {
		int now = 0;
		for (int t : at) {
			ctx.waitTicks(Ticks.of(t - now));
			now = t;
			ctx.takeScreenshot(label + "_" + t);
		}
	}

	private static void reset(TestSingleplayerContext sp) {
		onServer(sp, p -> {
			Attachments.profile(p).cooldowns.clear();
			Attachments.profile(p).atkCd = 0;
			Attachments.combatant(p).casting = false;
			Gunslinger.state(p).shotCd = 0;
			Gunslinger.state(p).reloadT = 0;
			Gunslinger.state(p).ammo = DualPistols.MAG;
		});
	}

	private static void onServer(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}

	private static PvpClass gs() {
		return Classes.byId(Gunslinger.ID);
	}
}
