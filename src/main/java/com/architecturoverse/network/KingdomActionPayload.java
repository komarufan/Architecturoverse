package com.architecturoverse.network;

import com.architecturoverse.Architecturoverse;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: one of the ruler's orders from the kingdom screen that is not about a
 * single citizen's job. {@code villageId} is -1 for "all villages" where that makes sense.
 */
public record KingdomActionPayload(Action action, int villageId, int value, Optional<UUID> citizen) implements CustomPacketPayload {
	public enum Action {
		/** value: structure type id. */
		BUILD_STRUCTURE,
		/** value: army order id; a rally point is the ruler's current position. */
		ARMY_ORDER,
		/** value: 1 to condemn, 0 to pardon. */
		SENTENCE,
		/** Calls the travelling merchant to the village. */
		CALL_TRADER,
		/** Sends the village soldiers after the merchant. */
		ROB_TRADER
	}

	public static final CustomPacketPayload.Type<KingdomActionPayload> TYPE =
		new CustomPacketPayload.Type<>(Architecturoverse.id("kingdom_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, KingdomActionPayload> CODEC =
		StreamCodec.ofMember(KingdomActionPayload::write, KingdomActionPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeEnum(action);
		buf.writeVarInt(villageId);
		buf.writeVarInt(value);
		buf.writeBoolean(citizen.isPresent());
		citizen.ifPresent(buf::writeUUID);
	}

	private static KingdomActionPayload read(RegistryFriendlyByteBuf buf) {
		return new KingdomActionPayload(buf.readEnum(Action.class), buf.readVarInt(), buf.readVarInt(),
			buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
