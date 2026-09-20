package kr.overbreak.classes;

import java.util.List;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 스킬 설명 한 칸 — F8 스킬 설명 화면 (클라이언트 SkillInfoScreen) 에 쓰입니다.
 *
 * @param key     조작 표시 (예: "패시브", "LMB", "RMB", "SHIFT", "E", "Q")
 * @param name    스킬 이름
 * @param icon    HUD 스프라이트 아이콘 (흰색 단색). 없으면 item 을 아이템 아이콘으로 그림
 * @param item    아이콘으로 쓸 아이템 id (예: "minecraft:iron_axe"). icon 이 있으면 무시
 * @param summary 겉으로 보이는 대략적인 설명 (한두 문장)
 * @param details 마우스를 올리면 보이는 세부 수치 (항목, 값)
 * @param ult     궁극기인가 (금색 강조)
 */
public record SkillInfo(String key, String name, @Nullable Identifier icon, @Nullable String item, String summary,
						List<Stat> details, boolean ult) {
	public record Stat(String label, String value) {}

	public static Stat stat(String label, String value) {
		return new Stat(label, value);
	}
}
