package com.architecturoverse.kingdom;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

/** Standing order for all soldiers of a village. */
public enum ArmyOrder {
	/** Default: hang around the military base and walk through the village. */
	PATROL,
	/** Stay at the military base. */
	BASE,
	/** Gather at the rally point the ruler picked. */
	RALLY;

	public static final Codec<ArmyOrder> CODEC = Codec.STRING.xmap(ArmyOrder::byName, ArmyOrder::name);

	public Component displayName() {
		return Component.translatable("army.architecturoverse." + name().toLowerCase());
	}

	public static ArmyOrder byName(String name) {
		for (ArmyOrder order : values()) {
			if (order.name().equals(name)) {
				return order;
			}
		}
		return PATROL;
	}

	public static ArmyOrder byId(int id) {
		ArmyOrder[] all = values();
		return id >= 0 && id < all.length ? all[id] : PATROL;
	}
}
