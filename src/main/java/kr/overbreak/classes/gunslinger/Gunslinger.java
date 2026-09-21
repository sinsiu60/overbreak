package kr.overbreak.classes.gunslinger;

import java.util.List;
import java.util.Map;

import kr.overbreak.Overbreak;
import kr.overbreak.classes.ClassInfo;
import kr.overbreak.classes.Classes;
import kr.overbreak.classes.PvpClass;
import kr.overbreak.classes.SkillInfo;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.core.tick.Ticks;
import kr.overbreak.input.InputRouter;
import kr.overbreak.item.SkillItems;
import kr.overbreak.skill.Cooldowns;
import kr.overbreak.skill.Effects;
import kr.overbreak.skill.HudExtra;
import kr.overbreak.skill.SkillSlot;
import kr.overbreak.util.Fx;
import kr.overbreak.util.Hud;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 직업 9 · 궤적의 깃털 — 건슬링어 (공중 기동 원거리형).
 *
 *   체력 180 · 낙하 피해 없음 · 근접 불가 · 게이지 피해 1당 1%
 *   공중에서 싸우는 직업입니다: 땅에서 1.5칸 이상 떠서 맞힌 총알은 무조건 치명타이며,
 *   그 치명타가 다시 반동 도약 · 사선 앵커의 쿨타임을 깎아 공중에 더 오래 머물게 합니다.
 *
 * 조작 (모드 공통 배치에 맞춤)
 *   LMB         쌍권총 연사
 *   RMB         반동 도약
 *   SHIFT       돌진 난사 (활공은 점프 키로 옮겨 웅크리기가 비었습니다)
 *   E           사선 앵커
 *   Q           궤적 해방
 *   R           재장전
 */
public final class Gunslinger implements PvpClass {
	public static final String ID = "gunslinger";
	public static final String BOOST = "gs_boost";
	public static final String SCATTER = "gs_scatter";
	public static final String ANCHOR = "gs_anchor";
	private static final Map<String, Integer> TOTALS =
			Map.of(BOOST, RecoilBoost.COOLDOWN, SCATTER, DashScatter.COOLDOWN, ANCHOR, WireAnchor.COOLDOWN);
	private static final int SKY = 0x7FD4FF;

