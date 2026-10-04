package com.architecturoverse.kingdom;

import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenMode;
import com.architecturoverse.citizen.CitizenStatus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * The kingdom's own register of a citizen, so the ruler can see and command
 * citizens that are in unloaded chunks. The record is the source of truth for
 * job, mode and status; loaded citizen entities copy it.
 */
public record CitizenRecord(UUID uuid, String name, CitizenJob job, CitizenMode mode, int villageId, CitizenStatus status) {
	public static final Codec<CitizenRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("uuid").forGetter(CitizenRecord::uuid),
		Codec.STRING.fieldOf("name").forGetter(CitizenRecord::name),
		CitizenJob.CODEC.fieldOf("job").forGetter(CitizenRecord::job),
		CitizenMode.CODEC.fieldOf("mode").forGetter(CitizenRecord::mode),
		Codec.INT.fieldOf("village").forGetter(CitizenRecord::villageId),
		CitizenStatus.CODEC.optionalFieldOf("status").forGetter(r -> Optional.of(r.status)),
		// Saves from before statuses existed only knew "condemned".
		Codec.BOOL.optionalFieldOf("condemned").forGetter(r -> Optional.empty())
	).apply(i, (uuid, name, job, mode, village, status, condemned) -> new CitizenRecord(uuid, name, job, mode, village,
		status.orElse(condemned.orElse(false) ? CitizenStatus.CONDEMNED : CitizenStatus.FREE))));

	public CitizenRecord withJob(CitizenJob newJob) {
		return new CitizenRecord(uuid, name, newJob, mode, villageId, status);
	}

	public CitizenRecord withMode(CitizenMode newMode) {
		return new CitizenRecord(uuid, name, job, newMode, villageId, status);
	}

	public CitizenRecord withStatus(CitizenStatus newStatus) {
		return new CitizenRecord(uuid, name, job, mode, villageId, newStatus);
	}

	public boolean condemned() {
		return status == CitizenStatus.CONDEMNED;
	}
}
