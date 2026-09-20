package kr.overbreak.classes.warrior;

import kr.overbreak.core.tick.Ticks;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.SkillInfo;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.item.SkillItems;
import kr.overbreak.net.SkillAnimPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.skill.SkillSlot;
import kr.overbreak.ult.UltGauge;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Targets;
import net.minecraft.ChatFormatting;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 직업 1 · 워리어 (근접 돌격형) — 데이터팩 class/warrior + skill/slay · fury · chain · bleed + ult/warrior.
 *
 *   체력 275 (+75) · 공격력 55 (도끼 +54) · 공격속도 1.0 · 이동속도 +7%
 */
public final class Warrior implements PvpClass {
	public static final String ID = "warrior";
	static final String SLAY = "wr_slay";
	static final String FURY = "wr_fury";
	static final String CHAIN = "wr_chain";
	private static final Map<String, Integer> TOTALS = Map.of(SLAY, 100, FURY, 200, CHAIN, 160);
	/** 광란의 포효 지속 (1/20초 단위 = 5초). */
	static final int FURY_TIME = 100;
	/** 포효가 도는 동안 둔화시키는 범위 · 세기. */
	static final double FURY_RADIUS = 3.0;
	static final double FURY_SLOW = 0.4;
	/** 포효 이동속도 증가 (0.1f 에서 50% → 30%). */
	static final double FURY_MOVE = 0.3;
	/** 포효 공격력 증가 — 평타 · 스킬 모두 (0.1f 에서 30% → 15%). */
	static final double FURY_POWER_RATIO = 0.15;

	private static final Identifier FURY_SPEED = Overbreak.id("fury_speed");
	private static final Identifier FURY_POWER = Overbreak.id("fury_power");
	private static final Identifier ULT_HASTE = Overbreak.id("ult_haste");
	private static final Identifier AXE_DAMAGE = Overbreak.id("warrior_axe_damage");

