package kr.overbreak.classes.ironfist;

import kr.overbreak.core.tick.Ticks;
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
import kr.overbreak.input.InputRouter;
import kr.overbreak.item.SkillItems;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.skill.HudExtra;
import kr.overbreak.skill.SkillSlot;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import kr.overbreak.util.Local;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 직업 13 · 파쇄권 (정면으로 받아내고 되갚는 근접 탱커) — 데이터팩 class/ironfist + skill/ironfist · punch · block · slam + ult/doom.
 *
 *   체력 200 (+0) · 초당 3발 · 이동속도 -5% · 근접 불가 · 게이지 피해 15당 2% + 흡수 10당 1%
 *   오른손 건틀릿(로켓 펀치 · 파워 블록 · 지진 강타 · 파멸의 일격) · 왼손 철권포 장갑(좌클릭 산탄)
 */
public final class IronFist implements PvpClass {
	public static final String ID = "ironfist";
	static final String PUNCH = "if_punch";
	static final String BLOCK = "if_block";
	static final String SLAM = "if_slam";
	private static final Map<String, Integer> TOTALS = Map.of(PUNCH, RocketPunch.COOLDOWN, BLOCK, PowerBlock.COOLDOWN, SLAM, SeismicSlam.COOLDOWN);
	private static final Identifier SHIELD_CAP = Overbreak.id("if_shield");

	static {
		PowerBlock.init();
	}

