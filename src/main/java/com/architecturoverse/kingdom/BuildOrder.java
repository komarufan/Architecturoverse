package com.architecturoverse.kingdom;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;

/** Something the ruler ordered the village builders to construct. */
public enum BuildOrder {
	WAREHOUSE,
	MINE;

	public static final Codec<BuildOrder> CODEC = Codec.STRING.xmap(BuildOrder::byName, BuildOrder::name);

	public Component displayName() {
		return Component.translatable("order.architecturoverse." + name().toLowerCase());
	}

	public static BuildOrder byName(String name) {
		for (BuildOrder order : values()) {
			if (order.name().equals(name)) {
				return order;
			}
		}
		return WAREHOUSE;
	}

	public static BuildOrder byId(int id) {
		BuildOrder[] all = values();
		return id >= 0 && id < all.length ? all[id] : WAREHOUSE;
	}
}
