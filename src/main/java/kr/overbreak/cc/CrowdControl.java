package kr.overbreak.cc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import kr.overbreak.Overbreak;
import kr.overbreak.combat.Motion;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 공용 군중 제어 — 데이터팩 cc/slow · cc/stun · cc/air · cc/push 대응.
 *
 * 중첩 규칙(데이터팩과 동일):
 *   기절·에어본 — 남은 시간이 새 지속시간 이상이면 줄이지 않음 (연출만 갱신)
 *   둔화      — 걸린 둔화가 새 것보다 강하면 무시, 같거나 약하면 갱신
 * 면역(ccImmune)이면 셋 다 튕겨냅니다.
 *
 * 지속시간 인자는 모두 시간 단위(1/20초 — 40 = 2초)이고, 들어오는 순간 지금 틱레이트의 틱 수로 바꿔 저장합니다 (Combatant 의 *T 는 틱).
 */
public final class CrowdControl {
	static final Identifier SLOW = Overbreak.id("cc_slow");
	static final Identifier STUN = Overbreak.id("cc_stun");
	static final Identifier AIR = Overbreak.id("cc_air");
	static final Identifier KNOCK = Overbreak.id("cc_knock");
	static final Identifier PUSH = Overbreak.id("cc_push");

	/** CC 가 걸려 있는 개체만 매 틱 돕니다. 모든 엔티티를 훑지 않습니다. */
	private static final Set<LivingEntity> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());

	private static final int SLOW_COLOR = Fx.rgb(0.35, 0.80, 1.00);
	private static final int STUN_COLOR = Fx.rgb(1.00, 0.88, 0.15);

	private CrowdControl() {}

	public static void init() {}

	// ── 부여 ────────────────────────────────────────────────

	/** @param amount 0.5 = 이동속도 50% 감소 */
	public static void slow(LivingEntity e, double amount, int time) {
		slow(e, amount, time, true);
	}

	/**
	 * @param amount 0.5 = 이동속도 50% 감소
	 * @param fx     입자를 낼 것인가. 매 틱 다시 거는 곳(광란의 포효 등)은 false — true 로 두면 입자가 틱마다 겹칩니다.
	 */
	public static void slow(LivingEntity e, double amount, int time, boolean fx) {
		int dur = Ticks.of(time);
		Combatant c = Attachments.combatant(e);
		if (immune(e, c)) {
			return;
		}
		int amt = (int) Math.round(amount * 100);
		if (c.slowT > 0 && c.slowAmt > amt) {
			return;
		}
		mod(e, Attributes.MOVEMENT_SPEED, SLOW, -amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		c.slowT = dur;
		c.slowAmt = amt;
		if (fx && e.level() instanceof ServerLevel level) {
			Fx.particle(level, Fx.dust(SLOW_COLOR, 1.1F), e.getX(), e.getY() + 0.3, e.getZ(), 8, 0.3, 0.3, 0.3, 0);
		}
		ACTIVE.add(e);
	}

	public static void stun(LivingEntity e, int time) {
		stun(e, time, true);
	}

	/**
	 * @param fx 기절 소리 · 입자를 낼 것인가. 매 틱 다시 거는 곳(사슬에 끌려오는 동안 등)은 false —
	 *           true 로 두면 소리가 틱마다 겹쳐 드드득 거립니다.
	 */
	public static void stun(LivingEntity e, int time, boolean fx) {
		int dur = Ticks.of(time);
		Combatant c = Attachments.combatant(e);
		if (immune(e, c)) {
			return;
		}
		if (c.stunT >= dur) {
			if (fx) {
				stunFx(e);
			}
			return;
		}
		if (c.stunT <= 0 && c.knockT <= 0) {
			c.ccId++;
		}
		hardMods(e, STUN);
		c.stunT = dur;
		c.stunMax = dur;
		if (fx) {
			stunFx(e);
		}
		ACTIVE.add(e);
	}

	/** 걸어 둔 기절만 풉니다 (넘어뜨림 · 에어본 · 둔화는 그대로). */
	public static void clearStun(LivingEntity e) {
		Combatant c = Attachments.combatant(e);
		if (c.stunT <= 0) {
			return;
		}
		c.stunT = 0;
		c.stunMax = 0;
		clearHardMods(e, STUN);
	}

	/**
	 * 넘어뜨림 — 기절의 상위 호환. 기절처럼 이동 · 공격 · 스킬이 모두 막히고, 모델이 실제로 뒤로 쓰러집니다.
	 * 기절보다 우선이라 기절 판정(뇌진탕 · 시전 끊김)에도 걸립니다. 남은 시간이 더 길면 줄이지 않습니다.
	 */
	public static void knockdown(LivingEntity e, int time) {
		int dur = Ticks.of(time);
		Combatant c = Attachments.combatant(e);
		if (immune(e, c)) {
			return;
		}
		if (c.knockT >= dur) {
			return;
		}
		if (c.stunT <= 0 && c.knockT <= 0) {
			c.ccId++;
		}
		hardMods(e, KNOCK);
		c.knockT = dur;
		c.knockMax = dur;
		c.dashT = 0;
		e.setDeltaMovement(0.0, Math.min(e.getDeltaMovement().y, 0.0), 0.0);
		e.hurtMarked = true;
		SkillAnimPayload.broadcast(e, SkillAnimPayload.KNOCKDOWN, -1, time);
		Fx.sound(e, SoundEvents.PLAYER_BIG_FALL, SoundSource.HOSTILE, 1.0F, 0.7F);
		Fx.sound(e, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6F, 1.4F);
		if (e instanceof ServerPlayer p) {
			Hud.title(p, Hud.bold("넘어짐!", ChatFormatting.RED), Component.empty(), 0, 10, 5);
		}
		ACTIVE.add(e);
	}

	/** 걸려 있는 기절 · 넘어뜨림을 늘립니다 (뇌진탕). 새 CC 번호를 만들지 않습니다. */
	public static void extendHard(LivingEntity e, int time) {
		int ticks = Ticks.of(time);
		Combatant c = Attachments.combatant(e);
		if (c.stunT > 0) {
			c.stunT += ticks;
			c.stunMax += ticks;
		}
		if (c.knockT > 0) {
			c.knockT += ticks;
			c.knockMax += ticks;
			SkillAnimPayload.broadcast(e, SkillAnimPayload.KNOCKDOWN, -1, Ticks.toTime(c.knockT));
		}
	}

	/** @param amp 부양 증폭 (0 = 초당 약 0.9칸, 1 = 1.8칸) */
	public static void airborne(LivingEntity e, int time, int amp) {
		int dur = Ticks.of(time);
		Combatant c = Attachments.combatant(e);
		if (immune(e, c)) {
			return;
		}
		if (c.airT >= dur) {
			return;
		}
		hardMods(e, AIR);
		c.airT = dur;
		c.airMax = dur;
		// 부양: 바닐라 공중 부양 효과 (플레이어는 클라이언트가 계산). 틱레이트 보정은 LivingEntityLevitationMixin 이 상수를 줄여서
		e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, dur + 1, amp, false, false));
		if (e.level() instanceof ServerLevel level) {
			Fx.particle(level, ParticleTypes.CLOUD, e.getX(), e.getY() + 0.2, e.getZ(), 12, 0.3, 0.1, 0.3, 0.02);
		}
		ACTIVE.add(e);
	}

	/**
	 * 밀쳐내기 — 이것도 군중 제어입니다 (밀리는 동안 평타·스킬 불가).
	 * 이동은 돌진과 같은 "매 틱 속도" 라 부드럽게 밀립니다. 제동은 걸지 않습니다.
	 */
	public static void push(LivingEntity e, double dirX, double dirZ, double distance, int time) {
		Combatant c = Attachments.combatant(e);
		if (immune(e, c)) {
			return;
		}
		Motion.dash(e, dirX, dirZ, distance / time, time, false);
		c.pushT = Ticks.of(time);
		mod(e, Attributes.ENTITY_INTERACTION_RANGE, PUSH, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ACTIVE.add(e);
	}

	/** 이동기 봉인. 더 길게 걸려 있으면 줄이지 않습니다. */
	public static void seal(LivingEntity e, int time) {
		int dur = Ticks.of(time);
		Combatant c = Attachments.combatant(e);
		c.sealT = Math.max(c.sealT, dur);
		ACTIVE.add(e);
	}

	/** 돌진처럼 CC 는 아니지만 매 틱 돌아야 하는 개체를 등록합니다. */
	public static void track(LivingEntity e) {
		ACTIVE.add(e);
	}

	// ── 매 틱 ───────────────────────────────────────────────

	public static void tick() {
		if (ACTIVE.isEmpty()) {
			return;
		}
		List<LivingEntity> snapshot = new ArrayList<>(ACTIVE);
		for (LivingEntity e : snapshot) {
			if (e.isRemoved() || e.isDeadOrDying()) {
				clearAll(e);
				ACTIVE.remove(e);
				continue;
			}
			Combatant c = Attachments.combatant(e);
			tickOne(e, c);
			if (!c.anyActive()) {
				ACTIVE.remove(e);
			}
		}
	}

	private static void tickOne(LivingEntity e, Combatant c) {
		ServerLevel level = (ServerLevel) e.level();
		// 발밑 표시 고리는 1/20초마다만 (60틱에서 파티클 패킷이 3배로 늘지 않게)
		boolean fx = Ticks.every(level.getGameTime(), 1);

		if (c.slowT > 0) {
			c.slowT--;
			if (fx) {
				Fx.ring(level, e.position(), 0.45, 8, 0.1, Fx.dust(SLOW_COLOR, 0.8F));
			}
			if (c.slowT <= 0) {
				unmod(e, Attributes.MOVEMENT_SPEED, SLOW);
				c.slowAmt = 0;
			}
		}

		if (c.stunT > 0) {
			c.stunT--;
			if (fx) {
				Fx.ring(level, e.position(), 0.50, 12, 0.1, Fx.dust(STUN_COLOR, 0.8F));
			}
			if (e instanceof ServerPlayer p) {
				int bar = c.stunMax > 0 ? c.stunT * 10 / c.stunMax : 0;
				Hud.actionbar(p, Component.empty()
						.append(Hud.bold("기절  ", ChatFormatting.YELLOW))
						.append(Hud.bold(Hud.bar10(bar), ChatFormatting.GOLD)));
				Attachments.profile(p).msgT = 2;
			}
			if (c.stunT <= 0) {
				clearHardMods(e, STUN);
				if (e instanceof ServerPlayer p) {
					Attachments.profile(p).msgT = 40;
					Hud.actionbar(p, Hud.text("기절 해제", ChatFormatting.GRAY));
					Fx.sound(p, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.6F);
				}
			}
		}

		if (c.knockT > 0) {
			c.knockT--;
			if (e instanceof ServerPlayer p) {
				int bar = c.knockMax > 0 ? c.knockT * 10 / c.knockMax : 0;
				Hud.actionbar(p, Component.empty()
						.append(Hud.bold("넘어짐  ", ChatFormatting.RED))
						.append(Hud.bold(Hud.bar10(bar), ChatFormatting.GOLD)));
				Attachments.profile(p).msgT = 2;
			}
			if (c.knockT <= 0) {
				clearHardMods(e, KNOCK);
				SkillAnimPayload.stop(e, SkillAnimPayload.KNOCKDOWN);
				if (e instanceof ServerPlayer p) {
					Attachments.profile(p).msgT = 40;
					Hud.actionbar(p, Hud.text("일어났다", ChatFormatting.GRAY));
				}
			}
		}

		if (c.airT > 0) {
			c.airT--;
			if (fx) {
				Fx.ring(level, e.position(), 0.50, 10, 0.1, Fx.dust(0xFFFFFF, 0.8F));
			}
			if (e instanceof ServerPlayer p) {
				int bar = c.airMax > 0 ? c.airT * 10 / c.airMax : 0;
				Hud.actionbar(p, Component.empty()
						.append(Hud.bold("에어본  ", ChatFormatting.WHITE))
						.append(Hud.bold(Hud.bar10(bar), ChatFormatting.AQUA)));
				Attachments.profile(p).msgT = 2;
			}
			if (c.airT <= 0) {
				clearHardMods(e, AIR);
				e.removeEffect(MobEffects.LEVITATION);
				if (e instanceof ServerPlayer p) {
					Attachments.profile(p).msgT = 40;
					Hud.actionbar(p, Hud.text("착지", ChatFormatting.GRAY));
					Fx.sound(p, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.2F);
				}
			}
		}

		if (c.pushT > 0) {
			c.pushT--;
			if (fx) {
				Fx.particle(level, ParticleTypes.CLOUD, e.getX(), e.getY() + 0.3, e.getZ(), 2, 0.25, 0.2, 0.25, 0.02);
			}
			if (c.pushT <= 0) {
				unmod(e, Attributes.ENTITY_INTERACTION_RANGE, PUSH);
				c.dashT = 0;
			}
		}

		if (c.sealT > 0) {
			c.sealT--;
		}
		if (c.breakT > 0) {
			c.breakT--;
		}

		Motion.tick(e, c);
	}

	/** 사망 · 직업 해제 · 초기화 때 전부 풉니다. */
	public static void clearAll(LivingEntity e) {
		Combatant c = Attachments.combatant(e);
		unmod(e, Attributes.MOVEMENT_SPEED, SLOW);
		clearHardMods(e, STUN);
		clearHardMods(e, AIR);
		clearHardMods(e, KNOCK);
		if (c.knockT > 0) {
			SkillAnimPayload.stop(e, SkillAnimPayload.KNOCKDOWN);
		}
		unmod(e, Attributes.ENTITY_INTERACTION_RANGE, PUSH);
		if (c.airT > 0) {
			e.removeEffect(MobEffects.LEVITATION);
		}
		c.slowT = c.slowAmt = c.stunT = c.stunMax = c.knockT = c.knockMax = c.airT = c.airMax = c.pushT = c.sealT = 0;
		c.dashT = 0;
		c.breakT = 0;
	}

	/**
	 * 걸려 있는 기절 · 넘어뜨림 · 에어본 · 둔화를 풉니다 (저지불가 시작). 봉인 · 밀쳐내기는 그대로 둡니다.
	 * 데이터팩 cc/stun/end · cc/air/end · cc/slow/end 를 한 번에.
	 */
	public static void cleanse(LivingEntity e) {
		Combatant c = Attachments.combatant(e);
		unmod(e, Attributes.MOVEMENT_SPEED, SLOW);
		clearHardMods(e, STUN);
		clearHardMods(e, AIR);
		clearHardMods(e, KNOCK);
		if (c.knockT > 0) {
			SkillAnimPayload.stop(e, SkillAnimPayload.KNOCKDOWN);
		}
		if (c.airT > 0) {
			e.removeEffect(MobEffects.LEVITATION);
		}
		c.slowT = c.slowAmt = c.stunT = c.stunMax = c.knockT = c.knockMax = c.airT = c.airMax = 0;
	}

	// ── 내부 ────────────────────────────────────────────────

	private static boolean immune(LivingEntity e, Combatant c) {
		if (!c.ccImmune) {
			return false;
		}
		if (e.level() instanceof ServerLevel level) {
			Fx.particle(level, ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.4, 0.3, 0.2);
			Fx.sound(e, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8F, 1.4F);
		}
		return true;
	}

	private static void stunFx(LivingEntity e) {
		if (!(e.level() instanceof ServerLevel level)) {
			return;
		}
		Fx.sound(e, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.7F, 1.8F);
		Fx.sound(e, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 0.5F, 2.0F);
		Fx.particle(level, Fx.dust(STUN_COLOR, 1.2F), e.getX(), e.getY() + 1, e.getZ(), 20, 0.35, 0.4, 0.35, 0);
		if (e instanceof ServerPlayer p) {
			Hud.title(p, Hud.bold("기절!", ChatFormatting.YELLOW), Component.empty(), 0, 10, 5);
		}
	}

	/** 기절·에어본: 이동·점프·공격 피해·공격 사거리 전부 0. 출처마다 ID 가 달라 겹쳐도 안전합니다. */
	private static void hardMods(LivingEntity e, Identifier id) {
		AttributeModifier.Operation total = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
		mod(e, Attributes.MOVEMENT_SPEED, id, -1, total);
		mod(e, Attributes.JUMP_STRENGTH, id, -1, total);
		mod(e, Attributes.ATTACK_DAMAGE, id, -1, total);
		mod(e, Attributes.ENTITY_INTERACTION_RANGE, id, -1, total);
	}

	private static void clearHardMods(LivingEntity e, Identifier id) {
		unmod(e, Attributes.MOVEMENT_SPEED, id);
		unmod(e, Attributes.JUMP_STRENGTH, id);
		unmod(e, Attributes.ATTACK_DAMAGE, id);
		unmod(e, Attributes.ENTITY_INTERACTION_RANGE, id);
	}

	public static void mod(LivingEntity e, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = e.getAttribute(attr);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		inst.addTransientModifier(new AttributeModifier(id, amount, op));
	}

	public static void unmod(LivingEntity e, Holder<Attribute> attr, Identifier id) {
		AttributeInstance inst = e.getAttribute(attr);
		if (inst != null) {
			inst.removeModifier(id);
		}
	}
}
