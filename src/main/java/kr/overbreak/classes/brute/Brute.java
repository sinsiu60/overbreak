package kr.overbreak.classes.brute;

import java.util.List;
import java.util.Map;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.SkillInfo;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.combat.DamageModifiers;
import kr.overbreak.item.SkillItems;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.skill.HudExtra;
import kr.overbreak.skill.SkillSlot;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.Nullable;

/**
 * 직업 8 · 투귀 (근접 브루저 — 오래 붙어 싸울수록 강해지는 형)
 *
 *   체력 240 (+40) · 공격력 45 (대검 +44) · 공격속도 1.2 · 이동속도 +3% · 평타 넉백 없음
 *
 * 워리어가 한 번에 몰아치고 빠진다면, 투귀는 맞으면서 스택을 쌓아 버티는 쪽입니다.
 * 붙어 있는 시간이 길수록 단단해지고(투기), 스스로 체력을 되돌리며(전열 재정비) 자리를 지킵니다.
 */
public final class Brute implements PvpClass {
	public static final String ID = "brute";
	static final String BLOW = "br_blow";
	static final String WHIRL = "br_whirl";
	static final String REGROUP = "br_regroup";
	private static final Map<String, Integer> TOTALS =
			Map.of(BLOW, HeavyBlow.COOLDOWN, WHIRL, Whirl.COOLDOWN, REGROUP, Regroup.COOLDOWN);

	private static final Identifier SWORD_DAMAGE = Overbreak.id("brute_sword_damage");
	private static final Identifier RAMPAGE_SPEED = Overbreak.id("brute_rampage_speed");

	/** 무쌍 (궁극기) — 지속 · 받는 피해 감소 · 평타 강화. */
	static final int RAMPAGE_TIME = 120;
	static final int RAMPAGE_GUARD_PERCENT = 40;
	static final double RAMPAGE_SPEED_RATIO = 0.25;
	static final int RAMPAGE_DAMAGE_MUL = 150;
	static final double RAMPAGE_RANGE = 6.4;
	static final int RAMPAGE_LIFESTEAL = 15;

