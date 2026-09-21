package kr.overbreak.client.anim.scatter;

import java.util.HashMap;
import java.util.Map;

import kr.overbreak.classes.gunslinger.DashScatter;
import kr.overbreak.client.anim.SkillAnims;
import kr.overbreak.client.fx.BulletTrails;
import kr.overbreak.client.fx.ScatterSounds;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.net.TracerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 돌진 난사 사격 이벤트 — 애니메이션이 사격 방향의 유일한 원천입니다 (스펙 PART 3-5).
 *
 *   발마다 그 순간의 자세(루트 yaw · pitch · roll + 상체 비틀림 + 팔 pitch · yaw)로 총구 자리와 방향을 계산해
 *   탄 궤적(판정 반경과 같은 5칸 · 0.1초) · 총구 화염 · 탄피 · 발사음을 냅니다.
 *   적 위치와는 아무 상관이 없습니다 — 판정은 서버의 원형 펄스가 따로 맡습니다.
 *   시드가 같으면 어느 클라이언트든 같은 방향으로 쏩니다. 늦게 보기 시작해도 지금 발부터 바로 계산합니다.
 */
public final class ScatterShots {
	private record Seen(SkillAnims.Play play, float until) {}

	private static final Map<Integer, Seen> SEEN = new HashMap<>();

	private ScatterShots() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			if (mc.level == null) {
				SEEN.clear();
			}
			return;
		}
		for (AbstractClientPlayer p : mc.level.players()) {
			SkillAnims.Play play = SkillAnims.find(p.getId(), SkillAnimPayload.GS_SCATTER);
			if (play == null) {
				SEEN.remove(p.getId());
				continue;
			}
			float now = Math.min(play.elapsed(1.0F), play.end());
			Seen seen = SEEN.get(p.getId());
			// 새 재생이면 지금 이전 발은 건너뜀 (늦게 받은 관전자 · 단계 건너뛰기)
			float from = seen != null && seen.play == play ? seen.until : play.elapsed(0.0F) - 1.0E-3F;
			for (ScatterBody.Shot s : ScatterBody.shotsBetween(from, now)) {
				fire(mc, p, s);
			}
			SEEN.put(p.getId(), new Seen(play, Math.max(from, now)));
		}
	}

	private static void fire(Minecraft mc, AbstractClientPlayer p, ScatterBody.Shot s) {
		int seed = ScatterView.seed(p.getId());
		float dashYaw = ScatterView.dashYaw(p.getId(), p.getYRot());
		ScatterBody.Pose pose = ScatterBody.pose(p.getId(), seed, s.at(), p.onGround(), false);
		float yaw = dashYaw + pose.rootYaw() + pose.wobbleYaw();
		Vec3[] m = ScatterBody.muzzle(pose, s.left(), p.position(), yaw);
		Vec3 tip = m[0];
		Vec3 to = tip.add(m[1].scale(DashScatter.RADIUS));
		BulletTrails.receive(new TracerPayload(p.getId(), tip.x, tip.y, tip.z, to.x, to.y, to.z, TracerPayload.GUNSLINGER_SCATTER));
		// 탄피 — 총 옆으로 튀어나감
		Vec3 kick = m[1].cross(new Vec3(0, 1, 0)).normalize().scale(s.left() ? -0.12 : 0.12).add(0, 0.12, 0);
		mc.level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_NUGGET), tip.x, tip.y, tip.z, kick.x, kick.y, kick.z);
		ScatterSounds.shoot(mc, p);
	}
}