	/** 튜토리얼 등이 "살육이 적중했다" 를 알고 싶을 때. */
	public static Consumer<ServerPlayer> slayHitListener = p -> {};
	/** 튜토리얼 등이 "피의 사슬로 끌어왔다" 를 알고 싶을 때. */
	public static BiConsumer<ServerPlayer, LivingEntity> grabArriveListener = (p, e) -> {};
	/** 게임 흐름(튜토리얼)이 쓰는 고정 훅 — 시험이 위 리스너를 바꿔도 지워지지 않습니다. */
	public static final java.util.List<Consumer<ServerPlayer>> SLAY_HIT_HOOKS = new java.util.concurrent.CopyOnWriteArrayList<>();
	public static final java.util.List<BiConsumer<ServerPlayer, LivingEntity>> GRAB_ARRIVE_HOOKS = new java.util.concurrent.CopyOnWriteArrayList<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("워리어", ChatFormatting.RED);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new WarriorState();
		Classes.baseStats(p, 75, 0.07, 100);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) p.sendSystemMessage(Component.empty()
				.append(Hud.text("[직업] ", ChatFormatting.GOLD))
				.append(Hud.bold("워리어", ChatFormatting.RED))
				.append(Hud.text(" 를 선택했습니다.", ChatFormatting.WHITE)));
		Fx.sound(p, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	@Override
	public void remove(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
		endFury(p);
		endUltSelf(p);
	}

	@Override
	public void giveItems(ServerPlayer p) {
		var inv = p.getInventory();
		inv.setItem(0, axe());
		// 스킬은 오른쪽 아래 HUD 에 표시합니다. 핫바에는 무기 한 칸만.
		inv.setItem(9, SkillItems.statSheet(Hud.bold("워리어", ChatFormatting.RED), SkillItems.lore()
				.line("근접 돌격형", ChatFormatting.DARK_GRAY).blank()
				.line("체력      275  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    55", ChatFormatting.GRAY)
				.line("공격속도  1.0  (1초에 1회)", ChatFormatting.GRAY)
				.line("이동속도  기본 대비 +7%", ChatFormatting.GRAY).blank()
				.bold("보유 스킬", ChatFormatting.WHITE)
				.line(" 피의 갈망    (패시브)", ChatFormatting.DARK_RED)
				.line(" 살육          (우클릭)", ChatFormatting.RED)
				.line(" 광란의 포효  (웅크리기)", ChatFormatting.RED)
				.line(" 피의 사슬    (E)", ChatFormatting.RED)
				.line(" 광란의 처형장  (궁극기 · 버리기 Q)", ChatFormatting.GOLD).blank()
				.bold("궁극기 게이지", ChatFormatting.WHITE)
				.line(" 피해 7당 +2%, 맵 에너지 획득 시 +25%", ChatFormatting.GRAY)
				.line(" 100% 가 되면 핫바 4번째 칸에 지급됩니다.", ChatFormatting.GRAY).blank()
				.line("쿨타임은 핫바 아이콘 아래 막대로 표시됩니다.", ChatFormatting.DARK_GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	private static ItemStack axe() {
		ItemStack s = SkillItems.skill("iron_axe", SkillItems.name("워리어의 철도끼", ChatFormatting.RED), SLAY,
				SkillItems.lore()
						.line("직업 · 워리어", ChatFormatting.DARK_GRAY).blank()
						.line("공격력 55", ChatFormatting.GRAY)
						.line("공격속도 1.0  (1초에 1회 공격)", ChatFormatting.GRAY).blank()
						.bold("[패시브] 피의 갈망", ChatFormatting.DARK_RED)
						.line(" 적을 때릴 때마다 출혈 1스택을 부여합니다.", ChatFormatting.GRAY)
						.line(" 출혈이 5스택에 도달하면 폭발하여 대상에게", ChatFormatting.GRAY)
						.line(" 50의 피해를 주고 자신은 체력 50을 회복합니다.", ChatFormatting.GRAY).blank()
						.bold("[우클릭] 살육", ChatFormatting.RED)
						.line(" 0.7초간 기를 모은 뒤 도끼를 빠르게 휘둘러", ChatFormatting.GRAY)
						.line(" 주위 4.5칸의 적에게 80의 피해와 출혈 2스택.", ChatFormatting.GRAY)
						.line(" 적중한 적 1명당 체력 35를 회복합니다.", ChatFormatting.GRAY)
						.line(" 쿨타임 5초 · 출혈 폭발마다 2초 감소", ChatFormatting.DARK_GRAY).build());
		// 도끼를 들고 있을 때만 공격력 +54 (기본 1 + 54 = 55)
		s.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(AXE_DAMAGE, 54.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.build());
		return s;
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("nether_star", Hud.bold("광란의 처형장", ChatFormatting.RED), SkillItems.lore()
				.line("궁극기 · 워리어", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 광란의 처형장", ChatFormatting.RED)
				.line(" 반경 6칸에 8초간 처형장을 펼칩니다.", ChatFormatting.GRAY)
				.line(" 발동 시 범위 내 적에게 60의 피해와", ChatFormatting.GRAY)
				.line(" 1초 기절을 부여합니다.", ChatFormatting.GRAY)
				.line(" 처형장 안의 적은 사슬에 묶여 밖으로", ChatFormatting.GRAY)
				.line(" 나갈 수 없고 80% 둔화됩니다.", ChatFormatting.GRAY)
				.line(" 자신은 이동속도 +20%,", ChatFormatting.GRAY)
				.line(" 살육 쿨타임이 2배로 빨리 줄어듭니다.", ChatFormatting.GRAY).blank()
				.line(" 사용하면 게이지가 0% 로 돌아갑니다.", ChatFormatting.DARK_GRAY).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	@Override
	public void primary(ServerPlayer p) {
		if (Cooldowns.blocked(p, SLAY, "살육", ChatFormatting.RED)) {
			return;
		}
		Slay.cast(p, state(p));
	}

	@Override
	public void secondary(ServerPlayer p) {
		if (Cooldowns.blocked(p, FURY, "광란의 포효", ChatFormatting.RED)) {
			return;
		}
		PlayerProfile prof = Attachments.profile(p);
		prof.setCooldown(FURY, 200);
		WarriorState st = state(p);
		endFury(p);
		st.furyT = Ticks.of(FURY_TIME);
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, FURY_SPEED, FURY_MOVE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		// 공격력 증가 하나로 통일 — 평타(속성)와 스킬(dmgMul) 양쪽에 같은 비율로 걸립니다
		CrowdControl.mod(p, Attributes.ATTACK_DAMAGE, FURY_POWER, FURY_POWER_RATIO, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Attachments.combatant(p).dmgMul = 100 + (int) Math.round(FURY_POWER_RATIO * 100);
		SkillAnimPayload.broadcast(p, SkillAnimPayload.FURY, -1);

		ServerLevel level = (ServerLevel) p.level();
		Fx.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.6F);
		Fx.particle(level, ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.8, 0.6, 0.3);
		Fx.particle(level, ParticleTypes.ENCHANTED_HIT, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.7, 0.5, 0.2);
		prof.msgT = 40;
		Hud.actionbar(p, Component.empty()
				.append(Hud.bold("광란의 포효", ChatFormatting.RED))
				.append(Hud.text("  이동속도 +30%  공격력 +15%", ChatFormatting.GOLD)));

		furyAura(p);
	}

	@Override
	public void tertiary(ServerPlayer p) {
		if (Cooldowns.blocked(p, CHAIN, "피의 사슬", ChatFormatting.RED)) {
			return;
		}
		BloodChain.cast(p, state(p));
	}

	@Override
	public void ult(ServerPlayer p) {
		UltGauge.consume(p);
		WarriorState st = state(p);
		endUltSelf(p);
		st.ultSelfT = Ticks.of(160);
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, ULT_HASTE, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Attachments.profile(p).msgT = 60;
		SkillAnimPayload.broadcast(p, SkillAnimPayload.ULT, -1);
		Hud.title(p, Hud.bold("광란의 처형장", ChatFormatting.DARK_RED), Component.empty(), 0, 30, 10);
		ExecutionGround.cast(p);
	}

	@Override
	public void onMeleeHit(ServerPlayer p, LivingEntity target) {
		Bleed.add(target, p);
	}

	// ── 매 틱 ───────────────────────────────────────────────

	private static final ClassInfo INFO = new ClassInfo("워리어", "근접 돌격형", 0xFF5A4A,
			"출혈을 쌓아 몰아붙이고, 사슬로 끌어와 처형장에 가두는 근접 전사.",
			List.of(
					SkillInfo.stat("체력", "275"),
					SkillInfo.stat("공격력", "55"),
					SkillInfo.stat("공격속도", "초당 1.0회"),
					SkillInfo.stat("이동속도", "기본 대비 +7%")),
			List.of(
					new SkillInfo("패시브", "피의 갈망", null, "minecraft:spider_eye",
							"적중마다 출혈, 5스택에 폭발해 회복",
							List.of(
									SkillInfo.stat("분류", "패시브 · 흡혈"),
									SkillInfo.stat("출혈", "적중마다 1스택"),
									SkillInfo.stat("출혈 유지", "추가 타격 없이 8초"),
									SkillInfo.stat("폭발 조건", "5스택"),
									SkillInfo.stat("폭발 피해", "50"),
									SkillInfo.stat("폭발 시 회복", "체력 50"),
									SkillInfo.stat("폭발 시 살육", "쿨타임 2초 감소")), false),
					new SkillInfo("LMB", "도끼 베기", null, "minecraft:iron_axe",
							"앞의 넓은 범위를 베어 여러 적 공격",
							List.of(
									SkillInfo.stat("분류", "근접 · 광역"),
									SkillInfo.stat("피해", "55 (공격력)"),
									SkillInfo.stat("공격속도", "초당 1.0회"),
									SkillInfo.stat("사거리", "3칸"),
									SkillInfo.stat("범위", "앞 100도 부채꼴"),
									SkillInfo.stat("근접 판정", "1칸 안은 방향 무관"),
									SkillInfo.stat("넉백", "없음"),
									SkillInfo.stat("추가 효과", "출혈 1스택")), false),
					new SkillInfo("RMB", "살육", Overbreak.id("hud/skill/warrior_slay"), null,
							"기를 모아 주위를 베고 체력 흡수",
							List.of(
									SkillInfo.stat("분류", "근접 · 광역 · 흡혈 · 채널링"),
									SkillInfo.stat("시전 시간", "0.7초 (기절 시 끊김)"),
									SkillInfo.stat("범위", "주위 반경 4.5칸"),
									SkillInfo.stat("피해", "80"),
									SkillInfo.stat("출혈", "2스택"),
									SkillInfo.stat("회복", "적중 1명당 체력 35"),
									SkillInfo.stat("재사용 대기시간", "5초")), false),
					new SkillInfo("SHIFT", "광란의 포효", Overbreak.id("hud/skill/warrior_fury"), null,
							"주위 적 둔화, 자신의 속도·공격력 증가",
							List.of(
									SkillInfo.stat("분류", "자버프 · 군중제어"),
									SkillInfo.stat("둔화 범위", "주위 3칸"),
									SkillInfo.stat("둔화", "40% (범위 안에 있는 동안)"),
									SkillInfo.stat("지속시간", "5초"),
									SkillInfo.stat("이동속도", "+30%"),
									SkillInfo.stat("공격력", "+15% (평타 · 스킬 모두)"),
									SkillInfo.stat("재사용 대기시간", "10초")), false),
					new SkillInfo("E", "피의 사슬", Overbreak.id("hud/skill/warrior_chain"), null,
							"사슬을 돌리다 던져 적을 끌어옴",
							List.of(
									SkillInfo.stat("분류", "투사체 · 끌어오기 · 군중제어"),
									SkillInfo.stat("준비 시간", "0.4초 (기절 시 끊김)"),
									SkillInfo.stat("사거리", "15칸"),
									SkillInfo.stat("피해", "25"),
									SkillInfo.stat("출혈", "1스택"),
									SkillInfo.stat("묶기", "끌려오는 동안 계속 기절"),
									SkillInfo.stat("끌어오기 속도", "초당 23.4칸"),
									SkillInfo.stat("멈추는 위치", "자신의 앞 1.3칸"),
									SkillInfo.stat("도착 후", "둔화 50% · 2초"),
									SkillInfo.stat("끊기는 조건", "대상이 기절 · 에어본"),
									SkillInfo.stat("재사용 대기시간", "8초")), false),
					new SkillInfo("Q", "광란의 처형장", Overbreak.id("hud/skill/warrior_ult"), null,
							"케이지에 적을 가두고 탈출을 막음",
							List.of(
									SkillInfo.stat("분류", "설치 · 광역 · 군중제어 · 자버프"),
									SkillInfo.stat("범위", "반경 6칸"),
									SkillInfo.stat("지속시간", "8초"),
									SkillInfo.stat("설치 시 피해", "60"),
									SkillInfo.stat("설치 시 기절", "1초"),
									SkillInfo.stat("안쪽 둔화", "80%"),
									SkillInfo.stat("탈출 저지", "밖으로 나가면 끌려옴"),
									SkillInfo.stat("자신 이동속도", "+20%"),
									SkillInfo.stat("살육 쿨타임", "2배 빠르게 감소"),
									SkillInfo.stat("충전", "피해 16당 2%")), true)));

	/** 피해 16 당 게이지 2% (0.1f 너프). */
	@Override
	public int ultRate() {
		return 125;
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
	public ClassInfo info() {
		return INFO;
	}

	@Override
	public List<SkillSlot> hudSlots(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		WarriorState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(SLAY), TOTALS.get(SLAY), st.slay != null),
				new SkillSlot(prof.cooldown(FURY), TOTALS.get(FURY), st.furyT > 0),
				new SkillSlot(prof.cooldown(CHAIN), TOTALS.get(CHAIN), st.chain != null));
	}

	@Override
	public void tick(ServerPlayer p) {
		WarriorState st = state(p);
		PlayerProfile prof = Attachments.profile(p);

		// 처형장 자버프 중에는 살육 쿨타임이 한 번 더 줄어듭니다 (2배).
		if (st.ultSelfT > 0 && prof.cooldown(SLAY) > 0) {
			prof.setCooldownTicks(SLAY, prof.cooldown(SLAY) - 1);
		}
		Cooldowns.tick(p, TOTALS);

		if (st.furyT > 0) {
			st.furyT--;
			// 포효가 도는 동안 "지금 주위에 있는" 적을 계속 둔화 (0.1a — 처음 맞은 적만 끝까지 둔화되던 것을 고침)
			if (Ticks.ambient()) {
				furyAura(p);
			}
			if (Ticks.ambient()) Fx.particle((ServerLevel) p.level(), ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 2, 0.3, 0.6, 0.3, 0);
			if (st.furyT <= 0) {
				endFury(p);
				prof.msgT = 40;
				Hud.actionbar(p, Hud.text("광란의 포효가 끝났다", ChatFormatting.GRAY));
			}
		}
		if (st.ultSelfT > 0) {
			st.ultSelfT--;
			if (Ticks.ambient()) Fx.particle((ServerLevel) p.level(), Fx.dust(Fx.rgb(0.90, 0.10, 0.10), 1.0F), p.getX(), p.getY() + 1, p.getZ(), 2, 0.3, 0.6, 0.3, 0);
			if (st.ultSelfT <= 0) {
				endUltSelf(p);
				prof.msgT = 40;
				Hud.actionbar(p, Hud.text("광란의 처형장이 끝났다", ChatFormatting.GRAY));
			}
		}
	}

	/** 포효의 둔화 장판 — 주위 {@value #FURY_RADIUS} 칸의 적을 0.2초씩 다시 둔화시켜, 범위를 벗어나면 곧 풀립니다. */
	private static void furyAura(ServerPlayer p) {
		for (LivingEntity e : Targets.enemies((ServerLevel) p.level(), p.position(), FURY_RADIUS, p)) {
			CrowdControl.slow(e, FURY_SLOW, 4, false);
		}
	}

	private static void endFury(ServerPlayer p) {
		CrowdControl.unmod(p, Attributes.MOVEMENT_SPEED, FURY_SPEED);
		CrowdControl.unmod(p, Attributes.ATTACK_DAMAGE, FURY_POWER);
		Combatant c = Attachments.combatant(p);
		c.dmgMul = 100;
		WarriorState st = Attachments.profile(p).state();
		if (st != null) {
			st.furyT = 0;
		}
	}

	private static void endUltSelf(ServerPlayer p) {
		CrowdControl.unmod(p, Attributes.MOVEMENT_SPEED, ULT_HASTE);
		WarriorState st = Attachments.profile(p).state();
		if (st != null) {
			st.ultSelfT = 0;
		}
	}

	static WarriorState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof WarriorState)) {
			prof.classState = new WarriorState();
		}
		return prof.state();
	}

	static void onSlayHit(ServerPlayer p) {
		slayHitListener.accept(p);
		SLAY_HIT_HOOKS.forEach(h -> h.accept(p));
	}

	static void onGrabArrive(ServerPlayer p, LivingEntity e) {
		grabArriveListener.accept(p, e);
		GRAB_ARRIVE_HOOKS.forEach(h -> h.accept(p, e));
	}

	/** 시험용: 대상에게 걸린 출혈 스택. */
	public static int bleedStacks(LivingEntity e) {
		return Bleed.stacks(e);
	}

	public static List<String> skillKeys() {
		return List.of(SLAY, FURY, CHAIN);
	}
}
