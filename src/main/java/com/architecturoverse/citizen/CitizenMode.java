package com.architecturoverse.citizen;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

/** Direct order from the ruler: do your job, follow me, or hold position. */
public enum CitizenMode {
	WORK,
	FOLLOW,
	STAY;

	public static final Codec<CitizenMode> CODEC = Codec.STRING.xmap(CitizenMode::byName, CitizenMode::name);

	public Component displayName() {
		return Component.translatable("mode.architecturoverse." + name().toLowerCase());
	}

	public CitizenMode next() {
		CitizenMode[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static CitizenMode byName(String name) {
		for (CitizenMode mode : values()) {
			if (mode.name().equals(name)) {
				return mode;
			}
		}
		return WORK;
	}

	public static CitizenMode byId(int id) {
		CitizenMode[] all = values();
		return id >= 0 && id < all.length ? all[id] : WORK;
	}
}
