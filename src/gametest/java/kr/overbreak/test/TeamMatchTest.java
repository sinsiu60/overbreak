package kr.overbreak.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.game.Game;
import kr.overbreak.game.Session;
import kr.overbreak.game.TeamMatch;
import kr.overbreak.net.MenuActionPayload;
import kr.overbreak.util.Targets;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 팀 격전 — 편 짜기(직접 고르기 · 자동 배정) · 아군 판정 제외 · 전멸하면 점수.
 *
 * 가짜 플레이어는 접속 목록에 없으므로 {@link Game} 의 틱이 사람을 건드리지 않고,
 * 경기 진행(편 짜기 · 카운트다운 · 점수)만 실제로 돕니다.
 * 경기는 서버에 하나뿐이라 3대3 · 2대2 를 한 시험 안에서 차례로 봅니다.
 */
public final class TeamMatchTest implements CustomTestMethodInvoker {
	private static FakePlayer player(GameTestHelper h, Vec3 rel, String name) {
		ServerLevel level = h.getLevel();
		FakePlayer p = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), name));
		Vec3 abs = h.absoluteVec(rel);
		p.snapTo(abs.x, abs.y, abs.z, 0.0F, 0.0F);
		return p;
	}

	private static List<FakePlayer> queue(GameTestHelper h, int teamSize, String mode, String tag) {
		List<FakePlayer> list = new ArrayList<>();
		for (int i = 0; i < teamSize * 2; i++) {
			FakePlayer p = player(h, new Vec3(1.5 + i * 0.5, 0, 1.5), tag + i);
			Session.setTutorialDone(p, true);
			list.add(p);
			Game.onAction(p, MenuActionPayload.QUEUE, "warrior", mode);
		}
		return list;
	}

	@GameTest(maxTicks = 500)
	public void teamDraftAndRounds(GameTestHelper h) {
		Game.resetForTest();
		List<FakePlayer> six = queue(h, 3, MenuActionPayload.MODE_TEAM3, "ob_t3_");
		h.assertTrue(Game.teamMatch() != null || Game.teamQueued(3) == 6,
				"여섯 명이 대기열에 들어감 (실측 " + Game.teamQueued(3) + ")");

		// ── 편 짜기: 플레이어가 직접 고릅니다 ──
		h.runAfterDelay(T.of(3), () -> {
			TeamMatch m = Game.teamMatch();
			h.assertTrue(m != null, "여섯 명이 모이면 편 짜기 시작");
			h.assertTrue(m.phase() == TeamMatch.Phase.DRAFT, "편 짜기 단계 (실측 " + m.phase() + ")");
			h.assertTrue(m.size() == 3, "한 팀 3명 (실측 " + m.size() + ")");
			for (FakePlayer p : six) {
				h.assertTrue(m.team(p) < 0, "처음에는 아무 편도 아님");
			}
			for (int i = 0; i < six.size(); i++) {
				h.assertTrue(m.pickTeam(six.get(i), i % 2), "편 고르기 " + i);
			}
			h.assertTrue(m.aliveCount(0) == 3 && m.aliveCount(1) == 3, "3 대 3");
			h.assertTrue(!m.pickTeam(six.get(1), 0), "찬 편으로는 못 감");
			h.assertTrue(m.team(six.get(1)) == 1, "홍팀에 그대로");
		});

		// ── 양쪽이 다 차면 바로 시작 · 아군은 판정에서 빠짐 ──
		h.runAfterDelay(T.of(6), () -> {
			TeamMatch m = Game.teamMatch();
			h.assertTrue(m != null && m.phase() != TeamMatch.Phase.DRAFT, "편이 다 차면 경기 시작");

			ServerPlayer mate = m.memberOf(0, 0);
			ServerPlayer ally = m.memberOf(0, 1);
			ServerPlayer foe = m.memberOf(1, 0);
			h.assertTrue(mate != null && ally != null && foe != null, "여섯 자리가 모두 참");
			h.assertTrue(m.sameTeam(mate, ally) && !m.sameTeam(mate, foe), "같은 편 · 다른 편");
			h.assertTrue(!Targets.hostile(mate, ally), "아군은 범위 스킬 · 총알에 잡히지 않음");
			h.assertTrue(Targets.hostile(mate, foe), "상대는 잡힘");
			h.assertTrue(!Targets.hostile(mate, mate), "자기 자신도 빠짐");
			LivingEntity dummy = h.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new Vec3(3.5, 0, 3.5));
			h.assertTrue(Targets.hostile(mate, dummy), "경기 밖의 생명체는 그대로 맞음");
			dummy.discard();
		});

		// ── 카운트다운 3초 뒤 전투 — 청팀 전멸이면 홍팀 1점 ──
		h.runAfterDelay(T.of(70), () -> {
			TeamMatch m = Game.teamMatch();
			h.assertTrue(m != null, "경기 진행 중");
			h.assertTrue(m.fighting(m.memberOf(0, 0)), "전투 단계 (실측 " + m.phase() + ")");
			for (int i = 0; i < m.size(); i++) {
				m.onDown(m.memberOf(0, i));
			}
			h.assertTrue(m.aliveCount(0) == 0, "청팀 전멸");
			h.assertTrue(m.score(1) == 1, "홍팀 1점 (실측 " + m.score(1) + ")");
			h.assertTrue(m.score(0) == 0, "청팀 0점");
			h.assertTrue(m.phase() == TeamMatch.Phase.ROUND_END, "라운드 마무리");
			cleanup(six);
		});

		// ── 2대2: 한 명만 고르고 시간이 되면 나머지는 자동 배정 ──
		List<FakePlayer> four = new ArrayList<>();
		h.runAfterDelay(T.of(73), () -> four.addAll(queue(h, 2, MenuActionPayload.MODE_TEAM2, "ob_t2_")));
		h.runAfterDelay(T.of(77), () -> {
			TeamMatch m = Game.teamMatch();
			h.assertTrue(m != null, "네 명이 모이면 편 짜기 시작");
			h.assertTrue(m.size() == 2 && m.players() == 4, "2대2 (실측 " + m.size() + ")");
			h.assertTrue(m.pickTeam(four.getFirst(), 1), "홍팀 고르기");
			h.assertTrue(m.team(four.getFirst()) == 1, "홍팀");
			// 편 짜기 시간이 다 됐을 때와 같은 처리 — 남은 사람이 빈자리에 들어갑니다
			m.begin();
			h.assertTrue(m.aliveCount(0) == 2 && m.aliveCount(1) == 2, "2 대 2 로 채워짐 (실측 "
					+ m.aliveCount(0) + " : " + m.aliveCount(1) + ")");
			cleanup(four);
			h.succeed();
		});
	}

	private static void cleanup(List<FakePlayer> players) {
		for (FakePlayer p : players) {
			Game.toMenu(p);
			Classes.clear(p);
		}
		Game.resetForTest();
	}

	@Override
	public void invokeTestMethod(GameTestHelper h, Method method) throws ReflectiveOperationException {
		method.invoke(this, h);
	}
}
