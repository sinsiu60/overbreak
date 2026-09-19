package kr.overbreak.core.tick;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.core.Attachments;
import kr.overbreak.ult.UltGauge;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * MSPT 부하 시험용 봇 — 서버 안에서만 도는 가짜 플레이어가 직업을 받고 표적(주민)을 향해 평타 · 스킬 · 궁극기를 계속 씁니다.
 *
 * 서버 쪽 전투 부하(스킬 틱 · 판정 · 파티클 패킷 만들기 · CC)는 재지만, 실제 접속자의 네트워크 · 이동 패킷 처리 · 청크 전송은 들어가지
 * 않습니다 — 최종 확인은 실제 클라이언트 10개로 해야 합니다. 봇은 둥글게 돌며 움직입니다.
 */
public final class LoadBots {
	private static final List<FakePlayer> BOTS = new ArrayList<>();
	private static final List<Villager> TARGETS = new ArrayList<>();
	private static final Random RANDOM = new Random(7);
	private static Vec3 center = Vec3.ZERO;
	private static long t;

	private LoadBots() {}

	public static int count() {
		return BOTS.size();
	}

	public static void spawn(ServerLevel level, Vec3 at, int n) {
		clear();
		center = at;
		List<PvpClass> classes = Classes.all();
		for (int i = 0; i < n; i++) {
			FakePlayer bot = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "ob_bot" + i));
			double a = Math.PI * 2.0 * i / n;
			bot.snapTo(at.x + Math.cos(a) * 6.0, at.y, at.z + Math.sin(a) * 6.0, 0.0F, 0.0F);
			Classes.give(bot, classes.get(i % classes.size()));
			BOTS.add(bot);
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			if (v != null) {
				v.snapTo(at.x + Math.cos(a) * 3.0, at.y, at.z + Math.sin(a) * 3.0, 0.0F, 0.0F);
				v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000.0);
				v.setHealth(100000.0F);
				v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
				v.setNoAi(true);
				v.addTag("overbreak.load_target");
				level.addFreshEntity(v);
				TARGETS.add(v);
			}
		}
	}

	public static void clear() {
		BOTS.forEach(Classes::clear);
		BOTS.clear();
		TARGETS.forEach(Villager::discard);
		TARGETS.clear();
	}

	static void tick(MinecraftServer server) {
		if (BOTS.isEmpty()) {
			return;
		}
		t++;
		int rate = TickRateConfig.tickRate();
		for (int i = 0; i < BOTS.size(); i++) {
			FakePlayer bot = BOTS.get(i);
			PvpClass c = Attachments.profile(bot).pvpClass;
			if (c == null) {
				continue;
			}
			// 둥글게 이동하며 가운데를 봄 (1바퀴 8초)
			double a = Math.PI * 2.0 * i / BOTS.size() + t * (Math.PI * 2.0) / (8.0 * rate);
			double x = center.x + Math.cos(a) * 6.0;
			double z = center.z + Math.sin(a) * 6.0;
			float yaw = (float) Math.toDegrees(Math.atan2(center.z - z, center.x - x)) - 90.0F;
			bot.snapTo(x, center.y, z, yaw, 8.0F);
			c.tick(bot);
			if (Attachments.profile(bot).atkCd > 0) {
				Attachments.profile(bot).atkCd--;
			}
			if ((t + i) % Math.max(1, rate / 4) == 0) {  // 1초에 4번
				c.basic(bot);
			}
			int roll = RANDOM.nextInt(rate * 2);
			if (roll == 0) {
				c.primary(bot);
			} else if (roll == 1) {
				c.secondary(bot);
			} else if (roll == 2) {
				c.tertiary(bot);
			} else if (roll == 3) {
				UltGauge.fill(bot);
				c.ult(bot);
			}
		}
		for (Villager v : TARGETS) {
			if (v.getHealth() < 50000.0F) {
				v.setHealth(v.getMaxHealth());
			}
		}
	}
}
