package com.architecturoverse.client;

import com.architecturoverse.Architecturoverse;
import com.architecturoverse.client.render.CitizenRenderer;
import com.architecturoverse.client.render.EnemySoldierRenderer;
import com.architecturoverse.client.render.TraderRenderer;
import com.architecturoverse.client.screen.KingdomScreen;
import com.architecturoverse.network.KingdomSnapshotPayload;
import com.architecturoverse.network.RequestKingdomPayload;
import com.architecturoverse.registry.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ArchitecturoverseClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Architecturoverse.id("main"));
	private static KeyMapping openKingdom;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.CITIZEN, CitizenRenderer::new);
		EntityRendererRegistry.register(ModEntities.TRADER, TraderRenderer::new);
		EntityRendererRegistry.register(ModEntities.ENEMY_SOLDIER, EnemySoldierRenderer::new);

		openKingdom = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.architecturoverse.kingdom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKingdom.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					ClientPlayNetworking.send(RequestKingdomPayload.INSTANCE);
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(KingdomSnapshotPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof KingdomScreen open) {
				open.update(payload);
			} else {
				context.client().gui.setScreen(new KingdomScreen(payload));
			}
		});
	}
}
