package com.architecturoverse;

import com.architecturoverse.network.KingdomNetworking;
import com.architecturoverse.registry.ModBlocks;
import com.architecturoverse.registry.ModEntities;
import com.architecturoverse.registry.ModItems;
import com.architecturoverse.village.CaptureHandler;
import com.architecturoverse.village.Retaliation;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Architecturoverse implements ModInitializer {
	public static final String MOD_ID = "architecturoverse";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModItems.init();
		ModEntities.init();
		KingdomNetworking.init();
		CaptureHandler.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				Retaliation.tick(server);
			}
		});
		LOGGER.info("Architecturoverse loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
