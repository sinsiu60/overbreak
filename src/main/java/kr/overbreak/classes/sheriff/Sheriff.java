package kr.overbreak.classes.sheriff;

import kr.overbreak.core.tick.Ticks;
import java.util.List;
import java.util.Map;

import kr.overbreak.Overbreak;
import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.SkillInfo;
import kr.overbreak.combat.DamageModifiers;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 직업 14 · 보안관 (원거리 정밀 사격형) — 데이터팩 class/sheriff + skill/sheriff/* + ult/deadeye (데이터팩 이름 "명사수").
 *
 *   체력 170 (-30) · 0.8초에 한 발 · 이동속도 기본 · 근접 불가 · 게이지 피해 10당 2.5%
 *   오른손 리볼버 (피스키퍼 · 리볼버 난사 · 황야의 무법자) · 왼손으로 섬광 수류탄 투척 · 스피드로더 재장전
 */
public final class Sheriff implements PvpClass {
	public static final String ID = "sheriff";
	static final String FAN = "sh_fan";
	static final String ROLL = "sh_roll";
	static final String FLASH = "sh_flash";
	private static final Map<String, Integer> TOTALS = Map.of(FAN, FanHammer.COOLDOWN, ROLL, CombatRoll.COOLDOWN, FLASH, Flashbang.COOLDOWN);
	private static final int GOLD = Fx.rgb(1.00, 0.90, 0.45);

	static {
		DamageModifiers.register(Sheriff::rollGuard);
	}

