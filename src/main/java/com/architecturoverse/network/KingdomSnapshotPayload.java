package com.architecturoverse.network;

import com.architecturoverse.Architecturoverse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: everything the kingdom screen shows. Sent when the ruler opens the book and after every order. */
public record KingdomSnapshotPayload(String ownerName, List<VillageInfo> villages, List<CitizenInfo> citizens, Optional<UUID> focus)
	implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<KingdomSnapshotPayload> TYPE =
		new CustomPacketPayload.Type<>(Architecturoverse.id("kingdom_snapshot"));

	public static final StreamCodec<RegistryFriendlyByteBuf, KingdomSnapshotPayload> CODEC =
		StreamCodec.ofMember(KingdomSnapshotPayload::write, KingdomSnapshotPayload::read);

	public record VillageInfo(int id, String dimension, BlockPos center, int population) {
	}

	public record CitizenInfo(UUID uuid, String name, int job, int mode, int villageId) {
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(ownerName);
		buf.writeCollection(villages, (b, v) -> {
			b.writeVarInt(v.id());
			b.writeUtf(v.dimension());
			b.writeBlockPos(v.center());
			b.writeVarInt(v.population());
		});
		buf.writeCollection(citizens, (b, c) -> {
			b.writeUUID(c.uuid());
			b.writeUtf(c.name());
			b.writeVarInt(c.job());
			b.writeVarInt(c.mode());
			b.writeVarInt(c.villageId());
		});
		buf.writeBoolean(focus.isPresent());
		focus.ifPresent(buf::writeUUID);
	}

	private static KingdomSnapshotPayload read(RegistryFriendlyByteBuf buf) {
		String ownerName = buf.readUtf();
		List<VillageInfo> villages = buf.readList(b -> new VillageInfo(b.readVarInt(), b.readUtf(), b.readBlockPos(), b.readVarInt()));
		List<CitizenInfo> citizens = buf.readList(b -> new CitizenInfo(b.readUUID(), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
		Optional<UUID> focus = buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty();
		return new KingdomSnapshotPayload(ownerName, villages, citizens, focus);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
