package kr.overbreak.core;

import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** 엔티티에 붙는 상태 두 가지. 둘 다 비저장입니다. */
public final class Attachments {
	public static final AttachmentType<Combatant> COMBATANT =
			AttachmentRegistry.createDefaulted(Overbreak.id("combatant"), Combatant::new);

	public static final AttachmentType<PlayerProfile> PROFILE =
			AttachmentRegistry.createDefaulted(Overbreak.id("profile"), PlayerProfile::new);

	private Attachments() {}

	/** 클래스를 불러 정적 필드를 등록시키는 용도. */
	public static void init() {}

	public static Combatant combatant(LivingEntity e) {
		return e.getAttachedOrCreate(COMBATANT);
	}

	public static PlayerProfile profile(ServerPlayer p) {
		return p.getAttachedOrCreate(PROFILE);
	}
}
