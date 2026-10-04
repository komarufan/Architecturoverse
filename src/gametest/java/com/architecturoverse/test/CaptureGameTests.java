package com.architecturoverse.test;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.network.KingdomNetworking;
import com.architecturoverse.registry.ModEntities;
import com.architecturoverse.registry.ModItems;
import com.architecturoverse.village.CaptureHandler;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class CaptureGameTests {
	private static final int PADDING = TestKingdoms.PADDING;
	private static final BlockPos BELL = TestKingdoms.BELL;

	private static FakePlayer setUp(GameTestHelper helper) {
		return TestKingdoms.newRuler(helper);
	}

	private static Kingdom kingdomOf(GameTestHelper helper, FakePlayer player) {
		return KingdomManager.get(helper.getLevel().getServer()).kingdom(player.getUUID())
			.orElseThrow(() -> helper.assertionException(Component.literal("Kingdom was not created")));
	}

	@GameTest(padding = PADDING)
	public void villagersBecomeCitizens(GameTestHelper helper) {
		FakePlayer player = setUp(helper);
		helper.spawn(EntityTypes.VILLAGER, 2, 1, 2);
		helper.spawn(EntityTypes.VILLAGER, 6, 1, 6);

		CaptureHandler.tryCapture(player, helper.getLevel(), helper.absolutePos(BELL));

		helper.assertTrue(helper.getEntities(EntityTypes.VILLAGER).isEmpty(), Component.literal("Villagers should be converted"));
		List<CitizenEntity> citizens = helper.getEntities(ModEntities.CITIZEN);
		helper.assertTrue(citizens.size() == 2, Component.literal("Expected 2 citizens, got " + citizens.size()));
		helper.assertTrue(citizens.stream().allMatch(c -> player.getUUID().equals(c.getRuler())), Component.literal("Citizens must obey the ruler"));
		Kingdom kingdom = kingdomOf(helper, player);
		helper.assertTrue(kingdom.villages().size() == 1, Component.literal("Expected one village"));
		helper.assertTrue(kingdom.citizens().size() == 2, Component.literal("Expected two citizen records"));
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void golemsBlockCapture(GameTestHelper helper) {
		FakePlayer player = setUp(helper);
		helper.spawn(EntityTypes.VILLAGER, 2, 1, 2);
		IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, 6, 1, 6);

		CaptureHandler.tryCapture(player, helper.getLevel(), helper.absolutePos(BELL));

		helper.assertTrue(helper.getEntities(ModEntities.CITIZEN).isEmpty(), Component.literal("Capture must fail while golems defend"));
		helper.assertTrue(golem.getTarget() == player, Component.literal("Golem should attack the usurper"));
		helper.assertTrue(KingdomManager.get(helper.getLevel().getServer()).kingdom(player.getUUID()).isEmpty(),
			Component.literal("No kingdom should be founded"));
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void bribedGolemsJoinRuler(GameTestHelper helper) {
		FakePlayer player = setUp(helper);
		player.setShiftKeyDown(true);
		player.getInventory().add(new ItemStack(Items.EMERALD, 7));
		helper.spawn(EntityTypes.VILLAGER, 2, 1, 2);
		IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, 6, 1, 6);

		CaptureHandler.tryCapture(player, helper.getLevel(), helper.absolutePos(BELL));

		helper.assertTrue(helper.getEntities(ModEntities.CITIZEN).size() == 1, Component.literal("Village should be captured"));
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 7 - CaptureHandler.EMERALDS_PER_GOLEM,
			Component.literal("Bribe should cost " + CaptureHandler.EMERALDS_PER_GOLEM + " emeralds"));
		helper.assertTrue(CaptureHandler.ownerOf(golem).filter(player.getUUID()::equals).isPresent(),
			Component.literal("Golem should serve the ruler"));
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void bribeNeedsEnoughEmeralds(GameTestHelper helper) {
		FakePlayer player = setUp(helper);
		player.setShiftKeyDown(true);
		player.getInventory().add(new ItemStack(Items.EMERALD, 2));
		helper.spawn(EntityTypes.VILLAGER, 2, 1, 2);
		helper.spawn(EntityTypes.IRON_GOLEM, 6, 1, 6);

		CaptureHandler.tryCapture(player, helper.getLevel(), helper.absolutePos(BELL));

		helper.assertTrue(helper.getEntities(ModEntities.CITIZEN).isEmpty(), Component.literal("Capture must fail without enough emeralds"));
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 2, Component.literal("Emeralds must not be taken"));
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void orderMakesSoldier(GameTestHelper helper) {
		FakePlayer player = setUp(helper);
		helper.spawn(EntityTypes.VILLAGER, 2, 1, 2);
		CaptureHandler.tryCapture(player, helper.getLevel(), helper.absolutePos(BELL));
		CitizenEntity citizen = helper.getEntities(ModEntities.CITIZEN).getFirst();

		boolean accepted = KingdomNetworking.commandCitizen(player, citizen.getUUID(), CitizenJob.SOLDIER, null);

		helper.assertTrue(accepted, Component.literal("Order should be accepted"));
		helper.assertTrue(citizen.getJob() == CitizenJob.SOLDIER, Component.literal("Citizen should become a soldier"));
		helper.assertTrue(citizen.getMainHandItem().is(Items.IRON_SWORD), Component.literal("Soldier should get a sword"));
		helper.assertTrue(kingdomOf(helper, player).citizen(citizen.getUUID()).map(r -> r.job() == CitizenJob.SOLDIER).orElse(false),
			Component.literal("Kingdom record should be updated"));
		helper.succeed();
	}

	@GameTest(padding = PADDING)
	public void cannotCaptureTwice(GameTestHelper helper) {
		FakePlayer player = setUp(helper);
		helper.spawn(EntityTypes.VILLAGER, 2, 1, 2);
		CaptureHandler.tryCapture(player, helper.getLevel(), helper.absolutePos(BELL));
		helper.spawn(EntityTypes.VILLAGER, 3, 1, 3);

		CaptureHandler.tryCapture(player, helper.getLevel(), helper.absolutePos(BELL));

		helper.assertTrue(kingdomOf(helper, player).villages().size() == 1, Component.literal("Village must not be claimed twice"));
		helper.assertTrue(helper.getEntities(EntityTypes.VILLAGER).size() == 1, Component.literal("Second ring must not convert anyone"));
		helper.succeed();
	}
}
