package com.architecturoverse.kingdom;

import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenMode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * The kingdom's own register of a citizen, so the ruler can see and command
 * citizens that are in unloaded chunks. The record is the source of truth for
 * job, mode and sentence; loaded citizen entities copy it.
 */
public record CitizenRecord(UUID uuid, String name, CitizenJob job, CitizenMode mode, int villageId, boolean condemned) {
	public static final Codec<CitizenRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("uuid").forGetter(CitizenRecord::uuid),
		Codec.STRING.fieldOf("name").forGetter(CitizenRecord::name),
		CitizenJob.CODEC.fieldOf("job").forGetter(CitizenRecord::job),
		CitizenMode.CODEC.fieldOf("mode").forGetter(CitizenRecord::mode),
		Codec.INT.fieldOf("village").forGetter(CitizenRecord::villageId),
		Codec.BOOL.optionalFieldOf("condemned", false).forGetter(CitizenRecord::condemned)
	).apply(i, CitizenRecord::new));

	public CitizenRecord withJob(CitizenJob newJob) {
		return new CitizenRecord(uuid, name, newJob, mode, villageId, condemned);
	}

	public CitizenRecord withMode(CitizenMode newMode) {
		return new CitizenRecord(uuid, name, job, newMode, villageId, condemned);
	}

	public CitizenRecord withCondemned(boolean newCondemned) {
		return new CitizenRecord(uuid, name, job, mode, villageId, newCondemned);
	}
}
