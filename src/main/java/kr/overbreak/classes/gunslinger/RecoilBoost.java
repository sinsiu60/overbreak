package kr.overbreak.classes.gunslinger;

import java.util.List;

import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Motion;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * [우클릭] 반동 도약 — 뒤로 쏜 반동으로 조준한 방향으로 날아가고, 충격탄은 출발 지점에서 터집니다 (0.2f).
 *
 *   적을 보고 쓰면 적에게 날아들고, 등지고 쓰면 도망치면서 쫓아오던 적이 출발점 폭발에 맞아 밀려납니다
 *
 *   방향: 시선 방향 그대로 (위아래 포함). 위로 향하는 몫이 0.19 보다 작으면 0.19 로 올림 → 수평을 봐도 8칸 날며 약 1.5칸 뜸
 *         수평보다 50° 넘게 아래를 보면 곧장 위로 약 7칸 솟구침
 *   추진: 0.25초 동안 중력 없이 등속 (초당 32칸 · 8칸). 끝나면 같은 방향 초당 6칸 관성만 남기고 중력 · 공중 제어에 맡김
 *         (바닥 조준은 수평 속도 0). 벽 · 천장에 막히면 그 자리에서 곧장 끝. 적은 뚫고 지나감
 *   폭발: 쓰는 순간의 발 위치, 반경 3칸 적에게 25 + 바깥으로 넉백. 벽 너머는 제외. 공중 치명타 없음
 *   균열 지대 위에서는 막힙니다 (이동기 · 쿨타임 안 씀). 쿨타임 6초. 쓸 때마다 활공 +1초
 */
public final class RecoilBoost implements Effects.Active {
	/** 쿨타임 (시간 단위 · 6초). */
	public static final int COOLDOWN = 120;
	/** 날아가는 거리 (칸) · 추진 시간 (시간 단위 · 0.25초) · 끝난 뒤 남는 속도 (초당 칸). */
	public static final double DISTANCE = 8.0;
	public static final int PROPULSION = 5;
	public static final double EXIT_SPEED = 6.0;
	/** 위로 향하는 최소 몫 · 바닥 조준 각도 (도) · 바닥 조준 때 솟구치는 높이 (칸). */
	public static final double MIN_UPWARD = 0.19;
	public static final double FLOOR_AIM = 50.0;
	public static final double FLOOR_RISE = 7.0;
	static final double RADIUS = 3.0;
	/** 폭발 피해 x100 (25.0). */
	static final int DAMAGE_100 = 2500;
	private static final int SKY = Fx.rgb(0.70, 0.92, 1.00);

	private final ServerPlayer caster;
	private final Vec3 dir;
	private final boolean floor;
	private int t;

	private RecoilBoost(ServerPlayer caster, Vec3 dir, boolean floor) {
		this.caster = caster;
		this.dir = dir;
		this.floor = floor;
	}

