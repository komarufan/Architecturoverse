package com.architecturoverse.test;

import com.architecturoverse.block.WarehouseBlock;
import com.architecturoverse.block.WarehouseBlockEntity;
import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.work.MinePlan;
import com.architecturoverse.citizen.work.MineRoute;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Construction;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MineSite;
import com.architecturoverse.registry.ModBlocks;
import com.architecturoverse.registry.ModEntities;
import com.architecturoverse.structure.Blueprint;
import com.architecturoverse.structure.StructureType;
import com.architecturoverse.village.CaptureHandler;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

public class StructureGameTests {
	private static final String ARENA = "architecturoverse-gametest:arena";

	@GameTest(padding = TestKingdoms.PADDING)
	public void militaryBaseBlueprintIsComplete(GameTestHelper helper) {
		Blueprint blueprint = StructureType.MILITARY_BASE.blueprint();
		helper.assertTrue(blueprint.width() == 11 && blueprint.depth() == 9, Component.literal("Base should be 11x9"));
		BlockPos origin = BlockPos.ZERO;
		for (char anchor : new char[] {StructureType.DOOR, StructureType.RALLY, StructureType.WORKBENCH, StructureType.GATE, StructureType.CELL}) {
			blueprint.anchor(anchor, origin, Rotation.NONE);
		}
		long beds = blueprint.entries().stream().filter(e -> e.state().getBlock() instanceof BedBlock).count();
		long pairedBeds = blueprint.entries().stream().filter(e -> e.state().getBlock() instanceof BedBlock && e.extraPos() != null).count();
		helper.assertTrue(beds == 3 && pairedBeds == 3, Component.literal("Expected 3 beds placed as head+foot, got " + beds + "/" + pairedBeds));
		BlockPos door = blueprint.anchor(StructureType.DOOR, origin, Rotation.NONE);
		helper.assertTrue(door.getZ() == 0, Component.literal("The door should be in the front row"));
		BlockPos turned = blueprint.anchor(StructureType.DOOR, origin, Rotation.CLOCKWISE_90);
		helper.assertTrue(turned.getX() == 0 && turned.getZ() == door.getX(), Component.literal("Turning the base should turn the door, got " + turned));
		helper.succeed();
	}

	@GameTest(padding = TestKingdoms.PADDING)
	public void deepMineIsWalkedInHops(GameTestHelper helper) {
		MineSite site = new MineSite(new BlockPos(0, 64, 0), Direction.EAST, 0);
		List<BlockPos> route = MinePlan.route(site, -64);
		BlockPos deep = route.get(60);
		Optional<BlockPos> up = MineRoute.waypoint(site, -64, Vec3.atBottomCenterOf(deep), new BlockPos(-20, 64, 0));
		helper.assertTrue(up.isPresent() && up.get().getY() > deep.getY() && route.indexOf(up.get()) < 60,
			Component.literal("Leaving a deep mine should climb the stairs, got " + up));
		Optional<BlockPos> down = MineRoute.waypoint(site, -64, new Vec3(-2, 64, 0), deep);
		helper.assertTrue(down.isPresent() && route.indexOf(down.get()) > 0 && route.indexOf(down.get()) < 60,
			Component.literal("Entering a deep mine should go down the stairs first, got " + down));
		Optional<BlockPos> near = MineRoute.waypoint(site, -64, Vec3.atBottomCenterOf(route.get(10)), route.get(12));
		helper.assertTrue(near.isEmpty(), Component.literal("Short walks need no hops"));
		helper.succeed();
	}

	/** Three idle citizens build the whole military base from a stocked warehouse. */
	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING, maxTicks = 8000)
	public void villagersBuildMilitaryBase(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				for (int y = -2; y <= 0; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		helper.setBlock(new BlockPos(20, 1, 20), Blocks.BELL);
		FakePlayer ruler = FakePlayer.get(helper.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Builder"));
		helper.spawn(EntityTypes.VILLAGER, new BlockPos(18, 1, 18));
		helper.spawn(EntityTypes.VILLAGER, new BlockPos(19, 1, 21));
		helper.spawn(EntityTypes.VILLAGER, new BlockPos(21, 1, 18));
		CaptureHandler.tryCapture(ruler, helper.getLevel(), helper.absolutePos(new BlockPos(20, 1, 20)));
		CitizenEntity anyone = helper.getEntities(ModEntities.CITIZEN).getFirst();

		BlockPos warehousePos = new BlockPos(21, 1, 15);
		helper.setBlock(warehousePos, ModBlocks.WAREHOUSE);
		WarehouseBlock.register(helper.getLevel(), helper.absolutePos(warehousePos), ruler);
		WarehouseBlockEntity warehouse = helper.getBlockEntity(warehousePos, WarehouseBlockEntity.class);
		for (int i = 0; i < 4; i++) {
			warehouse.insert(new ItemStack(Items.COBBLESTONE, 64));
		}
		for (int i = 0; i < 3; i++) {
			warehouse.insert(new ItemStack(Items.OAK_LOG, 64));
		}

		Construction site = new Construction(StructureType.MILITARY_BASE, helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, 0);
		KingdomManager manager = KingdomManager.get(helper.getLevel().getServer());
		manager.kingdom(ruler.getUUID()).ifPresent(k -> manager.updateVillage(k, anyone.getVillageId(), v -> v.withConstruction(Optional.of(site))));

		helper.succeedWhen(() -> {
			ClaimedVillage village = manager.kingdom(ruler.getUUID()).flatMap(k -> k.village(anyone.getVillageId())).orElseThrow();
			helper.assertTrue(village.militaryBase().isPresent(), Component.literal("Military base not finished yet"));
			helper.assertTrue(helper.getLevel().getBlockState(village.militaryBase().get().workbench()).is(Blocks.CRAFTING_TABLE),
				Component.literal("Workbench should stand at its anchor"));
			helper.assertTrue(helper.getLevel().getBlockState(village.militaryBase().get().gate()).is(Blocks.OAK_FENCE_GATE),
				Component.literal("The cell gate should be built"));
		});
	}
}
