package com.architecturoverse.network;

import com.architecturoverse.Architecturoverse;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: change a citizen's job and/or mode. A value of -1 leaves that field unchanged. */
public record CitizenCommandPayload(UUID citizen, int job, int mode) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CitizenCommandPayload> TYPE =
		new CustomPacketPayload.Type<>(Architecturoverse.id("citizen_command"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CitizenCommandPayload> CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, CitizenCommandPayload::citizen,
		ByteBufCodecs.VAR_INT, CitizenCommandPayload::job,
		ByteBufCodecs.VAR_INT, CitizenCommandPayload::mode,
		CitizenCommandPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
