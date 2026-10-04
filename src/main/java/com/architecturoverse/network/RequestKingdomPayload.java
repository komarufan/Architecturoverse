package com.architecturoverse.network;

import com.architecturoverse.Architecturoverse;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the ruler pressed the kingdom key and wants a fresh snapshot. */
public record RequestKingdomPayload() implements CustomPacketPayload {
	public static final RequestKingdomPayload INSTANCE = new RequestKingdomPayload();
	public static final CustomPacketPayload.Type<RequestKingdomPayload> TYPE =
		new CustomPacketPayload.Type<>(Architecturoverse.id("request_kingdom"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RequestKingdomPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
