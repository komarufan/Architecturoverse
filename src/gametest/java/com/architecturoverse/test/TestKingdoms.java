package com.architecturoverse.test;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.network.KingdomNetworking;
import com.architecturoverse.registry.ModEntities;
import com.architecturoverse.registry.ModItems;
import com.architecturoverse.village.CaptureHandler;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Helpers shared by the game tests. Every test gets its own ruler so tests running side by side never share a kingdom. */
final class TestKingdoms {
	/** Tests run side by side in one world; keep them further apart than a village radius. */
	static final int PADDING = 120;
	static final BlockPos BELL = new BlockPos(4, 1, 4);

	private TestKingdoms() {
	}

	/** A fresh ruler holding a scepter, and a bell at {@link #BELL}. */
	static FakePlayer newRuler(GameTestHelper helper) {
		helper.setBlock(BELL, Blocks.BELL);
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TestRuler"));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCEPTER));
		return player;
	}

	/** Lays a floor of the given block at relative y=0 over an area starting at the structure origin. */
	static void floor(GameTestHelper helper, int size, Block block) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(new BlockPos(x, 0, z), block);
			}
		}
	}

	/** Captures the village with a single villager at {@code villagerPos} and gives the citizen a job. */
	static CitizenEntity citizenWithJob(GameTestHelper helper, FakePlayer ruler, BlockPos villagerPos, CitizenJob job) {
		helper.spawn(EntityTypes.VILLAGER, villagerPos);
		CaptureHandler.tryCapture(ruler, helper.getLevel(), helper.absolutePos(BELL));
		CitizenEntity citizen = helper.getEntities(ModEntities.CITIZEN).stream().findFirst()
			.orElseThrow(() -> helper.assertionException(Component.literal("Villager was not converted")));
		KingdomNetworking.commandCitizen(ruler, citizen.getUUID(), job, null);
		return citizen;
	}
}
