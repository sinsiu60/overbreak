package kr.overbreak.classes.ironcleaver;

import java.util.List;

import kr.overbreak.combat.DamageModifiers;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.net.IronPayload;
import kr.overbreak.util.Targets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 참철 판정 도우미 — 부채꼴 · 직선 · 높이 · 시야 · 피해 (방어 파쇄 · 타격감 신호). */
public final class IronStrikes {
	private IronStrikes() {}

	/** 둘레 적 (넉넉한 반경). */
	static List<LivingEntity> around(ServerPlayer p, double range) {
		return Targets.enemies(p.level(), p.position(), range + 2.5, p);
	}

	/**
	 * 몸 기준 수평 각도 (도) — 0 = 정면, + = 오른쪽, - = 왼쪽.
	 * 마인크래프트 yaw 는 0 이 +Z, 시계 방향(위에서 보아)으로 커집니다.
	 */
	static double relAngle(ServerPlayer p, float yaw, LivingEntity e) {
		double dx = e.getX() - p.getX();
		double dz = e.getZ() - p.getZ();
		double target = Math.toDegrees(Math.atan2(-dx, dz));
		return Mth.wrapDegrees(target - yaw);
	}

	/** 몸 가장자리까지 수평 거리. */
	static double reach(ServerPlayer p, LivingEntity e) {
		double dx = e.getX() - p.getX();
		double dz = e.getZ() - p.getZ();
		return Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2.0);
	}

	/** 판정 높이 — 시전자 발밑 low ~ 머리 위 high 안에 몸이 걸치는가. */
	static boolean inHeight(ServerPlayer p, LivingEntity e, double low, double high) {
		AABB b = e.getBoundingBox();
		return b.maxY >= p.getY() + low && b.minY <= p.getY() + p.getBbHeight() + high;
	}

	/** 시전자 눈 → 적 몸통 시야가 트였는가 (벽 너머 제외). */
	static boolean sees(ServerPlayer p, LivingEntity e) {
		Vec3 eye = p.getEyePosition();
		Vec3 body = e.position().add(0, e.getBbHeight() * 0.5, 0);
		return p.level().clip(new ClipContext(eye, body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
	}

	/** 앞 직선 (길이 · 폭) 안인가 — yaw 방향, 발 기준. */
	static boolean inLine(ServerPlayer p, float yaw, LivingEntity e, double length, double width) {
		double y = Math.toRadians(yaw);
		double fx = -Math.sin(y);
		double fz = Math.cos(y);
		double dx = e.getX() - p.getX();
		double dz = e.getZ() - p.getZ();
		double along = dx * fx + dz * fz;
		double side = Math.abs(dx * -fz + dz * fx);
		double half = e.getBbWidth() / 2.0;
		return along >= -half && along <= length + half && side <= width / 2.0 + half;
	}

	/**
	 * 피해 한 번 — 방어 파쇄(피해 감소 절반)는 이 호출 동안만 켭니다. 넉백 없음.
	 * @param strength 타격감 세기 (0 평타 · 1 3타 · 2~5 모아 베기 1단~진 참 · 6 박치기)
	 */
	static void deal(ServerPlayer p, LivingEntity e, int damage100, boolean guardBreak, int strength) {
		float before = DamageModifiers.guardBreak;
		if (guardBreak) {
			DamageModifiers.guardBreak = IronSpec.GUARD_BREAK_FACTOR;
		}
		try {
			SkillDamage.dealFine(e, p, damage100, SkillDamage.Kind.MULTI_NO_KB);
		} finally {
			DamageModifiers.guardBreak = before;
		}
		if (e instanceof ServerPlayer victim) {
			IronPayload.send(victim, IronPayload.of(IronPayload.HURT, p, strength, 0));
		}
	}

	/** 이번 동작이 누군가를 맞혔다 — 시전자 본인에게 역경직 · 흔들림 신호 (한 동작에 한 번). */
	static void confirm(ServerPlayer p, int strength) {
		IronPayload.send(p, IronPayload.of(IronPayload.HIT, p, strength, 0));
	}
}
