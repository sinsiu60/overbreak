package kr.overbreak.skill;

/**
 * 스킬 HUD 한 칸의 상태.
 *
 * @param remaining 남은 쿨타임 (틱)
 * @param total     전체 쿨타임 (틱) — 진행 막대용
 * @param active    효과가 켜져 있거나 시전 중 (HUD 에서 강조)
 */
public record SkillSlot(int remaining, int total, boolean active) {}
