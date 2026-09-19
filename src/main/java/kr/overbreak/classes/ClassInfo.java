package kr.overbreak.classes;

import java.util.List;

/**
 * 직업 설명 — F8 스킬 설명 화면의 왼쪽 (직업 이름 · 역할 · 기본 능력치) 과 오른쪽 스킬 목록.
 *
 * @param name   직업 이름
 * @param role   역할 한 줄 (예: "근접 돌격형")
 * @param color  강조 색 (0xRRGGBB)
 * @param blurb  직업 소개 한두 문장
 * @param stats  기본 능력치 (항목, 값)
 * @param skills 위에서부터 표시할 스킬 순서
 */
public record ClassInfo(String name, String role, int color, String blurb, List<SkillInfo.Stat> stats, List<SkillInfo> skills) {}
