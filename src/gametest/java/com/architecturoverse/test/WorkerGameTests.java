package com.architecturoverse.test;

import com.architecturoverse.block.MineEntranceBlock;
import com.architecturoverse.block.WarehouseBlock;
import com.architecturoverse.block.WarehouseBlockEntity;
import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.work.MinePlan;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MineSite;
import com.architecturoverse.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public class WorkerGameTests {
	private static final BlockPos WAREHOUSE = new BlockPos(1, 1, 6);
	private static final BlockPos CITIZEN = new BlockPos(2, 1, 2);

	private static void placeWarehouse(GameTestHelper helper, FakePlayer ruler) {
		helper.setBlock(WAREHOUSE, ModBlocks.WAREHOUSE);
		boolean registered = WarehouseBlock.register(helper.getLevel(), helper.absolutePos(WAREHOUSE), ruler);
		helper.assertTrue(registered, Component.literal("Warehouse should register with the village"));
	}

	private static int countInWarehouse(GameTestHelper helper, Item item) {
		WarehouseBlockEntity warehouse = helper.getBlockEntity(WAREHOUSE, WarehouseBlockEntity.class);
		int count = 0;
		for (int i = 0; i < warehouse.getContainerSize(); i++) {
			ItemStack stack = warehouse.getItem(i);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	@GameTest(padding = TestKingdoms.PADDING, maxTicks = 1200, skyAccess = true)
	public void lumberjackFellsTreeIntoWarehouse(GameTestHelper helper) {
		TestKingdoms.floor(helper, 8, Blocks.GRASS_BLOCK);
		FakePlayer ruler = TestKingdoms.newRuler(helper);
		BlockPos trunk = new BlockPos(6, 1, 6);
		for (int y = 0; y < 4; y++) {
			helper.setBlock(trunk.above(y), Blocks.OAK_LOG);
		}
		for (BlockPos leaves : BlockPos.betweenClosed(trunk.offset(-1, 3, -1), trunk.offset(1, 4, 1))) {
			if (helper.getBlockState(leaves).isAir()) {
				helper.setBlock(leaves.immutable(), Blocks.OAK_LEAVES);
			}
		}
		TestKingdoms.citizenWithJob(helper, ruler, CITIZEN, CitizenJob.LUMBERJACK);
		placeWarehouse(helper, ruler);

		helper.succeedWhen(() -> {
			helper.assertTrue(countInWarehouse(helper, Items.OAK_LOG) == 4, Component.literal("All 4 logs should end up in the warehouse"));
			helper.assertTrue(helper.getBlockState(trunk).is(Blocks.OAK_SAPLING), Component.literal("A sapling should be replanted"));
		});
	}

	@GameTest(padding = TestKingdoms.PADDING, maxTicks = 1200, skyAccess = true)
	public void farmerHarvestsAndReplants(GameTestHelper helper) {
		TestKingdoms.floor(helper, 8, Blocks.GRASS_BLOCK);
		FakePlayer ruler = TestKingdoms.newRuler(helper);
		List<BlockPos> crops = List.of(new BlockPos(6, 1, 1), new BlockPos(6, 1, 2), new BlockPos(7, 1, 1));
		for (BlockPos crop : crops) {
			helper.setBlock(crop.below(), Blocks.FARMLAND);
			helper.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
		}
		TestKingdoms.citizenWithJob(helper, ruler, CITIZEN, CitizenJob.FARMER);
		placeWarehouse(helper, ruler);

		helper.succeedWhen(() -> {
			helper.assertTrue(countInWarehouse(helper, Items.WHEAT) >= crops.size(), Component.literal("Wheat should be delivered"));
			for (BlockPos crop : crops) {
				BlockState state = helper.getBlockState(crop);
				helper.assertTrue(state.is(Blocks.WHEAT) && !((CropBlock) Blocks.WHEAT).isMaxAge(state),
					Component.literal("Wheat should be replanted at " + crop));
			}
		});
	}

	@GameTest(padding = TestKingdoms.PADDING, maxTicks = 1200)
	public void minerDigsAlongThePlan(GameTestHelper helper) {
		TestKingdoms.floor(helper, 8, Blocks.GRASS_BLOCK);
		FakePlayer ruler = TestKingdoms.newRuler(helper);
		BlockPos entrance = new BlockPos(6, 1, 1);
		helper.setBlock(entrance, ModBlocks.MINE_ENTRANCE);
		MineSite site = new MineSite(helper.absolutePos(entrance), Direction.SOUTH, 0);
		// Fill the first steps of the plan with stone so there is something to dig.
		List<MinePlan.Step> plan = MinePlan.of(site, helper.getLevel().getMinY());
		List<BlockPos> firstSteps = plan.subList(0, 6).stream().map(MinePlan.Step::pos).toList();
		firstSteps.forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));

		CitizenEntity miner = TestKingdoms.citizenWithJob(helper, ruler, CITIZEN, CitizenJob.MINER);
		placeWarehouse(helper, ruler);
		boolean registered = MineEntranceBlock.register(helper.getLevel(), helper.absolutePos(entrance), Direction.SOUTH, ruler);
		helper.assertTrue(registered, Component.literal("Mine should register with the village"));

		helper.succeedWhen(() -> {
			for (BlockPos pos : firstSteps) {
				helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), Component.literal("Plan block not dug: " + pos));
			}
			int progress = KingdomManager.get(helper.getLevel().getServer()).kingdom(ruler.getUUID())
				.flatMap(k -> k.village(miner.getVillageId())).flatMap(v -> v.mine()).map(MineSite::progress).orElse(0);
			helper.assertTrue(progress >= firstSteps.size(), Component.literal("Mine progress should advance, is " + progress));
			int cobble = countInWarehouse(helper, Items.COBBLESTONE) + miner.getInventory().countItem(Items.COBBLESTONE);
			helper.assertTrue(cobble >= firstSteps.size(), Component.literal("Dug stone should be kept as cobblestone"));
		});
	}

	@GameTest(padding = TestKingdoms.PADDING)
	public void minePlanIsAStaircaseThenATunnel(GameTestHelper helper) {
		BlockPos entrance = new BlockPos(0, 64, 0);
		List<MinePlan.Step> plan = MinePlan.of(new MineSite(entrance, Direction.EAST, 0), -64);
		BlockPos firstFloor = plan.get(2).pos();
		helper.assertTrue(firstFloor.equals(new BlockPos(1, 63, 0)), Component.literal("First step should be one block east and one down, was " + firstFloor));
		helper.assertTrue(plan.get(0).pos().equals(new BlockPos(1, 65, 0)), Component.literal("Stairs need 3 blocks of head room"));
		int lowest = plan.stream().mapToInt(s -> s.pos().getY()).min().orElseThrow();
		helper.assertTrue(lowest == 64 - 1 - MinePlan.MAX_DEPTH, Component.literal("Staircase should stop at max depth, lowest y " + lowest));
		helper.assertTrue(plan.stream().anyMatch(MinePlan.Step::torch), Component.literal("The mine should be lit"));
		helper.succeed();
	}
}
