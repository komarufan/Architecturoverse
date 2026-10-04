package com.architecturoverse.test;

import com.architecturoverse.block.WarehouseBlock;
import com.architecturoverse.block.WarehouseBlockEntity;
import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.work.Walker;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.military.EnemySoldierEntity;
import com.architecturoverse.registry.ModBlocks;
import com.architecturoverse.registry.ModEntities;
import com.architecturoverse.trade.TraderEntity;
import com.architecturoverse.trade.Traders;
import com.architecturoverse.village.CaptureHandler;
import com.architecturoverse.village.Retaliation;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class TradeGameTests {
	private static final String ARENA = "architecturoverse-gametest:arena";
	private static final BlockPos BELL = new BlockPos(20, 1, 20);
	private static final BlockPos WAREHOUSE = new BlockPos(20, 1, 15);

	private static FakePlayer village(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		helper.setBlock(BELL, Blocks.BELL);
		FakePlayer ruler = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Merchant Prince"));
		helper.spawn(EntityTypes.VILLAGER, new BlockPos(18, 1, 18));
		CaptureHandler.tryCapture(ruler, helper.getLevel(), helper.absolutePos(BELL));
		helper.setBlock(WAREHOUSE, ModBlocks.WAREHOUSE);
		WarehouseBlock.register(helper.getLevel(), helper.absolutePos(WAREHOUSE), ruler);
		return ruler;
	}

	private static ClaimedVillage claimed(GameTestHelper helper, FakePlayer ruler) {
		return KingdomManager.get(helper.getLevel().getServer()).kingdom(ruler.getUUID())
			.flatMap(k -> k.villages().stream().findFirst()).orElseThrow();
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING, maxTicks = 1200)
	public void traderBuysTheWarehouse(GameTestHelper helper) {
		FakePlayer ruler = village(helper);
		WarehouseBlockEntity warehouse = helper.getBlockEntity(WAREHOUSE, WarehouseBlockEntity.class);
		warehouse.insert(new ItemStack(Items.COBBLESTONE, 64));
		warehouse.insert(new ItemStack(Items.DIAMOND, 8));
		warehouse.insert(new ItemStack(Items.EMERALD, 10));

		Traders.spawn(helper.getLevel(), ruler.getUUID(), claimed(helper, ruler), helper.absolutePos(new BlockPos(4, 1, 4)));

		helper.succeedWhen(() -> {
			// 64 cobblestone = 1 emerald, 8 diamonds = 32 emeralds, plus the 10 that were there.
			helper.assertTrue(warehouse.count(s -> s.is(Items.EMERALD)) == 43, Component.literal("Expected 43 emeralds, got "
				+ warehouse.count(s -> s.is(Items.EMERALD))));
			helper.assertTrue(warehouse.count(s -> !s.is(Items.EMERALD)) == 0, Component.literal("All goods should be sold"));
		});
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING, maxTicks = 1200)
	public void robbingTheTraderBringsRevenge(GameTestHelper helper) {
		FakePlayer ruler = village(helper);
		TraderEntity trader = Traders.spawn(helper.getLevel(), ruler.getUUID(), claimed(helper, ruler), helper.absolutePos(new BlockPos(4, 1, 4)));

		trader.hurtServer(helper.getLevel(), ruler.damageSources().playerAttack(ruler), 1000.0F);

		helper.assertTrue(!trader.isAlive(), Component.literal("Trader should be dead"));
		helper.assertTrue(claimed(helper, ruler).raidAt() > helper.getLevel().getGameTime(), Component.literal("Revenge should be scheduled"));
		helper.assertTrue(!helper.getEntities(EntityTypes.ITEM).isEmpty(), Component.literal("The purse should drop"));

		List<EnemySoldierEntity> squad = Retaliation.spawnSquad(helper.getLevel(), ruler.getUUID(), claimed(helper, ruler),
			helper.absolutePos(new BlockPos(4, 1, 18)), 3);
		helper.assertTrue(squad.size() == 3, Component.literal("Squad should spawn"));
		CitizenEntity citizen = helper.getEntities(ModEntities.CITIZEN).getFirst();
		helper.succeedWhen(() -> helper.assertTrue(squad.stream().anyMatch(e -> e.getTarget() == citizen),
			Component.literal("Enemies should go for the ruler's citizens")));
	}

	@GameTest(padding = TestKingdoms.PADDING, skyAccess = true)
	public void patrolsAvoidWater(GameTestHelper helper) {
		TestKingdoms.floor(helper, 8, Blocks.STONE);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.WATER);
		BlockPos water = helper.absolutePos(new BlockPos(3, 1, 3));
		BlockPos dry = helper.absolutePos(new BlockPos(5, 1, 5));
		helper.assertTrue(Walker.dryGround(helper.getLevel(), water.getX(), water.getZ()).isEmpty(), Component.literal("Water is no place to stand"));
		helper.assertTrue(Walker.dryGround(helper.getLevel(), dry.getX(), dry.getZ()).isPresent(), Component.literal("Stone is"));
		helper.succeed();
	}

	@GameTest(padding = TestKingdoms.PADDING, maxTicks = 600)
	public void citizensClimbOutOfWater(GameTestHelper helper) {
		TestKingdoms.floor(helper, 8, Blocks.STONE);
		FakePlayer ruler = TestKingdoms.newRuler(helper);
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.WATER);
				helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
			}
		}
		CitizenEntity citizen = TestKingdoms.citizenWithJob(helper, ruler, new BlockPos(2, 1, 2), com.architecturoverse.citizen.CitizenJob.UNEMPLOYED);
		BlockPos pool = helper.absolutePos(new BlockPos(2, 0, 2));
		citizen.snapTo(pool.getX() + 0.5, pool.getY(), pool.getZ() + 0.5, 0.0F, 0.0F);

		helper.succeedWhen(() -> {
			helper.assertTrue(citizen.tickCount > 260 && !citizen.isInWater(), Component.literal("The citizen should have climbed out"));
		});
	}
}
