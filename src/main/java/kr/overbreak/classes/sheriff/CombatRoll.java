package kr.overbreak.classes.sheriff;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.Motion;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * [웅크리기] 전술 구르기 — 데이터팩 skill/sheriff/roll/* 대응.
 *
 *   누르고 있던 방향키 쪽으로 0.3초(6틱) 동안 틱당 0.67칸 = 약 4칸. 아무 키도 안 누르면 바라보는 쪽
 *     (데이터팩은 키를 못 읽어 지난 틱에 움직인 자취를 썼지만, 모드는 서버가 받은 실제 방향키 상태를 씁니다)
 *   구르는 0.3초 동안 받는 피해 50% 감소 · 리볼버 난사 쿨타임 즉시 초기화
 *   앞이 벽이면 그 자리에서 멈춤. 이동기라 균열 지대 위에서는 막히고 쿨타임도 먹지 않음. 쿨타임 8초
 */
final class CombatRoll implements Effects.Active {
	static final int COOLDOWN = 120;
	static final int TICKS = 6;
	static final double SPEED = 0.67;
	static final int GUARD = 6;
	private static final int GOLD = Fx.rgb(0.95, 0.75, 0.30);

	private final ServerPlayer caster;
	private final SheriffState state;
	private int t;

	private CombatRoll(ServerPlayer caster, SheriffState state) {
		this.caster = caster;
		this.state = state;
	}

	static void cast(ServerPlayer p, SheriffState st) {
		if (Attachments.combatant(p).sealT > 0) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
			Attachments.profile(p).msgT = 30;
			Hud.actionbar(p, Hud.text("균열 지대 위에서는 이동기를 쓸 수 없다", ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (st.roll != null || Cooldowns.blocked(p, Sheriff.ROLL, "전술 구르기", ChatFormatting.GOLD)) {
			return;
		}
		Attachments.profile(p).setCooldown(Sheriff.ROLL, COOLDOWN);
		Vec3 dir = inputDirection(p);
		Motion.dash(p, dir.x, dir.z, SPEED, TICKS, false);
		// 돌진 속도는 CrowdControl.tick 이 매 틱 실어 줍니다 — 목록에 올려야 실제로 움직임
		CrowdControl.track(p);
		st.guardT = Ticks.of(GUARD);
		// 구르면 탄창이 가득 찹니다 (0.1 버전)
		st.ammo = Peacekeeper.MAG;
		st.reloadT = 0;
		CombatRoll r = new CombatRoll(p, st);
		st.roll = r;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.SH_ROLL, -1);
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.8F);
		Fx.sound(p, SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 1.2F, 0.8F);
		Effects.add(r);
	}

	/** 누르고 있는 방향키 → 수평 방향 (단위 벡터). 안 누르면 바라보는 쪽. */
	static Vec3 inputDirection(ServerPlayer p) {
		Input in = p.getLastClientInput();
		double f = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
		double s = (in.left() ? 1 : 0) - (in.right() ? 1 : 0);
		if (f == 0 && s == 0) {
			return Motion.flatLook(p);
		}
		double yaw = Math.toRadians(p.getYRot());
		Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
		return forward.scale(f).add(left.scale(s)).normalize();
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			end(false);
			return false;
		}
		t++;
		if (Ticks.ambient()) {
			Fx.particle(caster.level(), Fx.dust(GOLD, 1.1F), caster.getX(), caster.getY() + 0.4, caster.getZ(), 6, 0.25, 0.3, 0.25, 0);
		}
		if (t >= Ticks.of(TICKS) || (t > Ticks.of(1) && caster.horizontalCollision)) {
			end(true);
			return false;
		}
		return true;
	}

	private void end(boolean brake) {
		if (state.roll == this) {
			state.roll = null;
		}
		if (brake) {
			Motion.brake(caster);
		}
	}

	@Override
	public void cancel() {
		end(false);
	}

	@Override
	public Object owner() {
		return caster;
	}
}
