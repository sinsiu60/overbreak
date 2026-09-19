package kr.overbreak.util;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 대상 고르기 — 데이터팩 {@code @e[distance=..R, type=!item_display, type=!armor_stand] if data entity @s Health}.
 * 거리는 데이터팩과 같이 <b>엔티티 발밑 좌표</b> 기준입니다.
 */
public final class Targets {
	/**
	 * 같은 편인가 — 팀전에서 아군을 스킬 · 총알 판정에서 통째로 빼기 위한 갈고리.
	 * 게임 흐름(kr.overbreak.game.Game)이 접속 시 꽂습니다. 기본은 "아무도 같은 편이 아님".
	 */
	public static java.util.function.BiPredicate<Entity, LivingEntity> allies = (caster, target) -> false;

	private Targets() {}

	/** 시전자에게 맞을 수 있는 대상인가 (자기 자신 · 같은 편 제외). */
	public static boolean hostile(Entity caster, LivingEntity e) {
		return e != caster && !allies.test(caster, e);
	}

	public static List<LivingEntity> within(ServerLevel level, Vec3 center, double radius, Predicate<LivingEntity> filter) {
		double r2 = radius * radius;
		AABB box = new AABB(center, center).inflate(radius + 1.0);
		return level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !(e instanceof ArmorStand) && e.position().distanceToSqr(center) <= r2 && filter.test(e));
	}

	/** 시전자 본인과 같은 편을 뺀 반경 안의 생명체. */
	public static List<LivingEntity> enemies(ServerLevel level, Vec3 center, double radius, Entity caster) {
		return within(level, center, radius, e -> hostile(caster, e));
	}

	/**
	 * 수평 부채꼴 판정 — 기준점에서 대상 발밑까지 radius 안이고, yaw 방향에서 좌우 halfArcDeg 안.
	 * 수평으로 1칸 안에 붙은 대상은 방향과 관계없이 맞습니다.
	 */
	public static boolean inCone(Vec3 origin, float yawDeg, double halfArcDeg, double radius, LivingEntity e) {
		Vec3 to = e.position().subtract(origin);
		if (to.length() > radius) {
			return false;
		}
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		if (flat < 1.0) {
			return true;
		}
		double yaw = Math.toRadians(yawDeg);
		return (to.x * -Math.sin(yaw) + to.z * Math.cos(yaw)) / flat >= Math.cos(Math.toRadians(halfArcDeg));
	}

	public static LivingEntity nearest(List<LivingEntity> list, Vec3 from) {
		return list.stream().min(Comparator.comparingDouble(e -> e.position().distanceToSqr(from))).orElse(null);
	}
}
