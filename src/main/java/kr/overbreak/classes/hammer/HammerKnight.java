package kr.overbreak.classes.hammer;

import java.util.List;
import java.util.Map;

import kr.overbreak.Overbreak;
import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.SkillInfo;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.item.SkillItems;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.skill.SkillSlot;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 직업 2 · 햄머나이트 (기절 특화 · 근접 제압형) — 데이터팩 class/hammer_knight + skill/smash · charge · crush + ult/hammer.
 *
 *   체력 300 (+100) · 공격력 45 (철퇴 +44) · 공격속도 0.8 · 이동속도 +7%
 *   오른손 철퇴(우클릭 지면 분쇄) · 왼손 방패(웅크리기 돌진 충격)
 */
public final class HammerKnight implements PvpClass {
	public static final String ID = "hammer_knight";
	static final String SMASH = "hk_smash";
	static final String CHARGE = "hk_charge";
	static final String CRUSH = "hk_crush";
	private static final Map<String, Integer> TOTALS = Map.of(SMASH, Smash.COOLDOWN, CHARGE, Charge.COOLDOWN, CRUSH, Crush.COOLDOWN);
	private static final Identifier MACE_DAMAGE = Overbreak.id("hammer_damage");

	private static final ClassInfo INFO = new ClassInfo("햄머나이트", "기절 특화 · 근접 제압형", 0xFFC23A,
			"방패로 돌진해 파고들고, 철퇴로 땅을 내려찍어 적을 기절시키는 근접 제압형 기사.",
			List.of(
					SkillInfo.stat("체력", "300"),
					SkillInfo.stat("공격력", "45"),
					SkillInfo.stat("공격속도", "초당 0.8회"),
					SkillInfo.stat("이동속도", "기본 대비 +7%")),
			List.of(
					new SkillInfo("패시브", "뇌진탕", null, "minecraft:bell",
							"기절한 적을 처음 때리면 추가 피해",
							List.of(
									SkillInfo.stat("분류", "군중제어 연장 · 추가 피해"),
									SkillInfo.stat("조건", "기절 · 넘어뜨림 상태의 적"),
									SkillInfo.stat("추가 피해", "30 (무적 시간 무시)"),
									SkillInfo.stat("CC 연장", "0.5초"),
									SkillInfo.stat("발동 횟수", "기절 한 번에 한 번"),
									SkillInfo.stat("발동 경로", "평타 · 돌진 충격 · 대지 진동파")), false),
					new SkillInfo("LMB", "철퇴 휘두르기", null, "minecraft:mace",
							"앞의 넓은 범위를 내리쳐 여러 적 공격",
							List.of(
									SkillInfo.stat("분류", "근접 · 광역"),
									SkillInfo.stat("피해", "45 (공격력)"),
									SkillInfo.stat("공격속도", "초당 0.8회"),
									SkillInfo.stat("사거리", "3칸"),
									SkillInfo.stat("넉백", "없음"),
									SkillInfo.stat("범위", "앞 100도 부채꼴")), false),
					new SkillInfo("RMB", "지면 분쇄", Overbreak.id("hud/skill/hammer_smash"), null,
							"망치를 들어 내려찍어 앞의 적 기절",
							List.of(
									SkillInfo.stat("분류", "근접 · 광역 · 군중제어"),
									SkillInfo.stat("시전 시간", "0.5초 (기절 시 끊김)"),
									SkillInfo.stat("범위", "앞 120도 · 4칸"),
									SkillInfo.stat("피해", "40"),
									SkillInfo.stat("기절", "1.2초"),
									SkillInfo.stat("돌진 중 사용", "끝난 자리에서 360도"),
									SkillInfo.stat("재사용 대기시간", "9초")), false),
					new SkillInfo("SHIFT", "돌진 충격", Overbreak.id("hud/skill/hammer_charge"), null,
							"방패를 세우고 앞으로 돌진",
							List.of(
									SkillInfo.stat("분류", "이동기 · 근접 · 군중제어"),
									SkillInfo.stat("기 모으기", "0.3초 (제자리)"),
									SkillInfo.stat("돌진 거리", "7.6칸 (0.55초)"),
									SkillInfo.stat("피해", "20 (대상당 1회)"),
									SkillInfo.stat("둔화", "50% · 1.5초"),
									SkillInfo.stat("재사용 대기시간", "7초")), false),
					new SkillInfo("E", "중력 파쇄", Overbreak.id("hud/skill/hammer_crush"), null,
							"주위 적을 발밑으로 끌어오고 균열 생성",
							List.of(
									SkillInfo.stat("분류", "광역 · 군중제어 · 이동기 봉인"),
									SkillInfo.stat("선동작", "0.5초 (움직일 수 있음)"),
									SkillInfo.stat("범위", "반경 4칸"),
									SkillInfo.stat("끌어오기", "자신의 발밑 1칸"),
									SkillInfo.stat("피해", "30"),
									SkillInfo.stat("둔화", "70% · 0.8초"),
									SkillInfo.stat("균열 지대", "반경 5칸 · 5초 · 10% 둔화"),
									SkillInfo.stat("재사용 대기시간", "11초")), false),
					new SkillInfo("Q", "대지 진동파", Overbreak.id("hud/skill/hammer_ult"), null,
							"앞으로 퍼지는 충격파로 적을 넘어뜨림",
							List.of(
									SkillInfo.stat("분류", "광역 · 군중제어 · 관통"),
									SkillInfo.stat("시전 시간", "0.8초 (이동 불가)"),
									SkillInfo.stat("범위", "앞 70도 · 20칸"),
									SkillInfo.stat("피해", "100 (바로 앞) → 50 (20칸)"),
									SkillInfo.stat("넘어뜨림", "2초"),
									SkillInfo.stat("연계", "뇌진탕 즉시 발동"),
									SkillInfo.stat("충전", "피해 10당 2%")), true)));

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("햄머나이트", ChatFormatting.YELLOW);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new HammerState();
		Classes.baseStats(p, 100, 0.07, 80);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) p.sendSystemMessage(Component.empty()
				.append(Hud.text("[직업] ", ChatFormatting.GOLD))
				.append(Hud.bold("햄머나이트", ChatFormatting.YELLOW))
				.append(Hud.text(" 를 선택했습니다.", ChatFormatting.WHITE)));
		Fx.sound(p, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	@Override
	public void remove(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
	}

	@Override
	public void giveItems(ServerPlayer p) {
		Inventory inv = p.getInventory();
		inv.setItem(0, mace());
		inv.setItem(Inventory.SLOT_OFFHAND, SkillItems.skill("shield", SkillItems.name("강철 방패", ChatFormatting.YELLOW), CHARGE,
				SkillItems.lore()
						.line("직업 · 햄머나이트", ChatFormatting.DARK_GRAY).blank()
						.bold("[웅크리기] 돌진 충격", ChatFormatting.YELLOW)
						.line(" 0.3초간 방패를 세운 뒤 앞으로 돌진합니다.", ChatFormatting.GRAY)
						.line(" 경로의 적에게 20의 피해와 50% 둔화.", ChatFormatting.GRAY)
						.line(" 쿨타임 7초", ChatFormatting.DARK_GRAY).build()));
		inv.setItem(9, SkillItems.statSheet(Hud.bold("햄머나이트", ChatFormatting.YELLOW), SkillItems.lore()
				.line("기절 특화 · 근접 제압형", ChatFormatting.DARK_GRAY).blank()
				.line("체력      300  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    45", ChatFormatting.GRAY)
				.line("공격속도  0.8  (1.25초에 1회)", ChatFormatting.GRAY)
				.line("이동속도  기본 대비 +7%", ChatFormatting.GRAY).blank()
				.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	private static ItemStack mace() {
		ItemStack s = SkillItems.skill("mace", SkillItems.name("대지분쇄 철퇴", ChatFormatting.YELLOW), SMASH,
				SkillItems.lore()
						.line("직업 · 햄머나이트", ChatFormatting.DARK_GRAY).blank()
						.line("공격력 45 · 공격속도 0.8", ChatFormatting.GRAY).blank()
						.bold("[패시브] 뇌진탕", ChatFormatting.GOLD)
						.line(" 기절한 적을 처음 때리면 추가 피해 30 · 기절 0.5초 연장.", ChatFormatting.GRAY)
						.line(" 기절 한 번에 한 번만 발동합니다.", ChatFormatting.GRAY).blank()
						.bold("[우클릭] 지면 분쇄", ChatFormatting.YELLOW)
						.line(" 0.5초간 망치를 들었다가 내려찍어", ChatFormatting.GRAY)
						.line(" 앞 120도 · 4칸에 40의 피해와 1.2초 기절.", ChatFormatting.GRAY)
						.line(" 쿨타임 9초", ChatFormatting.DARK_GRAY).build());
		s.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(MACE_DAMAGE, 44.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.build());
		return s;
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("nether_star", Hud.bold("대지 진동파", ChatFormatting.GOLD), SkillItems.lore()
				.line("궁극기 · 햄머나이트", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 대지 진동파", ChatFormatting.GOLD)
				.line(" 0.8초간 망치를 뒤로 빼 힘을 모은 뒤 내려칩니다.", ChatFormatting.GRAY)
				.line(" 앞 70도 · 20칸으로 충격파가 퍼지며", ChatFormatting.GRAY)
				.line(" 닿는 순서대로 2초간 넘어뜨립니다.", ChatFormatting.GRAY)
				.line(" 피해 100 (바로 앞) → 50 (20칸).", ChatFormatting.GRAY).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	@Override
	public void primary(ServerPlayer p) {
		Smash.cast(p, state(p));
	}

	@Override
	public void secondary(ServerPlayer p) {
		Charge.cast(p, state(p));
	}

	@Override
	public void tertiary(ServerPlayer p) {
		Crush.cast(p, state(p));
	}

	@Override
	public void ult(ServerPlayer p) {
		Quake.cast(p, state(p));
	}

	@Override
	public void onMeleeHit(ServerPlayer p, LivingEntity target) {
		Concussion.apply(p, target);
	}

	@Override
	public boolean meleeKnockback() {
		return false;
	}

	@Override
	public ClassInfo info() {
		return INFO;
	}

	@Override
	public List<SkillSlot> hudSlots(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		HammerState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(SMASH), Smash.COOLDOWN, st.smash != null || st.smashQueued),
				new SkillSlot(prof.cooldown(CHARGE), Charge.COOLDOWN, st.charge != null),
				new SkillSlot(prof.cooldown(CRUSH), Crush.COOLDOWN, st.crush != null));
	}

	@Override
	public void tick(ServerPlayer p) {
		Cooldowns.tick(p, TOTALS);
	}

	static HammerState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof HammerState)) {
			prof.classState = new HammerState();
		}
		return prof.state();
	}
}
