package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 서버 → 공격자 클라이언트: 내 피해가 들어갔다 (조준점 히트 마커 · 피격음).
 *
 * 평타 · 스킬 · 출혈 폭발 등 공격자가 기록된 피해는 모두 해당합니다. kill = 그 피해로 대상이 쓰러짐, crit = 치명타(머리).
 */
public record HitPayload(float damage, boolean kill, boolean crit) implements CustomPacketPayload {
	public static final Type<HitPayload> TYPE = new Type<>(Overbreak.id("hit"));
	public static final StreamCodec<ByteBuf, HitPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, HitPayload::damage,
			ByteBufCodecs.BOOL, HitPayload::kill,
			ByteBufCodecs.BOOL, HitPayload::crit,
			HitPayload::new);

	/** 치명타 피해를 넣는 동안만 켭니다 (피해 이벤트에는 머리에 맞았는지가 없으므로 넣는 쪽이 알려 줌). */
	public static boolean critNext;

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
			if (taken <= 0.0F || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == target) {
				return;
			}
			if (ServerPlayNetworking.canSend(attacker, TYPE)) {
				ServerPlayNetworking.send(attacker, new HitPayload(taken, target.isDeadOrDying(), critNext));
			}
		});
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
