package kr.overbreak.net;

import io.netty.buffer.ByteBuf;
import kr.overbreak.Overbreak;
import kr.overbreak.input.InputTiming;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 클라이언트 → 서버: 스킬 키를 누르거나 뗀 정확한 시점 (서브틱).
 *
 * slot     0 평타(좌클릭) · 1 액티브1(우클릭) · 2 액티브2(웅크리기) · 3 액티브3(F) · 4 궁극기(Q)
 * pressed  누름 / 뗌
 * subTick  지난 클라이언트 틱에서 다음 틱까지 몇 % 지점에서 바뀌었나 (0.0 ~ 1.0, 화면 프레임 단위로 잼)
 *
 * 스킬 발동 자체는 기존 경로(좌클릭 · 사용 · 웅크리기 · 손 바꾸기 · 버리기)가 하고, 이 패킷은 "언제" 만 알려 줍니다.
 * 서버는 값을 믿지 않고 범위로 자르고 너무 잦으면 버립니다 ({@link InputTiming}).
 */
public record SkillInputPayload(int slot, boolean pressed, float subTick) implements CustomPacketPayload {
	public static final Type<SkillInputPayload> TYPE = new Type<>(Overbreak.id("skill_input"));
	public static final StreamCodec<ByteBuf, SkillInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SkillInputPayload::slot,
			ByteBufCodecs.BOOL, SkillInputPayload::pressed,
			ByteBufCodecs.FLOAT, SkillInputPayload::subTick,
			SkillInputPayload::new);

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
				context.server().execute(() -> InputTiming.receive(context.player(), payload)));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
