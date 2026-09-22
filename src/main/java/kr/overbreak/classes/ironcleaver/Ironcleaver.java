package kr.overbreak.classes.ironcleaver;

import static kr.overbreak.classes.ironcleaver.IronSpec.*;

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
import kr.overbreak.core.tick.GameClock;
import kr.overbreak.input.InputRouter;
import kr.overbreak.item.SkillItems;
import kr.overbreak.net.InputModePayload;
import kr.overbreak.net.IronPayload;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.skill.HudExtra;
import kr.overbreak.skill.SkillSlot;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 직업 10 · 참철 — 대검 근접 결투형 (모아 베기 한 방).
 *
 *   체력 260 · 이동속도 -5% · 게이지 피해 1당 1%
 *   패시브 「중량」: 동작 중 받는 넉백 -50% · 2단 이상 모아 벤 참격은 대상의 피해 감소를 절반만 받음 (방어 파쇄)
 *
 * 조작
 *   LMB 짧게    3타 콤보 (오른 가로 → 왼 가로 → 내려찍기)
 *   LMB 누르기  참 모으기 (0.6 · 1.2 · 1.8초 단계 · 1.8~2.0초 진 참)
 *   RMB         어깨 박치기 (모으는 중에도 — 모으기 시계는 멈춤)
 *   SHIFT       검막 (막는 중 LMB → 곧바로 모으기)
 *   E           대지 가르기
 *   Q           천참
 */
public final class Ironcleaver implements PvpClass {
	public static final String ID = "ironcleaver";
	public static final String BASH = "ic_bash";
	public static final String GUARD = "ic_guard";
	public static final String REND = "ic_rend";
	private static final Map<String, Integer> TOTALS =
			Map.of(BASH, BASH_COOLDOWN, GUARD, GUARD_COOLDOWN, REND, REND_COOLDOWN);
	private static final int STEEL = 0xC8CDD6;

