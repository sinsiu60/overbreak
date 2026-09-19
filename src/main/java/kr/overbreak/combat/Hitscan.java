package kr.overbreak.combat;

import java.util.function.Predicate;

import kr.overbreak.util.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 히트스캔 한 줄 — 데이터팩 util/ray/cast 대응. 벽에 막히고 첫 생명체에서 멈춥니다.
 * 머리 판정(치명타 2배)도 여기서 봅니다. 플레이어는 쏜 사람의 핑만큼 되감아 판정합니다 (HitboxRewind).
 */
public final class Hitscan {
	/**
	 * @param target 맞은 생명체 (없으면 null)
	 * @param end    광선이 멈춘 곳 (몸 · 벽 · 사거리 끝) — 예광탄 길이
	 * @param head   머리에 맞았는가
	 */
	public record Hit(@Nullable LivingEntity target, Vec3 end, boolean head) {}

	private Hitscan() {}

	/** 쏜 사람의 핑만큼 플레이어를 되감아 판정 ({@link HitboxRewind}). */
	public static Hit cast(ServerPlayer shooter, Vec3 from, Vec3 dir, double range) {
		return cast(shooter, from, dir, range, HitboxRewind.rewindTicks(shooter));
	}

	/**
	 * @param rewindTicks 플레이어 히트박스를 몇 서버 틱 전으로 되감을지 (0 = 지금)
	 */
	public static Hit cast(ServerPlayer shooter, Vec3 from, Vec3 dir, double range, int rewindTicks) {
		Vec3 end = from.add(dir.normalize().scale(range));
		BlockHitResult block = shooter.level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		// 아군은 총알이 그냥 지나갑니다 (막지도, 맞지도 않음) — 팀전
		Predicate<Entity> living = e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)
				&& kr.overbreak.util.Targets.hostile(shooter, le);
		// 되감은 자리까지 찾도록 넉넉히 (최대 200ms 동안 달려간 거리)
		AABB search = new AABB(from, end).inflate(3.0);
		double best = from.distanceToSqr(end);
		Vec3 back = dir.normalize().scale(-8.0);
		LivingEntity victim = null;
		Vec3 point = null;
		Vec3 entry = null;
		double eyeY = 0.0;
		for (Entity e : shooter.level().getEntities(shooter, search, living)) {
			LivingEntity le = (LivingEntity) e;
			// 기록이 있는 생명체(플레이어)만 되감음
			AABB box = HitboxRewind.boxAt(le, rewindTicks);
			AABB pick = box.inflate(e.getPickRadius());
			boolean inside = pick.contains(from);
			// 밀착: 눈이 몸 안이라 광선의 시작점이 곧 명중점이 됩니다. 머리 판정은 광선을 뒤로 물려
			// "몸에 들어간 면" 에서 봐야 합니다 — 그대로 두면 밀착 사격이 늘 머리로 잡혔습니다 (0.1a 수정)
			Vec3 at = pick.clip(inside ? from.add(back) : from, end).orElse(null);
			if (at == null && inside) {
				at = from;
			}
			if (at == null) {
				continue;
			}
			double d = inside ? 0.0 : from.distanceToSqr(at);
			if (d < best || (victim == null && d <= best)) {
				best = d;
				victim = le;
				point = inside ? from : at;
				entry = at;
				eyeY = box.minY + le.getEyeHeight();
			}
		}
		if (victim != null) {
			return new Hit(victim, point, headshot(victim, entry, eyeY));
		}
		return new Hit(null, end, false);
	}

	/**
	 * 머리 판정 — 눈높이에서 머리 크기만큼 아래부터 위 끝까지.
	 * 플레이어는 머리 0.5칸 중 눈 아래가 약 0.32칸이라, 키에 비례해 같은 비율로 봅니다 (웅크리면 같이 낮아짐).
	 */
	public static boolean headshot(LivingEntity e, Vec3 point) {
		return headshot(e, point, e.getEyeY());
	}

	/** 되감은 눈높이 기준 머리 판정. */
	public static boolean headshot(LivingEntity e, Vec3 point, double eyeY) {
		return point.y >= eyeY - 0.32 * e.getBbHeight() / 1.8;
	}

	/** 방향을 좌우(도, + = 오른쪽) · 위아래(도, + = 위)로 틉니다. */
	public static Vec3 turn(Vec3 dir, double yawDeg, double upDeg) {
		float[] yp = Local.yawPitch(dir);
		return Vec3.directionFromRotation((float) (yp[1] - upDeg), (float) (yp[0] + yawDeg));
	}
}
