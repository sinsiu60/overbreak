package kr.overbreak.classes.gunslinger;

import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Aim;
import kr.overbreak.combat.Hitscan;
import kr.overbreak.combat.Motion;
import kr.overbreak.combat.SkillDamage;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
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
import org.jspecify.annotations.Nullable;

/**
 * [E] 사선 앵커 — 16칸짜리 와이어를 쏘아 붙는 곳으로 끌려갑니다.
 *
 *   적에게 맞으면 : 20 피해 · 0.5초 기절 · 그 적의 <b>머리 위</b>로 끌려갑니다 (바로 공중 치명타 각)
 *   벽에 맞으면   : 그 지점으로 빠르게 당겨집니다
 *   아무것도 없으면: 와이어만 나가고 쿨타임은 절반만 (헛방)
 *   균열 지대 위에서는 막힙니다 (이동기). 쿨타임 7초
 */
public final class WireAnchor implements Effects.Active {
	/** 쿨타임 (시간 단위 · 7초). */
	public static final int COOLDOWN = 140;
	static final double RANGE = 16.0;
	/** 적중 피해 x100 (20.0). */
	static final int DAMAGE_100 = 2000;
	/** 기절 (시간 단위 · 0.5초). */
	static final int STUN = 10;
	/** 당겨지는 시간 (시간 단위). */
	static final int PULL = 6;
	/** 적 머리 위로 올라서는 높이 (칸). */
	static final double OVERHEAD = 1.6;
	private static final int SKY = Fx.rgb(0.70, 0.92, 1.00);

	private final ServerPlayer caster;
	private final Vec3 anchor;
	private final @Nullable LivingEntity target;
	private int t;
	/** 점프 키를 누르고 있었는가 — 쓸 때 이미 누르고 있던 것은 끊기로 치지 않습니다. */
	private boolean jumpHeld;

	private WireAnchor(ServerPlayer caster, Vec3 anchor, @Nullable LivingEntity target) {
		this.caster = caster;
		this.anchor = anchor;
		this.target = target;
		this.jumpHeld = Attachments.profile(caster).jumpDown;
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
		if (Cooldowns.blocked(p, Gunslinger.ANCHOR, "사선 앵커", ChatFormatting.AQUA)) {
			return;
		}
		DualPistols.interrupt(p, st);
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		Vec3 dir = Aim.direction(p);
		Hitscan.Hit hit = Hitscan.cast(p, eye, dir, RANGE);
		LivingEntity victim = hit.target();
		Vec3 end = hit.end();
		boolean wall = false;
		if (victim == null) {
			HitResult clip = level.clip(new ClipContext(eye, eye.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			wall = clip.getType() != HitResult.Type.MISS;
			if (wall) {
				end = clip.getLocation();
			}
		}
		SkillAnimPayload.broadcast(p, SkillAnimPayload.GS_ANCHOR, -1);
		wire(level, eye, end);
		// 체공 훈풍 활공 +1초 (헛방이어도 쏘았으면)
		AeroDrift.extend(st);
		Fx.sound(p, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.9F, 1.7F);
		if (victim == null && !wall) {
			// 헛방 — 쿨타임 절반
			Attachments.profile(p).setCooldown(Gunslinger.ANCHOR, COOLDOWN / 2);
			Fx.sound(p, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.5F, 0.8F);
			return;
		}
		Attachments.profile(p).setCooldown(Gunslinger.ANCHOR, COOLDOWN);
		Vec3 anchor;
		if (victim != null) {
			SkillDamage.dealFine(victim, p, DAMAGE_100, SkillDamage.Kind.NO_KB);
			CrowdControl.stun(victim, STUN);
			anchor = victim.position().add(0, victim.getBbHeight() + OVERHEAD, 0);
			Fx.sound(victim, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.4F);
		} else {
			// 벽: 표면에서 조금 떨어진 자리로
			anchor = end.subtract(dir.scale(0.8));
			Fx.sound(level, end.x, end.y, end.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
		}
		p.resetFallDistance();
		Effects.add(new WireAnchor(p, anchor, victim));
	}

	/** 와이어 한 줄 (출발 → 도착). */
	private static void wire(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		int steps = Math.max(4, (int) (d.length() * 3));
		for (int i = 0; i <= steps; i++) {
			Vec3 at = from.add(d.scale(i / (double) steps));
			Fx.particle(level, Fx.dust(SKY, 0.55F), at, 1, 0, 0, 0, 0);
		}
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected() || Attachments.combatant(caster).hardCc()) {
			return false;
		}
		t++;
		// 끌려가는 중 점프 키를 새로 누르면 와이어를 끊고 지금 속도 그대로 날아갑니다 (0.2e)
		boolean jump = Attachments.profile(caster).jumpDown;
		if (jump && !jumpHeld && t > 1) {
			snap();
			return false;
		}
		jumpHeld = jump;
		// 적을 걸었으면 그 적이 움직여도 머리 위를 따라갑니다
		Vec3 goal = target != null && target.isAlive()
				? target.position().add(0, target.getBbHeight() + OVERHEAD, 0)
				: anchor;
		Vec3 delta = goal.subtract(caster.position());
		double dist = delta.length();
		if (dist < 0.6 || t >= Ticks.of(PULL)) {
			Motion.brake(caster);
			caster.resetFallDistance();
			Fx.particle(caster.level(), Fx.dust(SKY, 1.1F), caster.position().add(0, 1, 0), 14, 0.3, 0.4, 0.3, 0);
			return false;
		}
		// 남은 거리를 남은 시간에 나눠 곧장 당깁니다 (시간 단위당 칸 → 틱당)
		double remain = Math.max(1, Ticks.of(PULL) - t + 1);
		Vec3 step = delta.scale(1.0 / remain);
		caster.setDeltaMovement(step);
		caster.hurtMarked = true;
		caster.resetFallDistance();
		Fx.particle(caster.level(), ParticleTypes.END_ROD, caster.position().add(0, 0.9, 0), 2, 0.2, 0.2, 0.2, 0.01);
		return true;
	}

	/** 와이어 끊기 — 제동하지 않으므로 마지막으로 실은 속도로 계속 날아갑니다. */
	private void snap() {
		caster.resetFallDistance();
		Fx.particle(caster.level(), Fx.dust(SKY, 0.9F), caster.position().add(0, 1, 0), 10, 0.25, 0.3, 0.25, 0);
		Fx.particle(caster.level(), ParticleTypes.END_ROD, caster.position().add(0, 1, 0), 4, 0.15, 0.2, 0.15, 0.05);
		Fx.sound(caster, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.9F, 1.5F);
	}

	@Override
	public void cancel() {
	}

	@Override
	public Object owner() {
		return caster;
	}
}