	private static final ClassInfo INFO = new ClassInfo("보안관", "원거리 정밀 사격형", 0xE8B04A,
			"리볼버 한 자루로 버티는 정밀 사격수. 멀리서 정확히 맞힐수록 강하고, 구르기로 난사를 다시 채워 근접전도 받아칩니다.",
			List.of(
					SkillInfo.stat("체력", "170"),
					SkillInfo.stat("공격력", "발당 70 (10~20칸 감소)"),
					SkillInfo.stat("공격속도", "0.8초에 1발"),
					SkillInfo.stat("이동속도", "기본")),
			List.of(
					new SkillInfo("좌클릭", "피스키퍼", null, "minecraft:crossbow",
							"16칸까지 곧게 날아가는 정밀 사격",
							List.of(
									SkillInfo.stat("분류", "히트스캔 · 탄창"),
									SkillInfo.stat("피해", "발당 70"),
									SkillInfo.stat("거리 감소", "10칸부터 줄어 20칸 밖 -70%"),
									SkillInfo.stat("치명타", "머리에 맞으면 175% (122.5 · 먼 거리 36.8)"),
									SkillInfo.stat("사거리", "60칸 · 탄퍼짐 없음"),
									SkillInfo.stat("사격 속도", "0.8초에 1발"),
									SkillInfo.stat("탄창", "6발 · R 또는 다 쓰면 2초 재장전"),
									SkillInfo.stat("넉백", "약하게 밀어냄")), false),
					new SkillInfo("RMB", "리볼버 난사", Overbreak.id("hud/skill/sheriff_fan"), null,
							"0.6초 동안 앞에 남은 탄을 퍼부음",
							List.of(
									SkillInfo.stat("분류", "히트스캔 · 연사 · 탄창"),
									SkillInfo.stat("발수", "6발 (0.1초마다)"),
									SkillInfo.stat("피해", "발당 30 (전탄 180)"),
									SkillInfo.stat("사거리", "8칸"),
									SkillInfo.stat("탄퍼짐", "첫 발 정확 → 여섯 번째 7.2도"),
									SkillInfo.stat("반동", "한 발마다 화면 3도"),
									SkillInfo.stat("넉백", "없음"),
									SkillInfo.stat("재사용 대기시간", "없음 (탄창을 씀)")), false),
					new SkillInfo("SHIFT", "전술 구르기", Overbreak.id("hud/skill/sheriff_roll"), null,
							"누른 방향으로 빠르게 구름",
							List.of(
									SkillInfo.stat("분류", "이동기 · 피해 감소 · 재장전"),
									SkillInfo.stat("거리", "약 4칸 (0.3초)"),
									SkillInfo.stat("방향", "방향키 · 안 누르면 정면"),
									SkillInfo.stat("구르는 동안", "받는 피해 50% 감소"),
									SkillInfo.stat("리볼버 난사", "탄창 전부 회복"),
									SkillInfo.stat("재사용 대기시간", "8초")), false),
					new SkillInfo("F", "섬광 수류탄", Overbreak.id("hud/skill/sheriff_flash"), null,
							"왼손으로 던져 터뜨려 적을 묶음",
							List.of(
									SkillInfo.stat("분류", "투사체 · 광역 · 군중제어 · 이동기 봉인"),
									SkillInfo.stat("투척", "곧게 6칸 · 벽 · 적에 닿으면 폭발"),
									SkillInfo.stat("범위", "반경 3.5칸"),
									SkillInfo.stat("피해", "25"),
									SkillInfo.stat("둔화", "80% · 1.6초"),
									SkillInfo.stat("이동기 봉인", "1.6초"),
									SkillInfo.stat("정신집중", "시전 중이던 스킬 끊김"),
									SkillInfo.stat("재사용 대기시간", "10초")), false),
					new SkillInfo("Q", "황야의 무법자", Overbreak.id("hud/skill/sheriff_ult"), null,
							"2초간 적을 조준한 뒤 한꺼번에 쏨",
							List.of(
									SkillInfo.stat("분류", "히트스캔 · 광역 · 채널링"),
									SkillInfo.stat("조준", "2초 · 앞 90도 · 20칸 (벽 뒤 제외)"),
									SkillInfo.stat("피해", "조준한 적마다 120"),
									SkillInfo.stat("넉백", "강하게 밀어냄"),
									SkillInfo.stat("조준 중", "이동 가능 · 다른 행동 잠김"),
									SkillInfo.stat("취소", "기절 · 섬광에 맞으면"),
									SkillInfo.stat("충전", "피해 10당 2.5%")), true)));

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("보안관", ChatFormatting.GOLD);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new SheriffState();
		Classes.baseStats(p, -30, 0.0, 125);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) p.sendSystemMessage(Component.empty()
				.append(Hud.text("[직업] ", ChatFormatting.GOLD))
				.append(Hud.bold("보안관", ChatFormatting.GOLD))
				.append(Hud.text(" 을 선택했습니다.", ChatFormatting.WHITE)));
		Fx.sound(p, SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	@Override
	public void remove(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
		SheriffState st = stateOrNull(p);
		if (st != null) {
			st.guardT = 0;
			st.reloadT = 0;
		}
	}

	@Override
	public void giveItems(ServerPlayer p) {
		Inventory inv = p.getInventory();
		inv.setItem(0, SkillItems.skill("overbreak:revolver", SkillItems.name("피스키퍼", ChatFormatting.GOLD), FAN,
				SkillItems.lore()
						.line("직업 · 보안관", ChatFormatting.DARK_GRAY).blank()
						.bold("[좌클릭] 피스키퍼", ChatFormatting.GOLD)
						.line(" 60칸 정밀 사격 · 발당 70 (10~20칸에 걸쳐 최대 -70%) · 0.8초에 1발.", ChatFormatting.GRAY)
						.line(" 머리에 맞으면 치명타 175%.", ChatFormatting.GRAY)
						.line(" 탄창 6발 · R 키 또는 다 쓰면 2초 재장전.", ChatFormatting.GRAY).blank()
						.bold("[우클릭] 리볼버 난사", ChatFormatting.GOLD)
						.line(" 0.6초 동안 8칸에 남은 탄만큼 · 발당 30. 쿨타임 없음 (탄창을 씁니다)", ChatFormatting.GRAY).blank()
						.bold("[웅크리기] 전술 구르기", ChatFormatting.GOLD)
						.line(" 누른 방향으로 4칸 · 피해 50% 감소 · 탄창 전부 회복. 쿨타임 8초", ChatFormatting.GRAY).blank()
						.bold("[F] 섬광 수류탄", ChatFormatting.GOLD)
						.line(" 6칸 투척 · 반경 3.5칸 25 · 둔화 80% · 이동기 봉인 · 시전 끊기. 쿨타임 10초", ChatFormatting.GRAY).blank()
						.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		inv.setItem(9, SkillItems.statSheet(Hud.bold("보안관", ChatFormatting.GOLD), SkillItems.lore()
				.line("원거리 정밀 사격형", ChatFormatting.DARK_GRAY).blank()
				.line("체력      170  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    발당 70  (10~20칸 -70%)", ChatFormatting.GRAY)
				.line("공격속도  0.8초에 1발", ChatFormatting.GRAY)
				.line("이동속도  기본", ChatFormatting.GRAY)
				.line("탄창      6발", ChatFormatting.GRAY).blank()
				.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("clock", Hud.bold("황야의 무법자", ChatFormatting.GOLD), SkillItems.lore()
				.line("궁극기 · 보안관", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 황야의 무법자", ChatFormatting.GOLD)
				.line(" 2초간 앞 90도 · 20칸의 적을 조준합니다 (벽 뒤 제외).", ChatFormatting.GRAY)
				.line(" 끝나면 조준한 적 전원에게 120 + 강한 넉백.", ChatFormatting.GRAY)
				.line(" 조준 중 기절하거나 섬광에 맞으면 취소됩니다.", ChatFormatting.RED).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	@Override
	public void basic(ServerPlayer p) {
		Peacekeeper.fire(p, state(p));
	}

	@Override
	public void primary(ServerPlayer p) {
		FanHammer.cast(p, state(p));
	}

	@Override
	public void secondary(ServerPlayer p) {
		CombatRoll.cast(p, state(p));
	}

	@Override
	public void tertiary(ServerPlayer p) {
		Flashbang.cast(p);
	}

	@Override
	public void ult(ServerPlayer p) {
		Deadeye.cast(p, state(p));
	}

	@Override
	public void reload(ServerPlayer p) {
		Peacekeeper.manualReload(p, state(p));
	}

	/** 황야의 무법자 조준 중에는 아무 입력도 받지 않습니다 (데이터팩 cast_lock). */
	@Override
	public boolean intercept(ServerPlayer p, InputRouter.Slot slot) {
		return state(p).deadeye != null;
	}

	@Override
	public boolean meleeAllowed() {
		return false;
	}

	/** 피해 20 당 게이지 2% (0.1a). */
	@Override
	public int ultRate() {
		return 100;
	}

	@Override
	public ClassInfo info() {
		return INFO;
	}

	@Override
	public List<SkillSlot> hudSlots(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		SheriffState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(FAN), FanHammer.COOLDOWN, st.fan != null),
				new SkillSlot(prof.cooldown(ROLL), CombatRoll.COOLDOWN, st.roll != null || st.guardT > 0),
				new SkillSlot(prof.cooldown(FLASH), Flashbang.COOLDOWN, false));
	}

	/** 무기 칸 옆: 탄창 (N/6). 조준점 아래: 재장전 · 황야의 무법자 남은 시간 게이지. */
	@Override
	public HudExtra hudExtra(ServerPlayer p) {
		SheriffState st = state(p);
		if (st.deadeye != null) {
			return new HudExtra(st.ammo, Peacekeeper.MAG, st.deadeye.remainingPercent(), HudExtra.METER_DURATION);
		}
		if (st.reloadT > 0) {
			int reload = Ticks.of(Peacekeeper.RELOAD);
			return new HudExtra(st.ammo, Peacekeeper.MAG, (reload - st.reloadT) * 100 / reload, HudExtra.METER_RELOAD);
		}
		return new HudExtra(st.ammo, Peacekeeper.MAG, -1, 0);
	}

	@Override
	public void tick(ServerPlayer p) {
		SheriffState st = state(p);
		Cooldowns.tick(p, TOTALS);
		Peacekeeper.tick(p, st);
		if (st.guardT > 0) {
			st.guardT--;
			if (Ticks.ambient()) Fx.particle(p.level(), Fx.dust(GOLD, 0.9F), p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.5, 0.3, 0);
		}
	}

	/** 전술 구르기 중 받는 피해 50% 감소 (데이터팩 roll_guard 인챈트 damage_protection 12.5). */
	private static float rollGuard(LivingEntity target, DamageSource source, float damage) {
		if (target instanceof ServerPlayer sp && Attachments.profile(sp).classState instanceof SheriffState st && st.guardT > 0) {
			return damage * 0.5F;
		}
		return damage;
	}

	/** 피스키퍼 피해 x100 — 맞힌 거리 기준 (시험용). */
	public static int shotDamage100(double distance) {
		return Peacekeeper.damage100(distance);
	}

	public static SheriffState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof SheriffState)) {
			prof.classState = new SheriffState();
		}
		return prof.state();
	}

	static @Nullable SheriffState stateOrNull(ServerPlayer p) {
		return Attachments.profile(p).classState instanceof SheriffState st ? st : null;
	}
}
