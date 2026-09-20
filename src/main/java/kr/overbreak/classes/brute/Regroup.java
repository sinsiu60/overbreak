package kr.overbreak.classes.brute;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Effects;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * [E] 전열 재정비 — 왼손에 쥔 강화 포션을 단숨에 들이켭니다 (0.2a).
 *
 *   {@value #DURATION} (1/20초 단위 = 1초) 동안 채널링. 걸을 수는 있지만 이동속도 -30%, 평타 · 다른 스킬 불가
 *   마시는 동안 받는 피해 {@value #GUARD_PERCENT}% 감소 — 맞으면서도 버틸 수 있게 (0.1f)
 *   다 마시면 빈 병을 발밑에 내던져 깨뜨립니다 (끊겨도 병은 깨집니다 — 마시다 만 것도 버립니다)
 *   끝까지 버티면 체력 {@value #HEAL} 회복 + 투기 {@value #FERVOR} 스택
 *   중간에 기절 · 넘어지면 끊기고, 그때까지 찬 만큼만 회복합니다 (쿨타임은 그대로)
 *   쿨타임 12초 (누른 순간부터)
 */
final class Regroup implements Effects.Active {
	static final int COOLDOWN = 240;
	static final int DURATION = 20;
	/** 끝까지 버텼을 때 회복량. */
	static final int HEAL = 60;
	static final int FERVOR = 5;
	static final double SLOW = 0.3;
	/** 채널링 중 받는 피해 감소 (%). */
	static final int GUARD_PERCENT = 40;

	private static final Identifier SLOW_ID = Overbreak.id("brute_regroup_slow");
	private static final int GREEN = Fx.rgb(0.45, 0.95, 0.45);
	/** 왼손에 쥐는 강화 포션 (3D 모델 overbreak:brute_potion). */
	private static final String POTION_MODEL = "overbreak:brute_potion";

	private final ServerPlayer caster;
	private final BruteState state;
	private int t;
	private float healed;

	private Regroup(ServerPlayer caster, BruteState state) {
		this.caster = caster;
		this.state = state;
	}

	/** 숨을 고르는 동안인가 (Brute 가 DamageModifier 에서 봅니다). */
	static boolean guarding(BruteState st) {
		return st.regroup != null;
	}

	static void cast(ServerPlayer p, BruteState st) {
		Attachments.profile(p).setCooldown(Brute.REGROUP, COOLDOWN);
		Regroup r = new Regroup(p, st);
		st.regroup = r;
		Attachments.combatant(p).casting = true;
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, SLOW_ID, -SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		holdPotion(p);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.BR_REGROUP, -1);
		Fx.sound(p, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.3F);
		Fx.sound(p, SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.9F, 0.7F);
		// 화면 가장자리 초록 — 마시는 동안 내내
		kr.overbreak.net.HealPayload.send(p, DURATION);
		Attachments.profile(p).msgT = 25;
		Hud.actionbar(p, Component.empty().append(Hud.bold("전열 재정비", ChatFormatting.GREEN))
				.append(Hud.text("  강화 포션을 들이키는 중...  받는 피해 -40%", ChatFormatting.GRAY)));
		Effects.add(r);
	}

	/** 채널링 진행도 0~100 (조준점 아래 게이지). */
	int percent() {
		return Math.min(100, t * 100 / Math.max(1, Ticks.of(DURATION)));
	}

	@Override
	public boolean tick() {
		if (!caster.isAlive() || caster.isRemoved() || caster.hasDisconnected()) {
			cleanup();
			return false;
		}
		Combatant c = Attachments.combatant(caster);
		if (c.stunT > 0 || c.knockT > 0 || c.breakT > 0) {
			interrupt();
			return false;
		}
		t++;
		int total = Ticks.of(DURATION);
		// 회복은 틱마다 조금씩 — 끊겨도 찬 만큼은 남습니다
		float step = HEAL / (float) total;
		caster.heal(step);
		healed += step;
		if (Ticks.ambient() && caster.level() instanceof ServerLevel level) {
			ServerPlayer self = SkillAnimPayload.canSend(caster) ? caster : null;
			Fx.particleExcept(level, self, Fx.dust(GREEN, 1.0F), caster.getX(), caster.getY() + 1.0, caster.getZ(), 4, 0.35, 0.5, 0.35, 0);
			Fx.particleExcept(level, self, ParticleTypes.HAPPY_VILLAGER, caster.getX(), caster.getY() + 1.0, caster.getZ(), 1, 0.3, 0.5, 0.3, 0);
		}
		if (t >= total) {
			finish();
			return false;
		}
		return true;
	}

	/** 왼손에 포션을 쥐여 줍니다 (원래 들고 있던 것은 없습니다 — 투귀는 왼손을 비워 둡니다). */
	private static void holdPotion(ServerPlayer p) {
		p.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND,
				kr.overbreak.item.SkillItems.prop(POTION_MODEL,
						kr.overbreak.item.SkillItems.name("강화 포션", ChatFormatting.GREEN)));
		p.containerMenu.sendAllDataToRemote();
	}

	/** 다 마셨든 끊겼든 빈 병은 발밑에 내던져 깨집니다. */
	private void dropBottle() {
		caster.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND,
				net.minecraft.world.item.ItemStack.EMPTY);
		caster.containerMenu.sendAllDataToRemote();
		if (!(caster.level() instanceof ServerLevel level)) {
			return;
		}
		double x = caster.getX();
		double y = caster.getY() + 0.1;
		double z = caster.getZ();
		Fx.sound(caster, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.9F, 1.2F);
		Fx.sound(caster, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
		Fx.particle(level, new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM,
				net.minecraft.world.item.Items.GLASS_BOTTLE), x, y, z, 12, 0.2, 0.05, 0.2, 0.15);
		Fx.particle(level, Fx.dust(GREEN, 1.1F), x, y, z, 18, 0.35, 0.05, 0.35, 0.02);
		Fx.particle(level, ParticleTypes.SPLASH, x, y, z, 10, 0.3, 0.05, 0.3, 0.1);
	}

	private void finish() {
		dropBottle();
		Fervor.gain(caster, state, FERVOR);
		Fx.sound(caster, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
		Attachments.profile(caster).msgT = 35;
		Hud.actionbar(caster, Component.empty().append(Hud.bold("전열 재정비", ChatFormatting.GREEN))
				.append(Hud.text("   체력 +" + Math.round(healed), ChatFormatting.GREEN))
				.append(Hud.text("   투기 +" + FERVOR, ChatFormatting.GOLD)));
		cleanup();
	}

	private void interrupt() {
		dropBottle();
		SkillAnimPayload.stop(caster, SkillAnimPayload.BR_REGROUP);
		Fx.sound(caster, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.8F, 0.5F);
		Attachments.profile(caster).msgT = 30;
		Hud.actionbar(caster, Component.empty().append(Hud.text("재정비가 끊겼다   ", ChatFormatting.GRAY))
				.append(Hud.text("체력 +" + Math.round(healed), ChatFormatting.DARK_GREEN)));
		cleanup();
	}

	@Override
	public void cancel() {
		cleanup();
	}

	@Override
	public Object owner() {
		return caster;
	}

	private void cleanup() {
		// 죽거나 나가서 끝났을 때도 손에 병이 남지 않게
		if (!caster.getOffhandItem().isEmpty()) {
			caster.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND,
					net.minecraft.world.item.ItemStack.EMPTY);
		}
		CrowdControl.unmod(caster, Attributes.MOVEMENT_SPEED, SLOW_ID);
		Attachments.combatant(caster).casting = false;
		if (state.regroup == this) {
			state.regroup = null;
		}
	}
}
