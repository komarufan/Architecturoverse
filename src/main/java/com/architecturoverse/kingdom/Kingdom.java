package com.architecturoverse.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** Everything one ruler owns. Mutate only through {@link KingdomManager} so changes get saved. */
public final class Kingdom {
	public static final Codec<Kingdom> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("owner").forGetter(k -> k.owner),
		Codec.STRING.fieldOf("owner_name").forGetter(k -> k.ownerName),
		Codec.INT.fieldOf("next_village_id").forGetter(k -> k.nextVillageId),
		ClaimedVillage.CODEC.listOf().fieldOf("villages").forGetter(k -> k.villages),
		CitizenRecord.CODEC.listOf().fieldOf("citizens").forGetter(k -> List.copyOf(k.citizens.values()))
	).apply(i, Kingdom::new));

	private final UUID owner;
	String ownerName;
	int nextVillageId;
	final List<ClaimedVillage> villages;
	final Map<UUID, CitizenRecord> citizens = new LinkedHashMap<>();

	Kingdom(UUID owner, String ownerName) {
		this(owner, ownerName, 1, List.of(), List.of());
	}

	private Kingdom(UUID owner, String ownerName, int nextVillageId, List<ClaimedVillage> villages, List<CitizenRecord> citizens) {
		this.owner = owner;
		this.ownerName = ownerName;
		this.nextVillageId = nextVillageId;
		this.villages = new ArrayList<>(villages);
		citizens.forEach(c -> this.citizens.put(c.uuid(), c));
	}

	public UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public List<ClaimedVillage> villages() {
		return List.copyOf(villages);
	}

	public Optional<ClaimedVillage> village(int id) {
		return villages.stream().filter(v -> v.id() == id).findFirst();
	}

	public List<CitizenRecord> citizens() {
		return List.copyOf(citizens.values());
	}

	public Optional<CitizenRecord> citizen(UUID uuid) {
		return Optional.ofNullable(citizens.get(uuid));
	}

	public int population(int villageId) {
		return (int) citizens.values().stream().filter(c -> c.villageId() == villageId).count();
	}
}
