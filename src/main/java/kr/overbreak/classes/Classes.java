package kr.overbreak.classes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kr.overbreak.Overbreak;
import kr.overbreak.cc.CrowdControl;
import kr.overbreak.core.Attachments;
import kr.overbreak.core.Combatant;
import kr.overbreak.core.PlayerProfile;
import kr.overbreak.item.SkillItems;
import kr.overbreak.ult.UltGauge;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

/** 직업 목록과 지급/해제 공통 절차. */
public final class Classes {
	public static final Identifier CLASS_HEALTH = Overbreak.id("class_health");
	/** 모든 직업의 기본 체력 (바닐라 20 의 10배). 직업 체력 보너스 · 피해 · 회복도 모두 10배 기준으로 씁니다. */
	public static final double BASE_HEALTH = 200.0;
	public static final Identifier CLASS_SPEED = Overbreak.id("class_speed");
	/** 달리기 대신 늘 달리기 속도 — 바닐라 달리기와 같은 +30% (최종값에 곱함). 달리기 자체는 막습니다 (GameLoop · 클라이언트 LocalPlayerSprintMixin). */
	public static final Identifier SPRINT_BASE = Overbreak.id("sprint_base");
	public static final double SPRINT_BASE_RATIO = 0.3;
	public static final Identifier NO_ATK_COOLDOWN = Overbreak.id("no_atk_cooldown");

	private static final Map<String, PvpClass> BY_ID = new LinkedHashMap<>();
	/** false 면 직업을 줄 때 "[직업] … 선택했습니다" 채팅을 보내지 않습니다 (대전 라운드마다 다시 줄 때). */
	public static boolean announce = true;

	private Classes() {}

	public static void init() {
		// 직업은 이전하는 대로 여기에 한 줄씩 추가합니다.
		register(new kr.overbreak.classes.warrior.Warrior());
		register(new kr.overbreak.classes.hammer.HammerKnight());
		register(new kr.overbreak.classes.ironfist.IronFist());
		register(new kr.overbreak.classes.valkyrie.Valkyrie());
		register(new kr.overbreak.classes.sheriff.Sheriff());
		register(new kr.overbreak.classes.shade.Shade());
		register(new kr.overbreak.classes.thunder.Thunder());
		register(new kr.overbreak.classes.brute.Brute());
	}

	public static void register(PvpClass c) {
		BY_ID.put(c.id(), c);
	}

	public static @Nullable PvpClass byId(String id) {
		return BY_ID.get(id);
	}

	public static List<PvpClass> all() {
		return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
	}

	/** 직업 지급 — 기존 직업을 해제한 뒤 새로 겁니다. */
	public static void give(ServerPlayer p, PvpClass c) {
		clear(p);
		PlayerProfile prof = Attachments.profile(p);
		prof.pvpClass = c;
		prof.ultRate = c.ultRate();
		prof.ultOn = c.usesGauge();
		c.give(p);
		UltGauge.reset(p);
	}

	/** 직업 해제 — 스탯 원복 · 진행 중 스킬 정리 · 직업 아이템 회수. */
	public static void clear(ServerPlayer p) {
		PlayerProfile prof = Attachments.profile(p);
		if (prof.pvpClass != null) {
			prof.pvpClass.remove(p);
		}
		prof.pvpClass = null;
		prof.classState = null;
		prof.ultOn = false;
		prof.ultRate = 200;
		prof.atkSpeed = 0;
		prof.atkCd = 0;
		prof.cooldowns.clear();
		prof.barShown.clear();
		UltGauge.reset(p);

		Combatant c = Attachments.combatant(p);
		c.dmgMul = 100;
		c.casting = false;
		c.rooted = false;
		CrowdControl.clearAll(p);

		CrowdControl.unmod(p, Attributes.MAX_HEALTH, CLASS_HEALTH);
		CrowdControl.unmod(p, Attributes.MOVEMENT_SPEED, CLASS_SPEED);
		CrowdControl.unmod(p, Attributes.MOVEMENT_SPEED, SPRINT_BASE);
		CrowdControl.unmod(p, Attributes.ATTACK_SPEED, NO_ATK_COOLDOWN);
		SkillItems.clearClassItems(p);
	}

	/** 공통 스탯. 체력은 기본 200 에 더하는 값(10배 기준), 이동속도는 비율(-0.07 = -7%). */
	public static void baseStats(ServerPlayer p, double healthBonus, double speedRatio, int atkSpeedX100) {
		// 바닐라 기본 20 → 200, 거기에 직업 보너스
		CrowdControl.mod(p, Attributes.MAX_HEALTH, CLASS_HEALTH, BASE_HEALTH - 20.0 + healthBonus, AttributeModifier.Operation.ADD_VALUE);
		if (speedRatio != 0) {
			CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, CLASS_SPEED, speedRatio, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
		CrowdControl.mod(p, Attributes.MOVEMENT_SPEED, SPRINT_BASE, SPRINT_BASE_RATIO, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// 바닐라 공격 쿨다운 게이지는 무한으로 고정합니다. 공격속도는 AttackSpeed 가 따로 셉니다.
		CrowdControl.mod(p, Attributes.ATTACK_SPEED, NO_ATK_COOLDOWN, 1000, AttributeModifier.Operation.ADD_VALUE);
		p.setHealth(p.getMaxHealth());
		Attachments.profile(p).atkSpeed = atkSpeedX100;
	}
}
