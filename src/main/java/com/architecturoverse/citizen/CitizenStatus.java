package com.architecturoverse.citizen;

import com.mojang.serialization.Codec;

/** A citizen's standing with the law. */
public enum CitizenStatus {
	FREE,
	/** Sentenced to death: gets the notice, walks into the cell of the military base and is executed. */
	CONDEMNED,
	/** Sent to prison: waits for a soldier who leads them to a prison cell. */
	ARRESTED,
	/** Sitting in a prison cell. */
	IMPRISONED,
	/** Rose up against the ruler; fights until killed or until surrendering. */
	REBEL;

	public static final Codec<CitizenStatus> CODEC = Codec.STRING.xmap(CitizenStatus::byName, CitizenStatus::name);

	public static CitizenStatus byName(String name) {
		for (CitizenStatus status : values()) {
			if (status.name().equals(name)) {
				return status;
			}
		}
		return FREE;
	}

	public static CitizenStatus byId(int id) {
		CitizenStatus[] all = values();
		return id >= 0 && id < all.length ? all[id] : FREE;
	}

	/** Prisoners and the condemned neither work nor take orders. */
	public boolean isDetained() {
		return this == CONDEMNED || this == ARRESTED || this == IMPRISONED;
	}
}
