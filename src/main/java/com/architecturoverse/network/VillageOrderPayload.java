package com.architecturoverse.network;

import com.architecturoverse.Architecturoverse;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the ruler orders a village's builders to construct something ({@link com.architecturoverse.kingdom.BuildOrder} id). */
public record VillageOrderPayload(int villageId, int order) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<VillageOrderPayload> TYPE =
		new CustomPacketPayload.Type<>(Architecturoverse.id("village_order"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VillageOrderPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, VillageOrderPayload::villageId,
		ByteBufCodecs.VAR_INT, VillageOrderPayload::order,
		VillageOrderPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
