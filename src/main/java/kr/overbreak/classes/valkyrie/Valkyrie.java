package kr.overbreak.classes.valkyrie;

import kr.overbreak.core.tick.Ticks;
import java.util.List;
import java.util.Map;

import kr.overbreak.Overbreak;
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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 직업 11 · 발키리 (압도적 연사 · 넉백 없음) — 데이터팩 class/valkyrie + skill/valkyrie · rocket · spring · overheat + ult/barrage.
 *
 *   체력 200 (+0) · 연사 초당 8발 (차원 도약 중 10발) · 이동속도 -10% · 근접 불가 · 게이지 피해 12당 2%
 *   넉백 없음 — 모든 공격이 적을 밀어내지 않습니다 (피해 종류 자체가 넉백 없음)
 *   조작: 좌클릭 누르고 있기 = 연사 (데이터팩은 우클릭), 우클릭 = 전술 로켓 (데이터팩은 좌클릭)
 */
public final class Valkyrie implements PvpClass {
	public static final String ID = "valkyrie";
	static final String ROCKET = "vk_rocket";
	static final String SPRING = "vk_spring";
	static final String OVERHEAT = "vk_overheat";
	private static final Map<String, Integer> TOTALS = Map.of(ROCKET, Rocket.COOLDOWN, SPRING, Spring.COOLDOWN, OVERHEAT, Overheat.COOLDOWN);