	static void cast(ServerPlayer p, GunslingerState st) {
		if (st.scatter != null) {
			DualPistols.denied(p);
			return;
		}
		if (Attachments.combatant(p).sealT > 0) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("균열 지대 위에서는 이동기를 쓸 수 없다", ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (Cooldowns.blocked(p, Gunslinger.BOOST, "반동 도약", ChatFormatting.AQUA)) {
			return;
		}
		DualPistols.interrupt(p, st);
		Attachments.profile(p).setCooldown(Gunslinger.BOOST, COOLDOWN);
		// 체공 훈풍 활공 +1초
		AeroDrift.extend(st);
		ServerLevel level = p.level();
		blast(p, level, p.position());
		Vec3 look = Aim.direction(p).normalize();
		boolean floor = floorAim(look);
		Vec3 dir = direction(look);
		double distance = floor ? FLOOR_RISE : DISTANCE;
		Motion.dash(p, dir, distance / PROPULSION, PROPULSION);
		// 속도를 싣는 창을 한 틱 늘립니다 — 서버가 실은 속도는 클라이언트에 한 틱 늦게 닿아 한 틱만큼 모자랐습니다
		// (실측 7.5칸 · 6.5칸, 돌진 난사와 같은 보정)
		Attachments.combatant(p).dashT += 1;
		// 추진 속도는 CrowdControl.tick 이 매 틱 실어 줍니다 — 목록에 올려야 실제로 움직임
		kr.overbreak.cc.CrowdControl.track(p);
		p.resetFallDistance();
		SkillAnimPayload.broadcast(p, SkillAnimPayload.GS_BOOST, -1);
		Effects.add(new RecoilBoost(p, dir, floor));
	}

	/** 수평보다 50° 넘게 아래를 보는가. */
	static boolean floorAim(Vec3 look) {
		return look.y < -Math.sin(Math.toRadians(FLOOR_AIM));
	}

	/** 날아갈 방향 — 바닥 조준이면 곧장 위, 아니면 위로 향하는 몫을 0.19 이상으로. 시험에서 직접 부릅니다. */
	public static Vec3 direction(Vec3 look) {
		if (floorAim(look)) {
			return new Vec3(0, 1, 0);
		}
		if (look.y < MIN_UPWARD) {
			double flat = Math.sqrt(Math.max(1.0E-9, look.x * look.x + look.z * look.z));
			double h = Math.sqrt(1.0 - MIN_UPWARD * MIN_UPWARD);
			return new Vec3(look.x / flat * h, MIN_UPWARD, look.z / flat * h);
		}
		return look;
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			return false;
		}
		t++;
		// 벽 · 천장에 막히면 그 자리에서 추진을 끝냄 (첫 틱은 출발이라 봐줌)
		boolean blocked = t > 1 && (caster.horizontalCollision || (caster.verticalCollision && dir.y > 0.1 && !caster.onGround()));
		if (t >= Ticks.of(PROPULSION) + 2 || blocked || Attachments.combatant(caster).hardCc()) {
			finish();
			return false;
		}
		return true;
	}

	/** 추진 끝 — 같은 방향으로 초당 6칸만 남김 (바닥 조준은 수평 0). */
	private void finish() {
		Attachments.combatant(caster).dashT = 0;
		Attachments.combatant(caster).dashHold = false;
		if (Attachments.combatant(caster).hardCc()) {
			return;
		}
		Vec3 exit = dir.scale(Ticks.speed(EXIT_SPEED / 20.0));
		if (floor) {
			exit = new Vec3(0, exit.y, 0);
		}
		caster.setDeltaMovement(exit);
		caster.hurtMarked = true;
		caster.resetFallDistance();
	}

	/** 출발점 폭발 — 반경 3칸 적에게 25 + 바깥으로 넉백 (시전자 자리에서 터지므로 넉백은 시전자 반대쪽 = 바깥). 벽 너머 제외. */
	private static void blast(ServerPlayer p, ServerLevel level, Vec3 feet) {
		Vec3 at = feet.add(0, 0.5, 0);
		List<LivingEntity> list = Targets.enemies(level, at, RADIUS, p);
		for (LivingEntity e : list) {
			Vec3 body = e.position().add(0, e.getBbHeight() * 0.5, 0);
			if (level.clip(new ClipContext(at, body, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) {
				continue;
			}
			SkillDamage.dealFine(e, p, DAMAGE_100, SkillDamage.Kind.NORMAL);
		}
		Fx.particle(level, ParticleTypes.EXPLOSION, at, 1, 0, 0, 0, 0);
		Fx.particle(level, ParticleTypes.CLOUD, at, 30, 0.6, 0.6, 0.6, 0.12);
		Fx.particle(level, Fx.dust(SKY, 1.5F), at, 26, 0.7, 0.7, 0.7, 0);
		Fx.particle(level, ParticleTypes.END_ROD, at, 14, 0.3, 0.3, 0.3, 0.14);
		Fx.sound(level, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.1F, 1.5F);
		Fx.sound(p, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	@Override
	public Object owner() {
		return caster;
	}
}