	static {
		DamageModifiers.register(Ironcleaver::modify);
		// 모으는 도중에 시전자를 보기 시작한 사람에게 지금 단계를 알려 줌 (오오라 색)
		EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
			if (entity instanceof ServerPlayer caster && stateOrNull(caster) instanceof IronState st && st.stage > 0) {
				IronPayload.send(viewer, IronPayload.of(IronPayload.STAGE, caster, st.stage, st.perfect ? 1 : 0));
			}
		});
	}

	private static final ClassInfo INFO = new ClassInfo("참철", "대검 근접 결투형", STEEL,
			"무거운 대검 한 자루로 버티다 한 방에 끝내는 결투가. 짧게 누르면 빠른 3타, 길게 누르면 단계마다 강해지는 모아 베기 — 1.8초에 맞춰 떼면 진 참.",
			List.of(
					SkillInfo.stat("체력", "260"),
					SkillInfo.stat("공격력", "35 · 35 · 55 (모아 베기 40~130)"),
					SkillInfo.stat("이동속도", "-5%"),
					SkillInfo.stat("넉백", "동작 중 -50%")),
			List.of(
					new SkillInfo("패시브", "중량", null, "minecraft:anvil",
							"무거워서 동작 중에는 잘 밀리지 않고, 크게 모은 참격은 방어를 반쯤 뚫음",
							List.of(
									SkillInfo.stat("동작 중 넉백", "-50% (휘두르기 · 모으기 · 스킬)"),
									SkillInfo.stat("모으기 2단부터", "넉백 면역"),
									SkillInfo.stat("방어 파쇄", "2단 이상 모아 베기 — 대상의 피해 감소 효과를 절반만 받음 (파워 블록 등)")), false),
					new SkillInfo("LMB", "참철검", Overbreak.id("hud/skill/ironcleaver_sword"), null,
							"짧게 누르면 3타 콤보, 0.25초 이상 누르고 있으면 참 모으기",
							List.of(
									SkillInfo.stat("1타", "오른쪽 → 왼쪽 가로 베기 · 150° 4칸 · 35 (선딜 0.35초)"),
									SkillInfo.stat("2타", "왼쪽 → 오른쪽 가로 베기 · 150° 4칸 · 35 (선딜 0.35초)"),
									SkillInfo.stat("3타", "내려찍기 · 폭 1.5 × 4.5칸 · 55 (선딜 0.45초)"),
									SkillInfo.stat("콤보", "후딜 중 누르면 이어짐 · 1.2초 쉬면 1타로"),
									SkillInfo.stat("모으기 단계", "0.6초 40 · 1.2초 70 · 1.8초 110 (앞 60° 5~5.5칸)"),
									SkillInfo.stat("진 참", "1.8~2.0초 사이에 떼면 130 · 6칸"),
									SkillInfo.stat("모으는 중", "이동 ×0.7 · 점프 불가 · 2.8초에 저절로 발동"),
									SkillInfo.stat("역경직", "맞히면 내 화면의 칼이 순간 멈췄다가 따라잡음")), false),
					new SkillInfo("RMB", "어깨 박치기", Overbreak.id("hud/skill/ironcleaver_bash"), null,
							"앞으로 6칸 몸통 돌진 — 처음 부딪친 적을 밀어냄",
							List.of(
									SkillInfo.stat("분류", "이동기 · 돌진"),
									SkillInfo.stat("돌진", "6칸 (0.3초)"),
									SkillInfo.stat("적중", "처음 부딪친 적 20 · 2칸 밀어냄 · 돌진 끝"),
									SkillInfo.stat("돌진 중", "받는 피해 ×0.5"),
									SkillInfo.stat("모으는 중", "쓸 수 있음 — 돌진하는 동안 모으기 시간은 멈춤"),
									SkillInfo.stat("균열 지대", "봉인됨"),
									SkillInfo.stat("재사용 대기시간", "7초")), false),
					new SkillInfo("SHIFT", "검막", Overbreak.id("hud/skill/ironcleaver_guard"), null,
							"1초 동안 대검을 세워 앞에서 오는 피해를 거의 막음",
							List.of(
									SkillInfo.stat("분류", "방어"),
									SkillInfo.stat("지속", "1초 · 움직일 수 없음"),
									SkillInfo.stat("막기", "앞 120°에서 온 피해 ×0.2"),
									SkillInfo.stat("막는 중 LMB", "막기를 끝내고 곧바로 모으기"),
									SkillInfo.stat("막기 성공", "1.5초 안에 시작한 모으기는 2단부터"),
									SkillInfo.stat("재사용 대기시간", "9초")), false),
					new SkillInfo("E", "대지 가르기", Overbreak.id("hud/skill/ironcleaver_rend"), null,
							"대검을 땅에 긁어 지면을 따라 달리는 칼날을 날림",
							List.of(
									SkillInfo.stat("분류", "투사체 · 관통"),
									SkillInfo.stat("시전", "0.3초 들고 0.2초 긁기 · 이동 ×0.5"),
									SkillInfo.stat("칼날", "초당 20칸 · 8칸 · 폭 2 × 높이 2.5 · 지면을 따라 1칸 턱까지 넘음"),
									SkillInfo.stat("피해", "25 + 1.5초 둔화 40%"),
									SkillInfo.stat("조건", "발밑 3칸 안에 땅이 있어야 함"),
									SkillInfo.stat("재사용 대기시간", "10초")), false),
					new SkillInfo("Q", "천참", Overbreak.id("hud/skill/ironcleaver_ult"), null,
							"1.2초 모았다가 앞 14칸을 벽째로 베어 버림",
							List.of(
									SkillInfo.stat("분류", "광역 · 직선"),
									SkillInfo.stat("모으기", "1.2초 · 움직일 수 없음 · 넉백 · 군중제어 면역"),
									SkillInfo.stat("예고", "베일 자리가 바닥에 붉게 표시됨"),
									SkillInfo.stat("판정", "앞 14칸 × 폭 4칸 · 벽 관통"),
									SkillInfo.stat("피해", "140"),
									SkillInfo.stat("후딜", "0.8초"),
									SkillInfo.stat("충전", "피해 1당 1%")), true)));

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("참철", ChatFormatting.WHITE);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new IronState();
		Classes.baseStats(p, HEALTH_BONUS, MOVE_SPEED, 400);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) p.sendSystemMessage(Component.empty()
				.append(Hud.text("[직업] ", ChatFormatting.WHITE))
				.append(Hud.bold("참철", ChatFormatting.WHITE))
				.append(Hud.text(" 을 선택했습니다.", ChatFormatting.WHITE)));
		Fx.sound(p, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.7F);
	}

	@Override
	public void remove(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
		IronState st = stateOrNull(p);
		if (st != null) {
			IronCombat.interrupt(p, st);
		}
		IronCombat.clearMods(p);
		Attachments.combatant(p).ccImmune = false;
	}

	@Override
	public void giveItems(ServerPlayer p) {
		Inventory inv = p.getInventory();
		inv.setItem(0, SkillItems.skill("overbreak:ironcleaver_greatsword", SkillItems.name("참철검", ChatFormatting.WHITE), BASH,
				SkillItems.lore()
						.line("직업 · 참철", ChatFormatting.DARK_GRAY).blank()
						.bold("[LMB 짧게] 3타 콤보", ChatFormatting.WHITE)
						.line(" 가로 · 가로 · 내려찍기 — 35 · 35 · 55", ChatFormatting.GRAY).blank()
						.bold("[LMB 누르기] 참 모으기", ChatFormatting.WHITE)
						.line(" 0.6초 40 · 1.2초 70 · 1.8초 110 · 1.8~2.0초에 떼면 진 참 130", ChatFormatting.GRAY).blank()
						.bold("[RMB] 어깨 박치기", ChatFormatting.WHITE)
						.line(" 6칸 돌진 · 처음 부딪친 적 20 + 2칸 밀어냄 · 모으는 중에도. 쿨타임 7초", ChatFormatting.GRAY).blank()
						.bold("[웅크리기] 검막", ChatFormatting.WHITE)
						.line(" 1초 동안 앞 120° 피해 ×0.2 · 막으면 다음 모으기가 2단부터. 쿨타임 9초", ChatFormatting.GRAY).blank()
						.bold("[E] 대지 가르기", ChatFormatting.WHITE)
						.line(" 지면을 따라 8칸 달리는 칼날 · 25 + 둔화 40%. 쿨타임 10초", ChatFormatting.GRAY).blank()
						.bold("[패시브] 중량", ChatFormatting.WHITE)
						.line(" 동작 중 넉백 -50% · 2단 이상 모아 베기는 피해 감소를 절반만 받게 함", ChatFormatting.GRAY).blank()
						.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		inv.setItem(9, SkillItems.statSheet(Hud.bold("참철", ChatFormatting.WHITE), SkillItems.lore()
				.line("대검 근접 결투형", ChatFormatting.DARK_GRAY).blank()
				.line("체력      260  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    35 · 35 · 55  (모아 베기 40~130)", ChatFormatting.GRAY)
				.line("이동속도  -5%", ChatFormatting.GRAY)
				.line("넉백      동작 중 -50%", ChatFormatting.GRAY).blank()
				.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("netherite_sword", Hud.bold("천참", ChatFormatting.RED), SkillItems.lore()
				.line("궁극기 · 참철", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 천참", ChatFormatting.RED)
				.line(" 1.2초 동안 움직이지 않고 모았다가 (넉백 · 군중제어 면역)", ChatFormatting.GRAY)
				.line(" 앞 14칸 × 폭 4칸을 벽째로 베어 140 피해.", ChatFormatting.GRAY)
				.line(" 베일 자리는 바닥에 붉게 표시됩니다.", ChatFormatting.RED).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	@Override
	public void primary(ServerPlayer p) {
		IronCombat.bash(p, state(p));
	}

	@Override
	public void secondary(ServerPlayer p) {
		IronCombat.guard(p, state(p));
	}

	@Override
	public void tertiary(ServerPlayer p) {
		IronCombat.rend(p, state(p));
	}

	@Override
	public void ult(ServerPlayer p) {
		IronCombat.ult(p, state(p));
	}

	/**
	 * 평타는 LMB 누름 · 뗌 신호(InputTiming)로 따로 돕니다 — 좌클릭 경로는 늘 여기서 끝.
	 * 모드가 없는 클라이언트는 누르고 있기를 알 수 없어 짧게 누른 휘두르기만 씁니다.
	 * 모으는 중에는 박치기만, 다른 동작 중에는 스킬을 받지 않습니다 (궁극기는 모으는 중에도 — 모으기를 버리고).
	 */
	@Override
	public boolean intercept(ServerPlayer p, InputRouter.Slot slot) {
		IronState st = state(p);
		if (slot == InputRouter.Slot.BASIC) {
			if (!InputModePayload.canSend(p)) {
				if (st.phase == IronState.Phase.IDLE) {
					IronCombat.startSwing(p, st, st.combo, GameClock.now(), false);
				} else if (st.phase == IronState.Phase.SWING) {
					st.buffered = true;
				}
			}
			return true;
		}
		return switch (st.phase) {
			case IDLE -> false;
			case CHARGE -> slot != InputRouter.Slot.PRIMARY && slot != InputRouter.Slot.ULT;
			default -> true;
		};
	}

	/** 천참 도중에는 게이지가 차지 않습니다. */
	@Override
	public boolean ultCharging(ServerPlayer p) {
		IronState st = stateOrNull(p);
		return st == null || st.phase != IronState.Phase.ULT;
	}

	@Override
	public boolean meleeAllowed() {
		return false;
	}

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
		IronState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(BASH), BASH_COOLDOWN, st.phase == IronState.Phase.BASH),
				new SkillSlot(prof.cooldown(GUARD), GUARD_COOLDOWN, st.phase == IronState.Phase.GUARD),
				new SkillSlot(prof.cooldown(REND), REND_COOLDOWN, st.phase == IronState.Phase.REND));
	}

	/**
	 * 조준점 아래: 모으기 게이지 (0~100 = 0~2.0초, 단계 눈금 · 진 참 창은 클라이언트가 그림).
	 * 스택 칸: 다음 콤보 타수 (3칸). 검막 보상이 남아 있으면 게이지 종류로 알림.
	 */
	@Override
	public HudExtra hudExtra(ServerPlayer p) {
		IronState st = state(p);
		int charge = IronCombat.chargePercent(p, st);
		int next = st.phase == IronState.Phase.SWING ? st.swing + 1 : st.combo;
		int flags = st.rewardT > 0 ? HudExtra.FLAG_IRON_REWARD : 0;
		return new HudExtra(-1, 0, charge, charge >= 0 ? HudExtra.METER_IRON : 0, next, 3, flags);
	}

	@Override
	public void tick(ServerPlayer p) {
		IronState st = state(p);
		Cooldowns.tick(p, TOTALS);
		IronCombat.tick(p, st);
	}

	// ── 받는 피해 ──────────────────────────────────────────

	/** 검막 (앞 120° ×0.2 · 막으면 보상) · 박치기 돌진 중 ×0.5. */
	private static float modify(LivingEntity target, DamageSource source, float damage) {
		if (!(target instanceof ServerPlayer p) || damage <= 0.0F) {
			return damage;
		}
		IronState st = stateOrNull(p);
		if (st == null) {
			return damage;
		}
		if (st.phase == IronState.Phase.GUARD) {
			Vec3 from = source.getSourcePosition();
			if (from != null && IronCombat.guardFront(p, st, from)) {
				st.guardSuccess = true;
				float yaw = (float) Math.toDegrees(Math.atan2(-(from.x - p.getX()), from.z - p.getZ()));
				IronPayload.broadcast(p, new IronPayload(IronPayload.BLOCK, p.getId(), 0, 0, from.x, from.y, from.z, yaw));
				return damage * GUARD_TAKEN;
			}
		}
		if (st.phase == IronState.Phase.BASH) {
			return damage * BASH_TAKEN;
		}
		return damage;
	}

	public static IronState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof IronState)) {
			prof.classState = new IronState();
		}
		return prof.state();
	}

	static @Nullable IronState stateOrNull(ServerPlayer p) {
		return Attachments.profile(p).classState instanceof IronState st ? st : null;
	}
}