	static {
		// 낙하 피해 면역 (늘)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !absorb(entity, source));
		// 돌진 난사 도중에 시전자를 보기 시작한 사람에게는 지난 만큼 건너뛰어 동작을 보냅니다
		net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
			if (entity instanceof ServerPlayer caster && stateOrNull(caster) instanceof GunslingerState rs && rs.release != null) {
				rs.release.sendTo(viewer);
			}
			if (entity instanceof ServerPlayer caster && stateOrNull(caster) instanceof GunslingerState st && st.scatter != null) {
				kr.overbreak.net.ScatterPayload.sendTo(viewer, caster, st.scatter.seed(), st.scatter.dashYaw());
				kr.overbreak.net.SkillAnimPayload.sendTo(viewer, caster, kr.overbreak.net.SkillAnimPayload.GS_SCATTER,
						DashScatter.LENGTH, st.scatter.elapsedTime());
			}
		});
	}

	private static final ClassInfo INFO = new ClassInfo("궤적의 깃털", "공중 기동 원거리형", SKY,
			"쏜 반동으로 날아다니는 곡예 사수. 땅에 발을 붙이는 순간 약해지고, 땅에서 1.5칸 이상 떠 있는 동안은 모든 총알이 치명타입니다.",
			List.of(
					SkillInfo.stat("체력", "180"),
					SkillInfo.stat("공격력", "발당 20 (공중 30)"),
					SkillInfo.stat("공격속도", "0.2초에 1발"),
					SkillInfo.stat("탄창", "18발 · 1.25초 재장전"),
					SkillInfo.stat("낙하 피해", "받지 않음")),
			List.of(
					new SkillInfo("패시브", "체공 훈풍", null, "minecraft:feather",
							"떨어지기 시작하면 점프 키로 활공하고, 1.5칸 이상 떠서 맞힌 총알은 무조건 치명타",
							List.of(
									SkillInfo.stat("활공", "떨어지기 시작한 뒤 점프 키를 누르고 있기 — 낙하 속도 -80%"),
									SkillInfo.stat("활공 시간", "2초 · 반동 도약 · 사선 앵커를 쓸 때마다 +1초 · 착지하면 2초로"),
									SkillInfo.stat("공중 치명타", "땅에서 1.5칸 이상 · 150% (평타 30 · 돌진 난사 18)"),
									SkillInfo.stat("쿨타임 환급", "공중 명중마다 반동 도약 · 사선 앵커 -0.5초"),
									SkillInfo.stat("낙하 피해", "언제나 받지 않음")), false),
					new SkillInfo("LMB", "쌍권총 연사", Overbreak.id("hud/skill/gunslinger_pistols"), null,
							"16칸 히트스캔을 좌우 총구로 번갈아 퍼부음",
							List.of(
									SkillInfo.stat("분류", "히트스캔 · 탄창"),
									SkillInfo.stat("피해", "발당 20 · 공중 30"),
									SkillInfo.stat("사거리", "16칸 · 거리 감소 없음"),
									SkillInfo.stat("사격 속도", "0.2초에 1발"),
									SkillInfo.stat("탄창", "18발 · R 또는 다 쓰면 1.25초 재장전"),
									SkillInfo.stat("넉백", "없음")), false),
					new SkillInfo("RMB", "반동 도약", Overbreak.id("hud/skill/gunslinger_boost"), null,
							"조준한 곳에 충격탄을 쏘고 그 반동으로 정반대로 날아감",
							List.of(
									SkillInfo.stat("분류", "광역 · 이동기"),
									SkillInfo.stat("폭발", "조준선 5칸 앞 (벽이면 벽)"),
									SkillInfo.stat("피해", "반경 3칸 25 + 넉백"),
									SkillInfo.stat("도약", "조준 반대 방향 — 바닥을 보면 약 7칸"),
									SkillInfo.stat("균열 지대", "봉인됨"),
									SkillInfo.stat("재사용 대기시간", "6초 (궤적 해방 중에는 1.5초)")), false),
					new SkillInfo("SHIFT", "돌진 난사", Overbreak.id("hud/skill/gunslinger_acro"), null,
							"바라보는 방향으로 6칸 치고 나가 멈춘 뒤, 분신이 8칸 안을 휘저으며 반경 5칸을 쓸어 버림",
							List.of(
									SkillInfo.stat("분류", "이동기 · 광역"),
									SkillInfo.stat("돌진", "바라보는 방향 6칸 (0.25초) · 땅에서 아래를 보면 정면 · 적을 뚫고 지나감"),
									SkillInfo.stat("난사", "0.15초마다 6번 · 반경 5칸 안 모든 적 (벽 너머 제외)"),
									SkillInfo.stat("피해", "한 번당 12 (전부 72 · 공중 108)"),
									SkillInfo.stat("난사 중", "이동 속도 ×0.5 · 넉백 없음 · 공중이면 떠 있음"),
									SkillInfo.stat("시점", "쓰는 동안 3인칭 고정 (F5 로도 1인칭으로 안 바뀜) · 끝나면 원래대로"),
									SkillInfo.stat("끊김", "기절 · 에어본 — 난사 전이면 난사 없이 끝"),
									SkillInfo.stat("균열 지대", "봉인됨 (쿨타임 안 씀)"),
									SkillInfo.stat("재사용 대기시간", "8초 (쓰는 즉시)")), false),
					new SkillInfo("E", "사선 앵커", Overbreak.id("hud/skill/gunslinger_anchor"), null,
							"16칸 와이어를 쏘아 붙는 곳으로 끌려감",
							List.of(
									SkillInfo.stat("분류", "이동기 · 군중제어"),
									SkillInfo.stat("사거리", "16칸"),
									SkillInfo.stat("적중", "20 피해 · 0.5초 기절 · 그 적의 머리 위로"),
									SkillInfo.stat("벽 적중", "그 지점으로 당겨짐"),
									SkillInfo.stat("헛방", "쿨타임 절반만"),
									SkillInfo.stat("점프로 끊기", "끌려가는 중 점프 키 — 와이어를 끊고 그 속도 그대로 날아감"),
									SkillInfo.stat("균열 지대", "봉인됨"),
									SkillInfo.stat("재사용 대기시간", "7초")), false),
					new SkillInfo("Q", "궤적 해방", Overbreak.id("hud/skill/gunslinger_ult"), null,
							"5초 동안 쏜 총알의 궤적을 공중에 남겼다가 한꺼번에 터뜨림",
							List.of(
									SkillInfo.stat("분류", "광역 · 지연 폭발"),
									SkillInfo.stat("발동", "즉시 · 5초 동안 탄창 무한 · 재장전 없음"),
									SkillInfo.stat("궤적", "평타 1발당 1줄 · 최대 25줄 · 총구 → 적중 지점 / 벽 / 16칸"),
									SkillInfo.stat("치명 궤적", "공중(1.5칸 이상)에서 쏜 궤적"),
									SkillInfo.stat("해방", "5초 뒤 또는 1초 뒤부터 Q · 0.5초 예고 (사격 불가)"),
									SkillInfo.stat("폭발", "궤적 1칸 안 적에게 줄당 12 (치명 18) · 적 1명당 최대 120"),
									SkillInfo.stat("반동 도약", "궁극기 동안 쿨타임 1.5초"),
									SkillInfo.stat("대응", "시전자가 죽으면 폭발 없이 사라짐"),
									SkillInfo.stat("충전", "피해 1당 1% · 궁극기 중에는 차지 않음")), true)));

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Hud.bold("궤적의 깃털", ChatFormatting.AQUA);
	}

	@Override
	public void give(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		prof.classState = new GunslingerState();
		Classes.baseStats(p, -20, 0.0, 500);
		for (String k : TOTALS.keySet()) {
			prof.setCooldown(k, 0);
		}
		giveItems(p);
		if (Classes.announce) p.sendSystemMessage(Component.empty()
				.append(Hud.text("[직업] ", ChatFormatting.AQUA))
				.append(Hud.bold("궤적의 깃털", ChatFormatting.AQUA))
				.append(Hud.text(" 을 선택했습니다.", ChatFormatting.WHITE)));
		Fx.sound(p, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	@Override
	public void remove(ServerPlayer p) {
		Effects.cancelOwnedBy(p);
		GunslingerState st = stateOrNull(p);
		if (st != null) {
			DualPistols.cancelReload(p, st);
			AeroDrift.stop(p, st);
			st.scatter = null;
			st.release = null;
		}
		Attachments.combatant(p).ccImmune = false;
	}

	@Override
	public void giveItems(ServerPlayer p) {
		Inventory inv = p.getInventory();
		inv.setItem(0, SkillItems.skill("overbreak:gunslinger_pistols", SkillItems.name("쌍권총", ChatFormatting.AQUA), BOOST,
				SkillItems.lore()
						.line("직업 · 궤적의 깃털", ChatFormatting.DARK_GRAY).blank()
						.bold("[LMB] 쌍권총 연사", ChatFormatting.AQUA)
						.line(" 16칸 히트스캔 · 발당 20 · 0.2초에 1발. 탄창 18발 (R 재장전 1.25초)", ChatFormatting.GRAY)
						.line(" 땅에서 1.5칸 이상 떠서 맞히면 치명타 150% (30).", ChatFormatting.GRAY).blank()
						.bold("[RMB] 반동 도약", ChatFormatting.AQUA)
						.line(" 조준한 곳에 반경 3칸 25 + 넉백, 그 반동으로 정반대로 약 7칸. 쿨타임 6초", ChatFormatting.GRAY).blank()
						.bold("[웅크리기] 돌진 난사", ChatFormatting.AQUA)
						.line(" 바라보는 방향으로 6칸 돌진 → 제동 → 반경 5칸에 0.15초마다 6번 · 한 번당 12. 쿨타임 8초", ChatFormatting.GRAY).blank()
						.bold("[E] 사선 앵커", ChatFormatting.AQUA)
						.line(" 16칸 와이어 · 적중 20 + 0.5초 기절 + 머리 위로 · 벽이면 그 자리로. 쿨타임 7초", ChatFormatting.GRAY)
						.line(" 끌려가는 중 점프 키를 누르면 와이어를 끊고 그 속도 그대로 날아갑니다.", ChatFormatting.GRAY).blank()
						.bold("[패시브] 체공 훈풍", ChatFormatting.AQUA)
						.line(" 떨어지기 시작한 뒤 점프 키를 누르고 있으면 2초 활공 (반동 도약 · 사선 앵커마다 +1초)", ChatFormatting.GRAY)
						.line(" 1.5칸 이상 떠서 맞히면 치명타 · 이동기 쿨타임 -0.5초 · 낙하 피해 없음", ChatFormatting.GRAY).blank()
						.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		// 쌍권총 — 양손에 한 자루씩. 왼손 총은 모습만 있는 소품이라 버리기 · 칸 고정에 걸리지 않습니다
		inv.setItem(Inventory.SLOT_OFFHAND, SkillItems.prop("overbreak:gunslinger_pistols",
				SkillItems.name("쌍권총 (왼손)", ChatFormatting.AQUA)));
		inv.setItem(9, SkillItems.statSheet(Hud.bold("궤적의 깃털", ChatFormatting.AQUA), SkillItems.lore()
				.line("공중 기동 원거리형", ChatFormatting.DARK_GRAY).blank()
				.line("체력      180  (기본 200)", ChatFormatting.GRAY)
				.line("공격력    발당 20  (공중 30)", ChatFormatting.GRAY)
				.line("공격속도  0.2초에 1발", ChatFormatting.GRAY)
				.line("이동속도  기본", ChatFormatting.GRAY)
				.line("탄창      18발", ChatFormatting.GRAY)
				.line("낙하 피해 받지 않음", ChatFormatting.GRAY).blank()
				.line("F8 로 스킬 설명을 볼 수 있습니다.", ChatFormatting.DARK_GRAY).build()));
		Attachments.profile(p).barShown.clear();
	}

	@Override
	public ItemStack ultItem() {
		return SkillItems.ult("feather", Hud.bold("궤적 해방", ChatFormatting.AQUA), SkillItems.lore()
				.line("궁극기 · 궤적의 깃털", ChatFormatting.DARK_GRAY).blank()
				.bold("[아이템 버리기 Q] 궤적 해방", ChatFormatting.AQUA)
				.line(" 5초 동안 탄창 무한 · 쏜 총알의 궤적이 하늘색 선으로 공중에 남습니다 (최대 25줄).", ChatFormatting.GRAY)
				.line(" 시간이 다 되거나 1초 뒤 Q 를 다시 누르면 0.5초 예고 후 모든 궤적이 동시에 폭발.", ChatFormatting.GRAY)
				.line(" 궤적 1칸 안의 적에게 줄당 12 (공중에서 쏜 궤적 18), 적 1명당 최대 120.", ChatFormatting.GRAY)
				.line(" 궁극기 동안 반동 도약 쿨타임 1.5초.", ChatFormatting.GRAY)
				.line(" 쓰는 사람이 죽으면 궤적은 폭발 없이 사라집니다.", ChatFormatting.RED).build());
	}

	// ── 스킬 ────────────────────────────────────────────────

	@Override
	public void basic(ServerPlayer p) {
		DualPistols.fire(p, state(p));
	}

	/** 우클릭 — 반동 도약. */
	@Override
	public void primary(ServerPlayer p) {
		RecoilBoost.cast(p, state(p));
	}

	/** 웅크리기 — 돌진 난사. 활공은 점프 키라 서로 겹치지 않습니다. */
	@Override
	public void secondary(ServerPlayer p) {
		DashScatter.cast(p, state(p));
	}

	@Override
	public void tertiary(ServerPlayer p) {
		WireAnchor.cast(p, state(p));
	}

	@Override
	public void ult(ServerPlayer p) {
		TrailRelease.cast(p, state(p));
	}

	@Override
	public void reload(ServerPlayer p) {
		DualPistols.manualReload(p, state(p));
	}

	@Override
	public boolean reloading(ServerPlayer p) {
		GunslingerState st = stateOrNull(p);
		return st != null && st.reloadT > 0;
	}

	/**
	 * 궤적 해방 중 Q 는 조기 해방 (발동 1초 뒤부터 — 그 전에는 무시).
	 * 돌진 난사는 궁극기 동안 쓰지 않습니다 (평타 · 반동 도약 · 사선 앵커는 그대로).
	 */
	@Override
	public boolean intercept(ServerPlayer p, InputRouter.Slot slot) {
		GunslingerState st = state(p);
		if (st.release == null) {
			return false;
		}
		if (slot == InputRouter.Slot.ULT) {
			st.release.requestRelease();
			return true;
		}
		return slot == InputRouter.Slot.SECONDARY;
	}

	/** 궤적 해방 중에는 게이지가 차지 않습니다 (평타 · 폭발 모두). */
	@Override
	public boolean ultCharging(ServerPlayer p) {
		GunslingerState st = stateOrNull(p);
		return st == null || st.release == null;
	}

	@Override
	public boolean meleeAllowed() {
		return false;
	}

	/** 피해 1당 게이지 1%. */
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
		GunslingerState st = state(p);
		return List.of(
				new SkillSlot(prof.cooldown(BOOST), st.release != null && st.release.collecting() ? TrailRelease.BOOST_COOLDOWN : RecoilBoost.COOLDOWN, false),
				new SkillSlot(prof.cooldown(SCATTER), DashScatter.COOLDOWN, st.scatter != null),
				new SkillSlot(prof.cooldown(ANCHOR), WireAnchor.COOLDOWN, false));
	}

	/** 무기 칸 옆: 탄창 (N/18). 조준점 아래: 재장전 · 궤적 해방 남은 시간 · 활공 남은 시간. */
	@Override
	public HudExtra hudExtra(ServerPlayer p) {
		GunslingerState st = state(p);
		if (st.release != null) {
			// 궤적 수 · 치명 궤적 수 · 해방 안내는 클라이언트가 받은 궤적으로 직접 그립니다 (client/fx/TrailView)
			return new HudExtra(st.ammo, DualPistols.MAG, st.release.remainingPercent(), HudExtra.METER_DURATION);
		}
		if (st.reloadT > 0) {
			int reload = Ticks.of(DualPistols.RELOAD);
			return new HudExtra(st.ammo, DualPistols.MAG, (reload - st.reloadT) * 100 / reload, HudExtra.METER_RELOAD);
		}
		if (st.gliding) {
			return new HudExtra(st.ammo, DualPistols.MAG, Math.min(100, st.glideT * 100 / Math.max(1, Ticks.of(AeroDrift.GLIDE))), HudExtra.METER_DURATION);
		}
		return new HudExtra(st.ammo, DualPistols.MAG, -1, 0);
	}

	@Override
	public void tick(ServerPlayer p) {
		GunslingerState st = state(p);
		Cooldowns.tick(p, TOTALS);
		DualPistols.tick(p, st);
		AeroDrift.tick(p, st);
		// 낙하 피해를 아예 받지 않는 직업이라 낙하 거리를 쌓아 두지 않습니다
		p.resetFallDistance();
	}

	// ── 공통 ────────────────────────────────────────────────

	/**
	 * 피해를 받지 않는 경우 — 낙하 피해 (늘).
	 * 시험에서 직접 부릅니다.
	 */
	public static boolean absorb(LivingEntity target, DamageSource source) {
		if (!(target instanceof ServerPlayer sp) || !(Attachments.profile(sp).classState instanceof GunslingerState st)) {
			return false;
		}
		return source.is(DamageTypeTags.IS_FALL);
	}

	public static GunslingerState state(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (!(prof.classState instanceof GunslingerState)) {
			prof.classState = new GunslingerState();
		}
		return prof.state();
	}

	static @Nullable GunslingerState stateOrNull(ServerPlayer p) {
		return Attachments.profile(p).classState instanceof GunslingerState st ? st : null;
	}
}
