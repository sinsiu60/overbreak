package kr.overbreak.client.hud;

import java.util.List;
import java.util.Map;

import kr.overbreak.Overbreak;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 직업별 스킬 HUD 배치 — 아이콘과 키 표시. 칸 순서는 서버 PvpClass.hudSlots 와 같습니다 (우클릭 · 웅크리기 · F).
 *
 * 아이콘: assets/overbreak/textures/gui/sprites/hud/skill/&lt;직업&gt;_&lt;스킬&gt;.png (흰색 단색, 투명 배경)
 */
public final class HudLayouts {
	public record SlotDef(Identifier icon, String key) {}

	public record Layout(List<SlotDef> slots, Identifier ultIcon, int accent) {}

	private static final Map<String, Layout> BY_CLASS = Map.of(
			"thunder", new Layout(List.of(
					new SlotDef(icon("thunder_step"), "RMB"),
					new SlotDef(icon("thunder_field"), "SHIFT"),
					new SlotDef(icon("thunder_smite"), "F")),
					icon("thunder_ult"), 0xFF5AD8FF),
			"shade", new Layout(List.of(
					new SlotDef(icon("shade_rend"), "RMB"),
					new SlotDef(icon("shade_evade"), "SHIFT"),
					new SlotDef(icon("shade_kunai"), "F")),
					icon("shade_ult"), 0xFF9B6BFF),
			"sheriff", new Layout(List.of(
					new SlotDef(icon("sheriff_fan"), "RMB"),
					new SlotDef(icon("sheriff_roll"), "SHIFT"),
					new SlotDef(icon("sheriff_flash"), "F")),
					icon("sheriff_ult"), 0xFFE8B04A),
			"valkyrie", new Layout(List.of(
					new SlotDef(icon("valkyrie_rocket"), "RMB"),
					new SlotDef(icon("valkyrie_spring"), "SHIFT"),
					new SlotDef(icon("valkyrie_overheat"), "F")),
					icon("valkyrie_ult"), 0xFFFFD24A),
			"ironfist", new Layout(List.of(
					new SlotDef(icon("ironfist_punch"), "RMB"),
					new SlotDef(icon("ironfist_block"), "SHIFT"),
					new SlotDef(icon("ironfist_slam"), "F")),
					icon("ironfist_ult"), 0xFF5AB4FF),
			"hammer_knight", new Layout(List.of(
					new SlotDef(icon("hammer_smash"), "RMB"),
					new SlotDef(icon("hammer_charge"), "SHIFT"),
					new SlotDef(icon("hammer_crush"), "F")),
					icon("hammer_ult"), 0xFFFFC23A),
			"brute", new Layout(List.of(
					new SlotDef(icon("brute_blow"), "RMB"),
					new SlotDef(icon("brute_whirl"), "SHIFT"),
					new SlotDef(icon("brute_regroup"), "F")),
					icon("brute_ult"), 0xFFFFA24A),
			"warrior", new Layout(List.of(
					new SlotDef(icon("warrior_slay"), "RMB"),
					new SlotDef(icon("warrior_fury"), "SHIFT"),
					new SlotDef(icon("warrior_chain"), "F")),
					icon("warrior_ult"), 0xFFFF5A4A));

	private HudLayouts() {}

	private static Identifier icon(String name) {
		return Overbreak.id("hud/skill/" + name);
	}

	public static @Nullable Layout get(String classId) {
		return BY_CLASS.get(classId);
	}
}
