package kr.overbreak.combat;

import java.util.List;

import kr.overbreak.classes.PvpClass;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.util.Fx;
import kr.overbreak.util.GroundShape;
import kr.overbreak.util.Local;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 근접 평타 — 좌클릭하면 조준 방향 앞 부채꼴 안의 적 모두에게 피해.
 *
 *   범위: 눈에서 대상 몸(히트박스)의 가장 가까운 점까지 meleeRange (기본 3칸 = 바닐라 공격 거리)
 *   각도: 수평 기준 meleeArcDegrees (기본 100도 = 좌우 50도). 1칸 안에 붙은 적은 각도와 무관하게 맞음
 *   벽 너머는 맞지 않음 (눈 → 대상 중심 레이캐스트)
 *   피해: 공격력 속성값 (무기 · 분노 등 버프 포함), 바닐라 근접 공격 판정 (넉백 · 게이지 · 출혈 등 그대로)
 *   공격속도: 한 번 휘두르면 맞은 수와 관계없이 잠금 (빗나가도 잠김)
 */
public final class MeleeCleave {
	public static final double DEFAULT_RANGE = 3.0;
	public static final double DEFAULT_ARC = 100.0;
	private static final double POINT_BLANK = 1.0;

	private MeleeCleave() {}

	/** @return 실제로 휘둘렀으면 true (공격속도 · 정신집중 · 기절로 막히면 false) */
	public static boolean swing(ServerPlayer p, PvpClass c) {
		PlayerProfile prof = Attachments.profile(p);
		Combatant cb = Attachments.combatant(p);
		if (prof.atkCd > 0 || cb.casting || cb.hardCc()) {
			return false;
		}
		if (prof.atkSpeed > 0) {
			prof.atkCd = kr.overbreak.core.tick.Ticks.of(Math.max(1, 2000 / prof.atkSpeed));
		}

		ServerLevel level = p.level();
		// 애니메이션: 정방향 · 역방향 베기를 번갈아. 1.5초(30틱) 쉬면 정방향부터.
		long now = kr.overbreak.core.tick.GameClock.now();
		if (now - prof.lastCleaveTick > 30) {
			prof.basicCombo = 0;
		}
		prof.lastCleaveTick = now;
		SkillAnimPayload.broadcast(p, c.basicAnim(prof.basicCombo++ % 2 != 0), -1);

		Vec3 eye = p.getEyePosition();
		Vec3 aim = Aim.facing(p);
		Vec3 flat = new Vec3(aim.x, 0.0, aim.z);
		if (flat.lengthSqr() < 1.0E-4) {
			float yaw = p.getYRot() * Mth.DEG_TO_RAD;
			flat = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		}
		Vec3 flatDir = flat.normalize();
		double range = c.meleeRange(p);
		double cosHalf = Math.cos(Math.toRadians(c.meleeArcDegrees() / 2.0));
		float damage = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);

		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range + 1.0),
				e -> kr.overbreak.util.Targets.hostile(p, e) && e.isAlive() && !(e instanceof ArmorStand)
						&& inCleave(level, p, eye, flatDir, e, range, cosHalf));

		DamageSource source = p.damageSources().playerAttack(p);
		int hits = 0;
		for (LivingEntity e : targets) {
			if (c.meleeClearsInvulnerability()) {
				// 직전 스킬이 만든 무적 시간에 평타가 통째로 씹히면 뒤따르는 스킬까지 함께 씹힙니다 (0.1f 수정)
				e.invulnerableTime = 0;
			}
			net.minecraft.world.phys.Vec3 before = e.getDeltaMovement();
			boolean marked = e.hurtMarked;
			if (e.hurtServer(level, source, damage)) {
				hits++;
				if (!c.meleeKnockback()) {
					// 맞아도 밀리지 않게 — 맞기 직전 속도로 되돌리고, 속도 패킷도 보내지 않습니다.
					// hurtMarked 를 켜면 맞을 때마다 클라이언트 속도가 서버 값으로 덮여 미끄러지듯 느려집니다 (0.1c)
					e.setDeltaMovement(before);
					e.hurtMarked = marked;
				}
				kr.overbreak.combat.Hurt.feedback(e, p, damage, true);
				if (c.meleeClearsInvulnerability()) {
					// 평타가 만든 무적 시간을 지워 스킬 콤보가 씹히지 않게
					e.invulnerableTime = 0;
				}
				c.onMeleeHit(p, e);
			}
		}

		// 판정 범위 — 바닥에 잠깐 비치는 반투명 부채꼴 모델
		GroundShape.flash(level, p.position(), Local.yawPitch(flatDir)[0], c.meleeArcDegrees(), range, 0x38FFFFFF, 0xB0FFFFFF, 5);
		if (hits > 0) {
			Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
		} else {
			Fx.sound(p, SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.8F, 1.0F);
		}
		return true;
	}

	/** 부채꼴 판정. */
	public static boolean inCleave(ServerLevel level, ServerPlayer p, Vec3 eye, Vec3 flatDir, LivingEntity e, double range, double cosHalf) {
		AABB box = e.getBoundingBox();
		Vec3 closest = new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY), Mth.clamp(eye.z, box.minZ, box.maxZ));
		double dist = closest.distanceTo(eye);
		if (dist > range) {
			return false;
		}
		Vec3 center = box.getCenter();
		if (dist > POINT_BLANK) {
			Vec3 to = new Vec3(center.x - p.getX(), 0.0, center.z - p.getZ());
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(flatDir) < cosHalf) {
				return false;
			}
		}
		HitResult wall = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return wall.getType() == HitResult.Type.MISS;
	}
}
