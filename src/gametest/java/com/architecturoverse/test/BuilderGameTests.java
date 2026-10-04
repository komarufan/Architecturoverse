package com.architecturoverse.test;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.kingdom.BuildOrder;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.registry.ModBlocks;
import com.architecturoverse.village.SiteFinder;
import com.architecturoverse.village.VillageOrders;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

public class BuilderGameTests {
	private static final BlockPos CITIZEN = new BlockPos(2, 1, 2);

	private static ClaimedVillage village(GameTestHelper helper, FakePlayer ruler, CitizenEntity citizen) {
		return KingdomManager.get(helper.getLevel().getServer()).kingdom(ruler.getUUID())
			.flatMap(k -> k.village(citizen.getVillageId()))
			.orElseThrow(() -> helper.assertionException(Component.literal("Village missing")));
	}

	@GameTest(padding = TestKingdoms.PADDING, maxTicks = 800, skyAccess = true)
	public void orderedWarehouseIsBuilt(GameTestHelper helper) {
		TestKingdoms.floor(helper, 16, Blocks.GRASS_BLOCK);
		FakePlayer ruler = TestKingdoms.newRuler(helper);
		CitizenEntity citizen = TestKingdoms.citizenWithJob(helper, ruler, CITIZEN, CitizenJob.UNEMPLOYED);

		boolean accepted = VillageOrders.order(ruler, citizen.getVillageId(), BuildOrder.WAREHOUSE);

		helper.assertTrue(accepted, Component.literal("Order should be accepted"));
		helper.assertTrue(citizen.getJob() == CitizenJob.BUILDER, Component.literal("The unemployed citizen should become a builder"));
		helper.assertTrue(!VillageOrders.order(ruler, citizen.getVillageId(), BuildOrder.WAREHOUSE),
			Component.literal("The same order must not be queued twice"));
		helper.succeedWhen(() -> {
			ClaimedVillage village = village(helper, ruler, citizen);
			helper.assertTrue(village.warehouse().isPresent(), Component.literal("Warehouse should be registered"));
			helper.assertTrue(helper.getLevel().getBlockState(village.warehouse().get()).is(ModBlocks.WAREHOUSE),
				Component.literal("Warehouse block should be placed"));
			helper.assertTrue(village.orders().isEmpty(), Component.literal("Order should be done"));
		});
	}

	// Builders walking to the mine site leave the test area, where entities stop ticking, so only the site choice is tested here.
	@GameTest(padding = TestKingdoms.PADDING, skyAccess = true)
	public void mineSiteIsAtVillageEdgeFacingTheVillage(GameTestHelper helper) {
		TestKingdoms.floor(helper, 48, Blocks.GRASS_BLOCK);
		BlockPos center = helper.absolutePos(TestKingdoms.BELL);

		SiteFinder.Site site = SiteFinder.mine(helper.getLevel(), center)
			.orElseThrow(() -> helper.assertionException(Component.literal("No mine site found")));

		helper.assertTrue(site.pos().distSqr(center) >= 20 * 20, Component.literal("Mine should be at the village edge"));
		BlockPos ahead = site.pos().relative(site.facing(), 4);
		helper.assertTrue(ahead.distSqr(center) < site.pos().distSqr(center), Component.literal("Doorway should face the village"));
		helper.succeed();
	}
}
