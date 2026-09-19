package kr.overbreak.combat;

import kr.overbreak.Overbreak;
import kr.overbreak.core.Attachments;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * 스킬 피해 — 데이터팩 skill/dmg/scale + $damage 매크로 대응.
 *
 * 피해 타입은 모드 리소스(data/overbreak/damage_type)에 들어 있어 월드 로드 때 항상 등록됩니다.
 * 데이터팩처럼 "새 피해 타입은 서버 재시작" 문제가 없습니다.
 */
public final class SkillDamage {
	private SkillDamage() {}

	/** 넉백 여부와 무적 프레임 관통 여부의 조합. 태그는 minecraft:no_knockback / bypasses_cooldown. */
	public enum Kind {
		/** 넉백 있음 · 무적 프레임 존중 */
		NORMAL("skill"),
		/** 넉백 있음 · 무적 프레임 관통 (연타 스킬) */
		MULTI("skill_multi"),
		/** 넉백 없음 · 무적 프레임 존중 */
		NO_KB("skill_nokb"),
		/** 넉백 없음 · 무적 프레임 관통 */
		MULTI_NO_KB("skill_multi_nokb"),
		/** 무적 시간까지 무시 (뇌진탕 · 연출용 체력 깎기) */
		PIERCE("pierce");

		final String path;

		Kind(String path) {
			this.path = path;
		}
	}

	/**
	 * 기본 피해(x10 정수)에 시전자 피해 배율을 곱해 넣습니다.
	 * 데이터팩과 같은 정수 계산이라 0.1 단위에서 내림됩니다 (#dv = base10 x mul / 100).
	 */
	public static boolean deal(LivingEntity target, @Nullable LivingEntity attacker, int base10, Kind kind) {
		int mul = attacker == null ? 100 : Attachments.combatant(attacker).dmgMul;
		int dv = base10 * mul / 100;
		return raw(target, attacker, dv / 10.0F, kind);
	}

	/** 0.01 단위 (x100 정수). 산탄처럼 여러 발이 따로 들어가 잘림이 쌓이는 스킬용. */
	public static boolean dealFine(LivingEntity target, @Nullable LivingEntity attacker, int base100, Kind kind) {
		int mul = attacker == null ? 100 : Attachments.combatant(attacker).dmgMul;
		int dv = base100 * mul / 100;
		return raw(target, attacker, dv / 100.0F, kind);
	}

	/** 넉백이 없는 종류인가 — 맞아도 움직임이 흔들리지 않아야 합니다. */
	public static boolean noKnockback(Kind kind) {
		return kind == Kind.NO_KB || kind == Kind.MULTI_NO_KB || kind == Kind.PIERCE;
	}

	/** 배율을 거치지 않고 그대로 넣습니다. */
	public static boolean raw(LivingEntity target, @Nullable LivingEntity attacker, float amount, Kind kind) {
		if (!(target.level() instanceof ServerLevel level) || amount <= 0) {
			return false;
		}
		Holder<DamageType> type = type(level, kind);
		DamageSource source = attacker == null ? new DamageSource(type) : new DamageSource(type, attacker, attacker);
		// 넉백 없는 피해는 맞은 사람의 움직임을 그대로 둡니다.
		// 바닐라는 맞을 때마다 hurtMarked 로 속도 패킷을 보내는데, 그때마다 클라이언트 속도가 서버 값으로
		// 덮여 연사에 맞으면 달리다 미끄러지듯 느려집니다 (0.1c 수정).
		net.minecraft.world.phys.Vec3 before = target.getDeltaMovement();
		boolean marked = target.hurtMarked;
		boolean hit = target.hurtServer(level, source, amount);
		if (hit && noKnockback(kind)) {
			target.setDeltaMovement(before);
			target.hurtMarked = marked;
		}
		Hurt.feedback(target, attacker, amount, hit);
		return hit;
	}

	public static Holder<DamageType> type(ServerLevel level, Kind kind) {
		return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
				.getOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, Overbreak.id(kind.path)));
	}
}