	private static final ClassInfo INFO = new ClassInfo("발키리", "원거리 연사 딜러", 0xFFD24A,
			"압도적인 연사로 화력을 쏟아붓는 원거리 딜러. 모든 공격이 적을 밀어내지 않고, 차원 도약으로 뜬 동안 더 빨리 쏩니다.",
			List.of(
					SkillInfo.stat("체력", "200"),
					SkillInfo.stat("공격력", "발당 7 (20칸 히트스캔)"),
					SkillInfo.stat("공격속도", "초당 8발 (도약 중 10발)"),
					SkillInfo.stat("이동속도", "기본 대비 -10%")),
			List.of(
					new SkillInfo("패시브", "넉백 없음", null, "minecraft:anvil",
							"모든 공격이 적을 밀어내지 않음",
							List.of(
									SkillInfo.stat("분류", "패시브 · 넉백 면역"),
									SkillInfo.stat("대상", "연사 · 로켓 · 과열 분사 · 궁극기"),
									SkillInfo.stat("미사일", "명중 5번마다 반경 1.5칸 7.5"),
									SkillInfo.stat("미사일 초기화", "3초간 못 맞히면")), false),
					new SkillInfo("LMB", "연사", null, "minecraft:crossbow",
							"누르고 있으면 계속 사격",
							List.of(
									SkillInfo.stat("분류", "히트스캔 · 연사 · 탄창"),
									SkillInfo.stat("피해", "발당 7"),
									SkillInfo.stat("치명타", "머리에 맞으면 1.5배 (10.5)"),
									SkillInfo.stat("사거리", "20칸"),
									SkillInfo.stat("사격 속도", "초당 8발 · 도약 중 10발"),
									SkillInfo.stat("탄창", "50발 · R 또는 다 쓰면 1.5초 재장전"),
									SkillInfo.stat("탄퍼짐", "1초까지 0 → 2초에 최대 2.85도"),
									SkillInfo.stat("손을 떼면", "정확도 즉시 회복")), false),
					new SkillInfo("RMB", "전술 로켓", Overbreak.id("hud/skill/valkyrie_rocket"), null,
							"날아가 터지며 적을 살짝 띄움",
							List.of(
									SkillInfo.stat("분류", "투사체 · 광역 · 군중제어"),
									SkillInfo.stat("속도 · 사거리", "틱당 1.2칸 · 24칸"),
									SkillInfo.stat("범위", "반경 3칸"),
									SkillInfo.stat("피해", "35"),
									SkillInfo.stat("띄우기", "0.8초"),
									SkillInfo.stat("궁극기 중", "사용 가능"),
									SkillInfo.stat("재사용 대기시간", "5초")), false),
					new SkillInfo("SHIFT", "차원 도약", Overbreak.id("hud/skill/valkyrie_spring"), null,
							"뛰어올라 천천히 떨어지며 연사 가속",
							List.of(
									SkillInfo.stat("분류", "이동기 · 공중"),
									SkillInfo.stat("도약", "보는 방향으로 크게"),
									SkillInfo.stat("느린 낙하", "최대 3초 (착지하면 끝)"),
									SkillInfo.stat("공중 연사", "+25% (초당 10발)"),
									SkillInfo.stat("재사용 대기시간", "12초")), false),
					new SkillInfo("E", "과열 분사", Overbreak.id("hud/skill/valkyrie_overheat"), null,
							"앞 부채꼴에 1초간 난사",
							List.of(
									SkillInfo.stat("분류", "근접 · 광역 · 군중제어"),
									SkillInfo.stat("범위", "앞 60도 · 5칸"),
									SkillInfo.stat("발수", "10발 (1초)"),
									SkillInfo.stat("피해", "발당 8.5 (전탄 85)"),
									SkillInfo.stat("둔화", "40% · 1.5초"),
									SkillInfo.stat("이동", "자유 (기절하면 중단)"),
									SkillInfo.stat("재사용 대기시간", "10초")), false),
					new SkillInfo("Q", "탄막 포격", Overbreak.id("hud/skill/valkyrie_ult"), null,
							"기를 모은 뒤 4초간 40발 포격",
							List.of(
									SkillInfo.stat("분류", "히트스캔 · 저지불가 · 채널링"),
									SkillInfo.stat("기 모으기", "0.7초 (움직일 수 있음)"),
									SkillInfo.stat("포격", "40발 · 발당 8.5 (전탄 340)"),
									SkillInfo.stat("치명타", "머리에 맞으면 2배"),
									SkillInfo.stat("상태", "저지불가 · 이동속도 -40%"),
									SkillInfo.stat("사용 가능", "전술 로켓만"),
									SkillInfo.stat("충전", "피해 12당 2%")), true)));

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("발키리", ChatFormatting.YELLOW);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new ValkyrieState();
		Classes.baseStats(p, 0, -0.10, 500);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) p.sendSystemMessage(Component.empty()
				.append(Hud.text("[직업] ", ChatFormatting.GOLD))
				.append(Hud.bold("발키리", ChatFormatting.YELLOW))
				.append(Hud.text(" 를 선택했습니다.", ChatFormatting.WHITE)));
		Fx.sound(p, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.5F);
	}

	@Override
	public void remove(ServerPlayer p) {
		ValkyrieState st = stateOrNull(p);
		if (st != null) {
			Spring.end(p, st);
		}
		Effects.cancelOwnedBy(p);
		Attachments.combatant(p).ccImmune = false;
	}

	@Override
	public void giveItems(ServerPlayer p) {
		Inventory inv = p.getInventory();
		inv.setItem(0, SkillItems.skill("overbreak:valkyrie_rifle", SkillItems.name("연사 포탑", ChatFormatting.YELLOW), ROCKET,
				SkillItems.lore()
						.line("직업 · 발키리", ChatFormatting.DARK_GRAY).blank()
						.bold("[좌클릭 누르고 있기] 연사", ChatFormatting.YELLOW)
						.line(" 20칸 · 발당 7 · 초당 8발 (차원 도약 중 10발).", ChatFormatting.GRAY)
						.line(" 머리에 맞으면 치명타 2배. 오래 붙잡으면 탄이 퍼집니다.", ChatFormatting.GRAY)
						.line(" 탄창 50발 · R 키 또는 다 쓰면 1.5초 재장전.", ChatFormatting.GRAY)
						.line(" 명중 5번마다 미사일 (반경 1.5칸 7.5).", ChatFormatting.GOLD).blank()
						.bold("[우클릭] 전술 로켓", ChatFormatting.YELLOW)
						.line(" 반경 3칸 35 + 0.8초 띄우기. 쿨타임 5초", ChatFormatting.GRAY).blank()
						.bold("[웅크리기] 차원 도약", ChatFormatting.AQUA)
						.line(" 뛰어올라 천천히 떨어지며 공격속도 +25%. 쿨타임 12초", ChatFormatting.GRAY).blank()
						.bold("[E] 과열 분사", ChatFormatting.GOLD)
						.line(" 앞 60도 · 5칸에 10발 (발당 8.5) + 둔화. 쿨타임 10초", ChatFormatting.GRAY).blank()
						.line("이 직업의 모든 공격은 적을 밀어내지 않습니다.", ChatFormatting.RED).build()));
		inv.setItem(9, SkillItems.statSheet(Hud.bold("발키리", ChatFormatting.YELLOW), SkillItems.lore()
				.line("압도적 연사로 화력을 쏟아붓는 원거리 딜러", ChatFormatting.DARK_GRAY).blank()
				.line("체력      200  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    발당 9  (히트스캔 12칸 · 근접 불가)", ChatFormatting.GRAY)
				.line("공격속도  초당 8발  (차원 도약 중 10)", ChatFormatting.GRAY)
				.line("이동속도  기본 대비 -10%", ChatFormatting.GRAY).blank()
				.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("nether_star", Hud.bold("탄막 포격", ChatFormatting.YELLOW), SkillItems.lore()
				.line("궁극기 · 발키리", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 탄막 포격", ChatFormatting.YELLOW)
				.line(" 0.7초 동안 기를 모은 뒤 (움직일 수 있음)", ChatFormatting.GRAY)
				.line(" 0.1초마다 한 발씩 40발 · 발당 8.5 · 12칸.", ChatFormatting.GRAY)
				.line(" 저지불가 · 이동속도 -40% · 전술 로켓만 사용 가능.", ChatFormatting.RED).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	/** 좌클릭 — 누르고 있으면 매 틱 들어옵니다. 실제 사격 간격은 직업 틱이 정합니다. */
	@Override
	public void basic(ServerPlayer p) {
		state(p).trigger = true;
	}

	@Override
	public void primary(ServerPlayer p) {
		Rocket.cast(p);
	}

	/** R — 탄창을 새로 끼움 (1.5초). */
	@Override
	public void reload(ServerPlayer p) {
		Rifle.manualReload(p, state(p));
	}

	@Override
	public void secondary(ServerPlayer p) {
		Spring.cast(p, state(p));
	}

	@Override
	public void tertiary(ServerPlayer p) {
		Overheat.cast(p, state(p));
	}

	@Override
	public void ult(ServerPlayer p) {
		Barrage.cast(p, state(p));
	}

	/** 탄막 포격 중에는 전술 로켓만 — 데이터팩 skill/spring · overheat 의 vk_ult 거부. */
	@Override
	public boolean intercept(ServerPlayer p, InputRouter.Slot slot) {
		if (state(p).barrage == null || slot == InputRouter.Slot.PRIMARY) {
			return false;
		}
		if (slot == InputRouter.Slot.SECONDARY || slot == InputRouter.Slot.TERTIARY) {
			Fx.sound(p, SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.7F, 0.5F);
		}
		return true;
	}

	@Override
	public boolean meleeAllowed() {
		return false;
	}

	/** 피해 12 당 게이지 2% (0.1a). */
	@Override
	public int ultRate() {
		return 167;
	}

	@Override
	public ClassInfo info() {
		return INFO;
	}

	@Override
	public List<SkillSlot> hudSlots(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		ValkyrieState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(ROCKET), Rocket.COOLDOWN, false),
				new SkillSlot(prof.cooldown(SPRING), Spring.COOLDOWN, st.floating),
				new SkillSlot(prof.cooldown(OVERHEAT), Overheat.COOLDOWN, st.overheat != null));
	}

	/** 무기 칸 옆: 탄창 (N/50). 조준점 아래: 미사일 스택 5칸 + (재장전 · 궁극기 남은 시간) 게이지. */
	@Override
	public HudExtra hudExtra(ServerPlayer p) {
		ValkyrieState st = state(p);
		int meter = -1;
		int kind = 0;
		if (st.barrage != null) {
			meter = st.barrage.remainingPercent();
			kind = HudExtra.METER_DURATION;
		} else if (st.reloadT > 0) {
			int reload = kr.overbreak.core.tick.Ticks.of(Rifle.RELOAD);
			meter = (reload - st.reloadT) * 100 / reload;
			kind = HudExtra.METER_RELOAD;
		}
		return new HudExtra(st.ammo, Rifle.MAG, meter, kind, st.missileN, Rifle.MISSILE_EVERY,
				st.boosted ? HudExtra.FLAG_HASTE : 0);
	}

	@Override
	public void tick(ServerPlayer p) {
		ValkyrieState st = state(p);
		Cooldowns.tick(p, TOTALS);
		// 가속 여부를 먼저 정해야 같은 틱의 사격 간격에 반영됩니다
		Spring.tick(p, st);
		Rifle.tick(p, st);
	}

	public static ValkyrieState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof ValkyrieState)) {
			prof.classState = new ValkyrieState();
		}
		return prof.state();
	}

	static @Nullable ValkyrieState stateOrNull(ServerPlayer p) {
		return Attachments.profile(p).classState instanceof ValkyrieState st ? st : null;
	}
}
