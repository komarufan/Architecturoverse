package com.architecturoverse.citizen;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/** What a citizen does all day. The profession only controls how the citizen looks. */
public enum CitizenJob {
	UNEMPLOYED(VillagerProfession.NONE),
	SOLDIER(VillagerProfession.WEAPONSMITH);

	public static final Codec<CitizenJob> CODEC = Codec.STRING.xmap(CitizenJob::byName, CitizenJob::name);

	private final ResourceKey<VillagerProfession> look;

	CitizenJob(ResourceKey<VillagerProfession> look) {
		this.look = look;
	}

	public ResourceKey<VillagerProfession> look() {
		return look;
	}

	public Component displayName() {
		return Component.translatable("job.architecturoverse." + name().toLowerCase());
	}

	public CitizenJob next() {
		CitizenJob[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static CitizenJob byName(String name) {
		for (CitizenJob job : values()) {
			if (job.name().equals(name)) {
				return job;
			}
		}
		return UNEMPLOYED;
	}

	public static CitizenJob byId(int id) {
		CitizenJob[] all = values();
		return id >= 0 && id < all.length ? all[id] : UNEMPLOYED;
	}
}