	static {
		Fervor.init();
		// 돌개바람으로 도는 동안 · 무쌍 중에는 받는 피해가 줄어듭니다
		DamageModifiers.register((target, source, damage) -> {
			if (!(target instanceof ServerPlayer p)) {
				return damage;
			}
			BruteState st = stateOrNull(p);
			if (st == null) {
				return damage;
			}
			float out = damage;
			if (Whirl.guarding(st)) {
				out = out * (100 - Whirl.GUARD_PERCENT) / 100.0F;
			}
			if (Regroup.guarding(st)) {
				out = out * (100 - Regroup.GUARD_PERCENT) / 100.0F;
			}
			if (st.rampageT > 0) {
				out = out * (100 - RAMPAGE_GUARD_PERCENT) / 100.0F;
			}
			return out;
		});
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("투귀", ChatFormatting.GOLD);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new BruteState();
		Classes.baseStats(p, 40, 0.03, 120);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) {
			p.sendSystemMessage(Component.empty()
					.append(Hud.text("[직업] ", ChatFormatting.GOLD))
					.append(displayName())
					.append(Hud.text(" 를 선택했습니다.", ChatFormatting.WHITE)));
		}
		Fx.sound(p, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	@Override
	public void remove(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
		BruteState st = stateOrNull(p);
		if (st != null) {
			Fervor.clear(p, st);
			st.rampageT = 0;
		}
		CrowdControl.unmod(p, Attributes.MOVEMENT_SPEED, RAMPAGE_SPEED);
		Attachments.combatant(p).dmgMul = 100;
	}

	@Override
	public void giveItems(ServerPlayer p) {
		p.getInventory().setItem(0, sword());
		p.getInventory().setItem(9, SkillItems.statSheet(displayName(), SkillItems.lore()
				.line("근접 브루저", ChatFormatting.DARK_GRAY).blank()
				.line("체력      240  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    45", ChatFormatting.GRAY)
				.line("공격속도  1.2  (1초에 1.2회)", ChatFormatting.GRAY)
				.line("이동속도  기본 대비 +3%", ChatFormatting.GRAY).blank()
				.bold("보유 스킬", ChatFormatting.WHITE)
				.line(" 투기          (패시브)", ChatFormatting.GOLD)
				.line(" 강타          (우클릭)", ChatFormatting.GOLD)
				.line(" 돌개바람      (웅크리기)", ChatFormatting.GOLD)
				.line(" 전열 재정비  (E)", ChatFormatting.GREEN)
				.line(" 무쌍          (궁극기 · 버리기 Q)", ChatFormatting.GOLD).blank()
				.bold("궁극기 게이지", ChatFormatting.WHITE)
				.line(" 피해 10당 +2%", ChatFormatting.GRAY)
				.line(" 100% 가 되면 핫바 4번째 칸에 지급됩니다.", ChatFormatting.GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	private static ItemStack sword() {
		ItemStack s = SkillItems.skill("netherite_sword", SkillItems.name("투귀의 대검", ChatFormatting.GOLD), BLOW,
				SkillItems.lore()
						.line("직업 · 투귀", ChatFormatting.DARK_GRAY).blank()
						.line("공격력 45", ChatFormatting.GRAY)
						.line("공격속도 1.2  (1초에 1.2회 공격)", ChatFormatting.GRAY).blank()
						.bold("[패시브] 투기", ChatFormatting.GOLD)
						.line(" 때리거나 맞을 때마다 1스택 (최대 10 · 5초).", ChatFormatting.GRAY)
						.line(" 스택당 받는 피해 -2%, 이동속도 +1%.", ChatFormatting.GRAY).blank()
						.bold("[우클릭] 강타", ChatFormatting.GOLD)
						.line(" 0.3초 뒤 앞 4칸에 70 (투기당 +4, 최대 110)", ChatFormatting.GRAY)
						.line(" 과 0.6초 기절. 맞히면 투기 3스택. 쿨타임 7초", ChatFormatting.GRAY).blank()
						.bold("[웅크리기] 돌개바람", ChatFormatting.GOLD)
						.line(" 1초간 주위 3.5칸을 0.2초마다 15 (전부 75).", ChatFormatting.GRAY)
						.line(" 도는 동안 이동속도 +30% · 받는 피해 -30% · 저지불가. 쿨 8초", ChatFormatting.GRAY).blank()
						.bold("[E] 전열 재정비", ChatFormatting.GREEN)
						.line(" 1초 채널링으로 체력 60 회복 + 투기 5스택.", ChatFormatting.GRAY)
						.line(" 그동안 받는 피해 -40%. 기절하면 끊깁니다. 쿨 12초", ChatFormatting.GRAY).blank()
						.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build());
		// 대검을 들고 있을 때만 공격력 +44 (기본 1 + 44 = 45)
		s.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(SWORD_DAMAGE, 44.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.build());
		return s;
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("netherite_scrap", Hud.bold("무쌍", ChatFormatting.GOLD), SkillItems.lore()
				.line("궁극기 · 투귀", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 무쌍", ChatFormatting.GOLD)
				.line(" 6초 동안 투기가 최대로 고정됩니다.", ChatFormatting.GRAY)
				.line(" 받는 피해 -40%, 이동속도 +25%.", ChatFormatting.GRAY)
				.line(" 평타 범위가 6.4칸으로 넓어지고 피해가 1.5배.", ChatFormatting.GRAY)
				.line(" 평타로 벨 때마다 체력 15 회복.", ChatFormatting.GREEN).blank()
				.line(" 사용하면 게이지가 0% 로 돌아갑니다.", ChatFormatting.DARK_GRAY).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	@Override
	public void primary(ServerPlayer p) {
		if (Cooldowns.blocked(p, BLOW, "강타", ChatFormatting.GOLD)) {
			return;
		}
		HeavyBlow.cast(p, state(p));
	}

	@Override
	public void secondary(ServerPlayer p) {
		if (Cooldowns.blocked(p, WHIRL, "돌개바람", ChatFormatting.GOLD)) {
			return;
		}
		Whirl.cast(p, state(p));
	}

	@Override
	public void tertiary(ServerPlayer p) {
		if (Cooldowns.blocked(p, REGROUP, "전열 재정비", ChatFormatting.GREEN)) {
			return;
		}
		Regroup.cast(p, state(p));
	}

	@Override
	public void ult(ServerPlayer p) {
		UltGauge.consume(p);
		BruteState st = state(p);
		st.rampageT = Ticks.of(RAMPAGE_TIME);
		Fervor.gain(p, st, Fervor.MAX);
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, RAMPAGE_SPEED, RAMPAGE_SPEED_RATIO,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Attachments.combatant(p).dmgMul = RAMPAGE_DAMAGE_MUL;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.BR_ULT, -1);
		Hud.title(p, Hud.bold("무쌍", ChatFormatting.GOLD), Hud.text("6초 동안 아무도 널 세우지 못한다", ChatFormatting.GRAY), 0, 40, 10);
		Fx.sound(p, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2F, 0.8F);
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
		if (p.level() instanceof ServerLevel level) {
			// 본인 화면은 1인칭 포효 동작이 대신하므로 큰 폭발 입자는 뺍니다
			ServerPlayer self = SkillAnimPayload.canSend(p) ? p : null;
			Fx.particleExcept(level, self, ParticleTypes.EXPLOSION, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.5, 0.4, 0.5, 0);
			Fx.particleExcept(level, self, Fx.dust(Fx.rgb(1.00, 0.55, 0.20), 1.5F), p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.6, 0.8, 0.6, 0);
		}
	}

	@Override
	public void onMeleeHit(ServerPlayer p, LivingEntity target) {
		BruteState st = state(p);
		Fervor.gain(p, st, 1);
		if (st.rampageT > 0) {
			p.heal(RAMPAGE_LIFESTEAL);
		}
	}

	/** 맞을수록 투기가 쌓입니다. */
	@Override
	public void onDamaged(ServerPlayer p, float taken) {
		if (taken > 0.0F) {
			Fervor.gain(p, state(p), 1);
		}
	}

	/** 무쌍 중에는 평타가 두 배 멀리 닿습니다. */
	@Override
	public double meleeRange(ServerPlayer p) {
		BruteState st = stateOrNull(p);
		return st != null && st.rampageT > 0 ? RAMPAGE_RANGE : meleeRange();
	}

	/** 대검을 번갈아 대각선으로 휘두릅니다. */
	@Override
	public int basicAnim(boolean back) {
		return back ? SkillAnimPayload.BR_BASIC_BACK : SkillAnimPayload.BR_BASIC;
	}

	@Override
	public boolean meleeKnockback() {
		return false;
	}

	@Override
	public boolean meleeClearsInvulnerability() {
		return true;
	}

	@Override
	public double meleeRange() {
		return 3.2;
	}

	@Override
	public double meleeArcDegrees() {
		return 100.0;
	}

	// ── 매 틱 ───────────────────────────────────────────────

	@Override
	public void tick(ServerPlayer p) {
		BruteState st = state(p);
		Cooldowns.tick(p, TOTALS);
		if (st.rampageT > 0 && --st.rampageT <= 0) {
			endRampage(p, st);
		}
		Fervor.tick(p, st);
	}

	private static void endRampage(ServerPlayer p, BruteState st) {
		st.rampageT = 0;
		CrowdControl.unmod(p, Attributes.MOVEMENT_SPEED, RAMPAGE_SPEED);
		Attachments.combatant(p).dmgMul = 100;
		Attachments.profile(p).msgT = 40;
		Hud.actionbar(p, Hud.text("무쌍이 끝났다", ChatFormatting.GRAY));
	}

	@Override
	public List<SkillSlot> hudSlots(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		BruteState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(BLOW), HeavyBlow.COOLDOWN, st.blow != null),
				new SkillSlot(prof.cooldown(WHIRL), Whirl.COOLDOWN, st.whirl != null),
				new SkillSlot(prof.cooldown(REGROUP), Regroup.COOLDOWN, st.regroup != null));
	}

	@Override
	public HudExtra hudExtra(ServerPlayer p) {
		BruteState st = state(p);
		int meter = -1;
		int kind = 0;
		if (st.regroup != null) {
			meter = st.regroup.percent();
			kind = HudExtra.METER_CHARGE;
		}
		return new HudExtra(-1, 0, meter, kind, st.fervor, Fervor.MAX,
				st.rampageT > 0 ? HudExtra.FLAG_HASTE : 0);
	}

	// ── 설명 ────────────────────────────────────────────────

	private static final ClassInfo INFO = new ClassInfo("투귀", "근접 브루저", 0xFFA24A,
			"붙어서 오래 싸울수록 단단해지는 근접 브루저. 투기를 쌓아 버티고, 숨을 골라 다시 달려듭니다.",
			List.of(
					SkillInfo.stat("체력", "240"),
					SkillInfo.stat("공격력", "45"),
					SkillInfo.stat("공격속도", "초당 1.2회"),
					SkillInfo.stat("이동속도", "기본 대비 +3%")),
			List.of(
					new SkillInfo("패시브", "투기", null, "minecraft:blaze_powder",
							"때리거나 맞을 때마다 단단해지고 빨라짐",
							List.of(
									SkillInfo.stat("분류", "패시브 · 자버프 · 스택"),
									SkillInfo.stat("쌓이는 조건", "적을 때리거나 피해를 받을 때"),
									SkillInfo.stat("최대 스택", "10"),
									SkillInfo.stat("유지", "마지막 스택에서 5초"),
									SkillInfo.stat("스택당 받는 피해", "-2% (최대 -20%)"),
									SkillInfo.stat("스택당 이동속도", "+1% (최대 +10%)"),
									SkillInfo.stat("표시", "조준점 아래 칸")), false),
					new SkillInfo("LMB", "대검 휩쓸기", null, "minecraft:netherite_sword",
							"앞의 넓은 범위를 베어 여러 적 공격",
							List.of(
									SkillInfo.stat("분류", "근접 · 광역"),
									SkillInfo.stat("피해", "45 (공격력)"),
									SkillInfo.stat("공격속도", "초당 1.2회"),
									SkillInfo.stat("사거리", "3.2칸"),
									SkillInfo.stat("범위", "앞 100도 부채꼴"),
									SkillInfo.stat("넉백", "없음"),
									SkillInfo.stat("추가 효과", "투기 1스택")), false),
					new SkillInfo("RMB", "강타", Overbreak.id("hud/skill/brute_blow"), null,
							"대검을 내리찍어 기절시킴",
							List.of(
									SkillInfo.stat("분류", "근접 · 광역 · 군중제어 · 채널링"),
									SkillInfo.stat("준비 시간", "0.3초 (기절 시 끊김)"),
									SkillInfo.stat("범위", "앞 120도 · 4칸"),
									SkillInfo.stat("피해", "70 + 투기당 4 (최대 110)"),
									SkillInfo.stat("기절", "0.6초"),
									SkillInfo.stat("적중 시", "투기 3스택"),
									SkillInfo.stat("재사용 대기시간", "7초")), false),
					new SkillInfo("SHIFT", "돌개바람", Overbreak.id("hud/skill/brute_whirl"), null,
							"휘돌며 주위를 계속 벰",
							List.of(
									SkillInfo.stat("분류", "근접 · 광역 · 저지불가"),
									SkillInfo.stat("지속시간", "1초"),
									SkillInfo.stat("범위", "주위 반경 3.5칸"),
									SkillInfo.stat("피해", "0.2초마다 15 (전부 75)"),
									SkillInfo.stat("받는 피해", "-30%"),
									SkillInfo.stat("이동속도", "+30% (휘돌며 파고듦)"),
									SkillInfo.stat("적중 시", "벨 때마다 투기 1스택"),
									SkillInfo.stat("재사용 대기시간", "8초")), false),
					new SkillInfo("E", "전열 재정비", Overbreak.id("hud/skill/brute_regroup"), null,
							"숨을 골라 체력을 되돌림",
							List.of(
									SkillInfo.stat("분류", "회복 · 채널링"),
									SkillInfo.stat("채널링", "1초 (걸을 수 있음)"),
									SkillInfo.stat("회복", "체력 60"),
									SkillInfo.stat("투기", "5스택"),
									SkillInfo.stat("받는 피해", "-40% (채널링 동안)"),
									SkillInfo.stat("이동속도", "-30%"),
									SkillInfo.stat("끊기면", "찬 만큼만 회복 (쿨타임은 그대로)"),
									SkillInfo.stat("재사용 대기시간", "12초")), false),
					new SkillInfo("Q", "무쌍", Overbreak.id("hud/skill/brute_ult"), null,
							"투기를 최대로 고정하고 몰아붙임",
							List.of(
									SkillInfo.stat("분류", "자버프 · 흡혈"),
									SkillInfo.stat("지속시간", "6초"),
									SkillInfo.stat("투기", "최대치 고정"),
									SkillInfo.stat("받는 피해", "-40%"),
									SkillInfo.stat("이동속도", "+25%"),
									SkillInfo.stat("평타 사거리", "3.2칸 → 6.4칸"),
									SkillInfo.stat("피해", "+50% (스킬 포함)"),
									SkillInfo.stat("평타 적중", "체력 15 회복"),
									SkillInfo.stat("충전", "피해 10당 2%")), true)));

	@Override
	public ClassInfo info() {
		return INFO;
	}

	static BruteState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof BruteState)) {
			prof.classState = new BruteState();
		}
		return prof.state();
	}

	/** 투귀가 아니면 null. */
	static @Nullable BruteState stateOrNull(ServerPlayer p) {
		return Attachments.profile(p).classState instanceof BruteState st ? st : null;
	}

	/** 시험용: 지금 쌓인 투기 스택. */
	public static int fervor(ServerPlayer p) {
		BruteState st = stateOrNull(p);
		return st == null ? 0 : st.fervor;
	}

	/** 시험용: 무쌍이 도는가. */
	public static boolean rampaging(ServerPlayer p) {
		BruteState st = stateOrNull(p);
		return st != null && st.rampageT > 0;
	}

	public static List<String> skillKeys() {
		return List.of(BLOW, WHIRL, REGROUP);
	}
}