	private static final ClassInfo INFO = new ClassInfo("파쇄권", "근접 탱커 · 반격형", 0x5AB4FF,
			"정면으로 받아내고 되갚는 근접 탱커. 막아 낸 만큼 로켓 펀치가 세지고, 적을 벽에 처박는 것이 최대 화력입니다.",
			List.of(
					SkillInfo.stat("체력", "200"),
					SkillInfo.stat("공격력", "밀착 38.5 (산탄 11발)"),
					SkillInfo.stat("공격속도", "초당 3발"),
					SkillInfo.stat("이동속도", "기본 대비 -5%")),
			List.of(
					new SkillInfo("패시브", "최선의 방어는", null, "minecraft:golden_apple",
							"스킬로 적을 맞히면 추가 체력",
							List.of(
									SkillInfo.stat("분류", "패시브 · 보호막"),
									SkillInfo.stat("적중 1명당", "추가 체력 +20"),
									SkillInfo.stat("최대", "120"),
									SkillInfo.stat("감소", "2초 뒤부터 초당 20"),
									SkillInfo.stat("평타", "얻지 못함"),
									SkillInfo.stat("궁극기 충전", "추가 체력 10당 1%")), false),
					new SkillInfo("LMB", "철권포", null, "minecraft:iron_ingot",
							"왼손에서 산탄을 발사",
							List.of(
									SkillInfo.stat("분류", "히트스캔 · 산탄"),
									SkillInfo.stat("탄환", "11발 · 발당 3.5 (맞은 만큼 합산)"),
									SkillInfo.stat("밀착 전탄", "38.5 (머리 77)"),
									SkillInfo.stat("치명타", "머리에 맞은 탄환 2배"),
									SkillInfo.stat("발사 속도", "초당 3발"),
									SkillInfo.stat("사거리", "5칸"),
									SkillInfo.stat("탄퍼짐", "항상 같은 모양"),
									SkillInfo.stat("탄창", "4발 · 쏜 뒤 0.7초부터 0.7초마다 1발"),
									SkillInfo.stat("넉백", "없음")), false),
					new SkillInfo("RMB", "로켓 펀치", Overbreak.id("hud/skill/ironfist_punch"), null,
							"기를 모았다가 돌진해 적을 밀쳐냄",
							List.of(
									SkillInfo.stat("분류", "이동기 · 근접 · 군중제어"),
									SkillInfo.stat("충전", "누르고 있기 · 최대 0.9초"),
									SkillInfo.stat("거리", "5 → 12칸"),
									SkillInfo.stat("피해", "30 → 60"),
									SkillInfo.stat("밀쳐내기", "1.5 → 4칸"),
									SkillInfo.stat("벽 충돌", "40 추가 + 0.7초 기절"),
									SkillInfo.stat("강화", "피해 1.5배 · 거리 1.3배 (90 · 15.6칸)"),
									SkillInfo.stat("강화 벽 충돌 기절", "0.7 → 1.7초 (충전량)"),
									SkillInfo.stat("강화 조건", "파워 블록 60 이상 · 궁극기 사용"),
									SkillInfo.stat("재사용 대기시간", "5초")), false),
					new SkillInfo("SHIFT", "파워 블록", Overbreak.id("hud/skill/ironfist_block"), null,
							"건틀릿을 세워 정면 피해를 막음",
							List.of(
									SkillInfo.stat("분류", "방어 · 피해 감소"),
									SkillInfo.stat("지속", "최대 2초 (다시 누르면 해제)"),
									SkillInfo.stat("정면 90도 피해", "전부 막음"),
									SkillInfo.stat("충전 성공", "로켓 펀치 쿨타임 초기화"),
									SkillInfo.stat("이동속도", "-35%"),
									SkillInfo.stat("강화 조건", "60 이상 막아 내기"),
									SkillInfo.stat("군중 제어", "막지 못함"),
									SkillInfo.stat("재사용 대기시간", "6초 (해제 후)")), false),
					new SkillInfo("E", "지진 강타", Overbreak.id("hud/skill/ironfist_slam"), null,
							"도약했다가 땅을 내리쳐 앞으로 파동",
							List.of(
									SkillInfo.stat("분류", "이동기 · 광역 · 군중제어"),
									SkillInfo.stat("도약", "보는 방향으로 포물선"),
									SkillInfo.stat("범위", "앞 90도 · 16칸"),
									SkillInfo.stat("피해", "40 (오래 떠 있으면 최대 60)"),
									SkillInfo.stat("둔화", "30% · 1.5초"),
									SkillInfo.stat("취소", "비행 중 F"),
									SkillInfo.stat("재사용 대기시간", "7초")), false),
					new SkillInfo("Q", "파멸의 일격", Overbreak.id("hud/skill/ironfist_ult"), null,
							"하늘로 솟구쳤다가 고른 곳에 내리꽂힘",
							List.of(
									SkillInfo.stat("분류", "이동기 · 광역"),
									SkillInfo.stat("솟구침 · 조준", "1초 · 최대 3초 (무적)"),
									SkillInfo.stat("조준", "이동 키로 착탄 원을 끌고 다님 (최대 20칸)"),
									SkillInfo.stat("확정", "우클릭"),
									SkillInfo.stat("범위", "반경 6칸"),
									SkillInfo.stat("피해", "중심 150 → 가장자리 15"),
									SkillInfo.stat("착지", "추가 체력 120"),
									SkillInfo.stat("사용 시", "로켓 펀치 강화"),
									SkillInfo.stat("충전", "피해 15당 2%")), true)));

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("파쇄권", ChatFormatting.GRAY);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new IronFistState();
		Classes.baseStats(p, 0, -0.05, 100 * HandCannon.SHOTS_PER_SECOND);
		// 흡수 체력을 직접 들고 있으려면 상한을 열어야 합니다 (기본 0)
		CrowdControl.mod(p, Attributes.MAX_ABSORPTION, SHIELD_CAP, Absorb.MAX, AttributeModifier.Operation.ADD_VALUE);
		p.setAbsorptionAmount(0.0F);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) p.sendSystemMessage(Component.empty()
				.append(Hud.text("[직업] ", ChatFormatting.GOLD))
				.append(Hud.bold("파쇄권", ChatFormatting.GRAY))
				.append(Hud.text(" 를 선택했습니다.", ChatFormatting.WHITE)));
		Fx.sound(p, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	@Override
	public void remove(ServerPlayer p) {
		IronFistState st = stateOrNull(p);
		if (st != null && st.slam != null) {
			st.slam.cancel();
		}
		Effects.cancelOwnedBy(p);
		CrowdControl.unmod(p, Attributes.MAX_ABSORPTION, SHIELD_CAP);
		p.setAbsorptionAmount(0.0F);
		p.setNoGravity(false);
	}

	@Override
	public void giveItems(ServerPlayer p) {
		Inventory inv = p.getInventory();
		inv.setItem(0, SkillItems.skill("overbreak:gauntlet", SkillItems.name("파쇄 건틀릿", ChatFormatting.AQUA), PUNCH,
				SkillItems.lore()
						.line("직업 · 파쇄권", ChatFormatting.DARK_GRAY).blank()
						.bold("[우클릭 길게] 로켓 펀치", ChatFormatting.RED)
						.line(" 기를 모았다가 손을 떼면 돌진합니다. 거리 5~12칸 · 피해 30~60.", ChatFormatting.GRAY)
						.line(" 적을 밀쳐내고, 벽에 처박히면 40 추가 + 0.7초 기절.", ChatFormatting.GRAY).blank()
						.bold("[웅크리기] 파워 블록", ChatFormatting.AQUA)
						.line(" 최대 2초간 정면 피해를 전부 막습니다. 60 이상 막으면 강화 + 쿨 초기화.", ChatFormatting.GRAY).blank()
						.bold("[E] 지진 강타", ChatFormatting.GOLD)
						.line(" 도약했다가 착지하며 앞 90도 · 16칸에 파동.", ChatFormatting.GRAY).blank()
						.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		inv.setItem(Inventory.SLOT_OFFHAND, SkillItems.skill("overbreak:hand_cannon", SkillItems.name("철권포", ChatFormatting.GRAY), "if_gun",
				SkillItems.lore()
						.line("직업 · 파쇄권", ChatFormatting.DARK_GRAY).blank()
						.bold("[좌클릭] 철권포", ChatFormatting.GRAY)
						.line(" 탄환 11발 · 발당 3.5 · 사거리 5칸 · 초당 3발. 맞은 탄환을 합산해 한 번에.", ChatFormatting.GRAY)
						.line(" 머리에 맞은 탄환은 치명타 2배. 밀착 전탄 38.5 (머리 77).", ChatFormatting.GRAY)
						.line(" 탄창 4발, 쏜 뒤 0.7초부터 0.7초마다 한 발씩 찹니다.", ChatFormatting.GRAY).build()));
		inv.setItem(9, SkillItems.statSheet(Hud.bold("파쇄권", ChatFormatting.GRAY), SkillItems.lore()
				.line("정면으로 받아내고 되갚는 근접 탱커", ChatFormatting.DARK_GRAY).blank()
				.line("체력      200  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    밀착 38.5 (산탄 11발)", ChatFormatting.GRAY)
				.line("공격속도  초당 3발", ChatFormatting.GRAY)
				.line("이동속도  기본 대비 -5%", ChatFormatting.GRAY).blank()
				.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("anvil", Hud.bold("파멸의 일격", ChatFormatting.RED), SkillItems.lore()
				.line("궁극기 · 파쇄권", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 파멸의 일격", ChatFormatting.RED)
				.line(" 1초 동안 하늘로 솟구칩니다 (무적).", ChatFormatting.GRAY)
				.line(" 3초 동안 이동 키로 내리꽂을 곳을 고릅니다 - 상대도 봅니다.", ChatFormatting.GRAY)
				.line(" 우클릭으로 확정, 3초가 지나면 그 자리로.", ChatFormatting.GRAY)
				.line(" 반경 6칸 · 중심 150 에서 가장자리 15 로 줄어드는 피해.", ChatFormatting.RED)
				.line(" 착지하면 추가 체력이 가득 찹니다 (120).", ChatFormatting.GOLD).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	@Override
	public void basic(ServerPlayer p) {
		HandCannon.fire(p, state(p));
	}

	@Override
	public void primary(ServerPlayer p) {
		RocketPunch.cast(p, state(p));
	}

	@Override
	public void secondary(ServerPlayer p) {
		PowerBlock.cast(p, state(p));
	}

	@Override
	public void tertiary(ServerPlayer p) {
		SeismicSlam.cast(p, state(p));
	}

	@Override
	public void ult(ServerPlayer p) {
		MeteorStrike.cast(p, state(p));
	}

	/** 데이터팩 skill/ironfist/pre_use · pre_sneak · pre_swap — 기존 차단보다 먼저. */
	@Override
	public boolean intercept(ServerPlayer p, InputRouter.Slot slot) {
		IronFistState st = state(p);
		if (st.doom != null) {
			// 궁극기 중에는 조준 단계의 우클릭(내리꽂기) 말고는 아무 입력도 받지 않습니다
			if (slot == InputRouter.Slot.PRIMARY && st.doom.aiming()) {
				st.doom.drop();
			}
			return slot != InputRouter.Slot.BASIC;
		}
		if (slot == InputRouter.Slot.SECONDARY && st.block != null) {
			st.block.end();
			return true;
		}
		if (slot == InputRouter.Slot.TERTIARY && st.slam != null) {
			st.slam.cancel();
			return true;
		}
		// 충전 중에 반복해서 들어오는 우클릭 · 웅크리기 · F 는 조용히 무시합니다
		return st.charging() && slot != InputRouter.Slot.BASIC && slot != InputRouter.Slot.ULT;
	}

	@Override
	public boolean meleeAllowed() {
		return false;
	}

	/** 피해 15 당 게이지 2% (0.1a). */
	@Override
	public int ultRate() {
		return 133;
	}

	@Override
	public ClassInfo info() {
		return INFO;
	}

	@Override
	public List<SkillSlot> hudSlots(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		IronFistState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(PUNCH), RocketPunch.COOLDOWN, st.punch != null || st.empowerT > 0),
				new SkillSlot(prof.cooldown(BLOCK), PowerBlock.COOLDOWN, st.block != null),
				new SkillSlot(prof.cooldown(SLAM), SeismicSlam.COOLDOWN, st.slam != null));
	}

	@Override
	public HudExtra hudExtra(ServerPlayer p) {
		IronFistState st = state(p);
		if (st.charging()) {
			return new HudExtra(st.ammo, HandCannon.MAG, st.punch.chargePercent(), HudExtra.METER_CHARGE);
		}
		if (st.block != null) {
			return new HudExtra(st.ammo, HandCannon.MAG, st.block.guardPercent(), HudExtra.METER_GUARD);
		}
		return new HudExtra(st.ammo, HandCannon.MAG, -1, 0);
	}

	@Override
	public void tick(ServerPlayer p) {
		IronFistState st = state(p);
		Cooldowns.tick(p, TOTALS);
		HandCannon.tick(p, st);
		if (st.empowerT > 0) {
			st.empowerT--;
			if (Ticks.every(p.tickCount, 2)) {
				empoweredSparks(p);
			}
		}
		// 비행 판정은 체공 카운터를 갱신하기 "전에" — 착지한 틱에 0 으로 초기화되면 체공 보정이 날아갑니다
		if (st.slam != null) {
			st.slam.flyTick();
		}
		st.air = p.onGround() ? 0 : st.air + 1;
		Absorb.tick(p, st);
	}

	/** 강화 펀치가 차 있는 동안 오른손 건틀릿 근처에서 파란 스파크 (밝은 하늘색 → 파랑으로 번쩍 + 전기 불꽃). */
	private static void empoweredSparks(ServerPlayer p) {
		// 몸통 방향 기준 오른손 쪽 (^x 는 왼쪽이 양수라 음수)
		Vec3 hand = Local.flat(p.position(), p.yBodyRot, -0.38, p.isCrouching() ? 0.55 : 0.75, 0.12);
		ServerLevel level = p.level();
		Fx.particle(level, new DustColorTransitionOptions(0xC8F0FF, 0x2F7BFF, 1.0F), hand.x, hand.y, hand.z, 4, 0.18, 0.22, 0.18, 0);
		Fx.particle(level, ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 2, 0.12, 0.15, 0.12, 0.08);
	}

	public static IronFistState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof IronFistState)) {
			prof.classState = new IronFistState();
		}
		return prof.state();
	}

	/** 파쇄권이 아니면 null. */
	static @Nullable IronFistState stateOrNull(ServerPlayer p) {
		return Attachments.profile(p).classState instanceof IronFistState st ? st : null;
	}
}
