package kr.overbreak.classes.ironfist;

import kr.overbreak.core.tick.Ticks;
import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.combat.DamageModifiers;
import kr.overbreak.core.Attachments;
import kr.overbreak.net.InputModePayload;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * [웅크리기] 파워 블록 — 데이터팩 skill/block/* + 플러그인 정면 방어 대응 (체력 10배 기준).
 *
 *   최대 2초. 오른손 건틀릿을 앞으로 세워 정면 90도에서 오는 피해를 70% 깎음 (옆 · 뒤는 그대로)
 *   지속 중 이동속도 -35%, 평타 · 스킬 불가. 다시 웅크리면 바로 해제. 군중 제어는 못 막고 기절하면 즉시 풀림
 *   60 이상 막아 내면 다음 로켓 펀치가 강화됨 (5초 안에 써야 함)
 *   쿨타임 6초 (풀린 순간부터)
 */
final class PowerBlock implements Effects.Active {
	static final int DURATION = 40;
	static final int COOLDOWN = 120;
	static final float THRESHOLD = 60.0F;
	static final int EMPOWER = 100;
	/** 정면 피해를 전부 막습니다 (0.1 버전). */
	static final float REDUCE = 1.0F;
	static final double FRONT_COS = Math.cos(Math.toRadians(45.0));
	private static final Identifier SLOW = Overbreak.id("if_block");

	private final ServerPlayer caster;
	private final IronFistState state;
	private int t = Ticks.of(DURATION);
	float blocked;

	private PowerBlock(ServerPlayer caster, IronFistState state) {
		this.caster = caster;
		this.state = state;
	}

	static void init() {
		DamageModifiers.register(PowerBlock::modify);
	}

	static void cast(ServerPlayer p, IronFistState st) {
		if (st.block != null || Cooldowns.blocked(p, IronFist.BLOCK, "파워 블록", ChatFormatting.AQUA)) {
			return;
		}
		PowerBlock b = new PowerBlock(p, st);
		st.block = b;
		Attachments.combatant(p).casting = true;
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, SLOW, -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.IF_BLOCK, -1);
		Fx.sound(p, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 0.7F);
		Fx.sound(p, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.8F, 1.6F);
		Effects.add(b);
	}

	/** 막아 낸 양 0~100 (HUD, 60 = 100). */
	int guardPercent() {
		return Math.min(100, Math.round(blocked * 100.0F / THRESHOLD));
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		if (Attachments.combatant(caster).hardCc()) {
			end();
			return false;
		}
		t--;
		if (!InputModePayload.canSend(caster)) {
			Attachments.profile(caster).msgT = 2;
			int bar = Math.min(10, Math.round(blocked * 10.0F / THRESHOLD));
			Hud.actionbar(caster, Component.empty()
					.append(Hud.bold("파워 블록  ", ChatFormatting.GRAY))
					.append(Hud.bold(Hud.bar10(bar), bar >= 10 ? ChatFormatting.GOLD : ChatFormatting.GRAY)));
		}
		if (t <= 0) {
			end();
			return false;
		}
		return true;
	}

	/** 해제 — 쿨타임은 여기서부터. 같은 틱의 목록 제거는 tick 이 false 를 돌려주거나 다음 틱에 됩니다. */
	void end() {
		if (state.block != this) {
			return;
		}
		cleanup();
		Attachments.profile(caster).setCooldown(IronFist.BLOCK, COOLDOWN);
		Fx.sound(caster, SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.7F, 1.4F);
		if (blocked >= THRESHOLD) {
			state.empowerT = Ticks.of(EMPOWER);
			// 충전에 성공하면 로켓 펀치를 바로 다시 (0.1 버전)
			Attachments.profile(caster).setCooldown(IronFist.PUNCH, 0);
			Attachments.profile(caster).msgT = 40;
			Hud.title(caster, Hud.bold("강화", ChatFormatting.GOLD), Component.empty(), 0, 20, 8);
			Fx.sound(caster, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 1.2F);
		}
	}

	private void cleanup() {
		CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, SLOW);
		Attachments.combatant(caster).casting = false;
		SkillAnimPayload.stop(caster, SkillAnimPayload.IF_BLOCK);
		if (state.block == this) {
			state.block = null;
		}
	}

	@Override
	public void cancel() {
		cleanup();
	}

	@Override
	public @Nullable Object owner() {
		return caster;
	}

	/** 정면 90도에서 온 피해 70% 감소. 출처 위치가 없는 피해(낙하 · 화염 등)는 그대로. */
	private static float modify(LivingEntity target, DamageSource source, float damage) {
		if (!(target instanceof ServerPlayer p) || damage <= 0.0F) {
			return damage;
		}
		IronFistState st = IronFist.stateOrNull(p);
		if (st == null || st.block == null) {
			return damage;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return damage;
		}
		double tx = from.x - p.getX();
		double tz = from.z - p.getZ();
		double len = Math.sqrt(tx * tx + tz * tz);
		if (len > 1.0E-3) {
			double yaw = Math.toRadians(p.getYRot());
			double dot = (tx * -Math.sin(yaw) + tz * Math.cos(yaw)) / len;
			if (dot < FRONT_COS) {
				return damage;
			}
		}
		float cut = damage * REDUCE;
		st.block.blocked += cut;
		Fx.sound(p, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9F, 1.3F);
		return damage - cut;
	}
}
