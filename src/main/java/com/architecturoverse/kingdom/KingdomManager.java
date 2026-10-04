package com.architecturoverse.kingdom;

import com.architecturoverse.Architecturoverse;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-wide register of all kingdoms, stored in data/architecturoverse/kingdoms.dat. */
public final class KingdomManager extends SavedData {
	private static final Codec<KingdomManager> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Kingdom.CODEC)
		.xmap(KingdomManager::new, m -> m.kingdoms);

	// Our data has no vanilla datafixers; command storage fixes leave unknown content alone.
	public static final SavedDataType<KingdomManager> TYPE = new SavedDataType<>(
		Architecturoverse.id("kingdoms"), KingdomManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	/** Two villages can not be claimed closer than this to each other. */
	public static final int MIN_VILLAGE_DISTANCE = ClaimedVillage.RADIUS + 16;

	private final Map<UUID, Kingdom> kingdoms;

	private KingdomManager() {
		this(Map.of());
	}

	private KingdomManager(Map<UUID, Kingdom> kingdoms) {
		this.kingdoms = new HashMap<>(kingdoms);
	}

	public static KingdomManager get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public Optional<Kingdom> kingdom(UUID owner) {
		return Optional.ofNullable(kingdoms.get(owner));
	}

	public java.util.List<Kingdom> all() {
		return java.util.List.copyOf(kingdoms.values());
	}

	public Kingdom getOrCreate(ServerPlayer player) {
		Kingdom kingdom = kingdoms.computeIfAbsent(player.getUUID(), id -> new Kingdom(id, player.getPlainTextName()));
		kingdom.ownerName = player.getPlainTextName();
		setDirty();
		return kingdom;
	}

	/** Finds the kingdom and village whose territory (expanded by {@code radius}) contains the position. */
	public Optional<Claim> claimAt(ResourceKey<Level> dimension, BlockPos pos, int radius) {
		for (Kingdom kingdom : kingdoms.values()) {
			for (ClaimedVillage village : kingdom.villages) {
				if (village.contains(dimension, pos, radius)) {
					return Optional.of(new Claim(kingdom, village));
				}
			}
		}
		return Optional.empty();
	}

	public ClaimedVillage addVillage(Kingdom kingdom, ResourceKey<Level> dimension, BlockPos center) {
		ClaimedVillage village = new ClaimedVillage(kingdom.nextVillageId++, dimension, center.immutable());
		kingdom.villages.add(village);
		setDirty();
		return village;
	}

	public Optional<ClaimedVillage> updateVillage(Kingdom kingdom, int villageId, UnaryOperator<ClaimedVillage> change) {
		for (int i = 0; i < kingdom.villages.size(); i++) {
			ClaimedVillage village = kingdom.villages.get(i);
			if (village.id() == villageId) {
				ClaimedVillage updated = change.apply(village);
				kingdom.villages.set(i, updated);
				setDirty();
				return Optional.of(updated);
			}
		}
		return Optional.empty();
	}

	/** The village of this ruler nearest to {@code pos} whose center is at most {@code range} blocks away. */
	public Optional<ClaimedVillage> nearestOwnVillage(UUID ruler, ResourceKey<Level> dimension, BlockPos pos, int range) {
		return kingdom(ruler).flatMap(k -> k.villages.stream()
			.filter(v -> v.contains(dimension, pos, range))
			.min((a, b) -> Double.compare(a.center().distSqr(pos), b.center().distSqr(pos))));
	}

	/** Unregisters any warehouse or mine at this position, e.g. after the block was broken. */
	public void forgetBlock(ResourceKey<Level> dimension, BlockPos pos) {
		for (Kingdom kingdom : kingdoms.values()) {
			for (int i = 0; i < kingdom.villages.size(); i++) {
				ClaimedVillage village = kingdom.villages.get(i);
				if (!village.dimension().equals(dimension)) {
					continue;
				}
				ClaimedVillage updated = village;
				if (village.warehouse().filter(pos::equals).isPresent()) {
					updated = updated.withWarehouse(Optional.empty());
				}
				if (village.mine().filter(m -> m.entrance().equals(pos)).isPresent()) {
					updated = updated.withMine(Optional.empty());
				}
				if (updated != village) {
					kingdom.villages.set(i, updated);
					setDirty();
				}
			}
		}
	}

	public void putCitizen(Kingdom kingdom, CitizenRecord record) {
		kingdom.citizens.put(record.uuid(), record);
		setDirty();
	}

	public Optional<CitizenRecord> updateCitizen(Kingdom kingdom, UUID citizen, UnaryOperator<CitizenRecord> change) {
		CitizenRecord old = kingdom.citizens.get(citizen);
		if (old == null) {
			return Optional.empty();
		}
		CitizenRecord updated = change.apply(old);
		kingdom.citizens.put(citizen, updated);
		setDirty();
		return Optional.of(updated);
	}

	public void removeCitizen(UUID owner, UUID citizen) {
		Kingdom kingdom = kingdoms.get(owner);
		if (kingdom != null && kingdom.citizens.remove(citizen) != null) {
			setDirty();
		}
	}

	public record Claim(Kingdom kingdom, ClaimedVillage village) {
	}
}
