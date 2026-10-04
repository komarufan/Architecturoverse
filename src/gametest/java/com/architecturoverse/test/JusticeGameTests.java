package com.architecturoverse.test;

import com.architecturoverse.block.WarehouseBlock;
import com.architecturoverse.block.WarehouseBlockEntity;
import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Construction;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MilitaryBase;
import com.architecturoverse.kingdom.Placement;
import com.architecturoverse.military.EnemySoldierEntity;
import com.architecturoverse.network.KingdomNetworking;
import com.architecturoverse.registry.ModBlocks;
import com.architecturoverse.registry.ModEntities;
import com.architecturoverse.structure.ConstructionPlan;
import com.architecturoverse.structure.StructureType;
import com.architecturoverse.village.CaptureHandler;
import com.architecturoverse.village.Economy;
import com.architecturoverse.village.Executions;
import com.architecturoverse.village.Retaliation;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

public class JusticeGameTests {
	private static final String ARENA = "architecturoverse-gametest:arena";
	private static final BlockPos BELL = new BlockPos(20, 1, 20);

	private record Village(FakePlayer ruler, List<CitizenEntity> citizens) {
		int id() {
			return citizens.getFirst().getVillageId();
		}
	}

	private static Village village(GameTestHelper helper, int count) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		helper.setBlock(BELL, Blocks.BELL);
		FakePlayer ruler = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Judge"));
		for (int i = 0; i < count; i++) {
			helper.spawn(EntityTypes.VILLAGER, new BlockPos(16 + i, 1, 17));
		}
		CaptureHandler.tryCapture(ruler, helper.getLevel(), helper.absolutePos(BELL));
		return new Village(ruler, helper.getEntities(ModEntities.CITIZEN));
	}

	private static KingdomManager manager(GameTestHelper helper) {
		return KingdomManager.get(helper.getLevel().getServer());
	}

	private static Kingdom kingdom(GameTestHelper helper, Village village) {
		return manager(helper).kingdom(village.ruler().getUUID()).orElseThrow();
	}

	private static void updateVillage(GameTestHelper helper, Village village, UnaryOperator<ClaimedVillage> change) {
		manager(helper).updateVillage(kingdom(helper, village), village.id(), change);
	}

	/** Puts every block of a building in place at once and returns where it stands. */
	private static Placement instantBuild(GameTestHelper helper, StructureType type, BlockPos relativeOrigin) {
		Construction site = new Construction(type, helper.absolutePos(relativeOrigin), Rotation.NONE, 0);
		for (ConstructionPlan.Step step : ConstructionPlan.of(site)) {
			if (!step.foundation()) {
				helper.getLevel().setBlockAndUpdate(step.pos(), step.state());
				if (step.extraPos() != null && step.extraState() != null) {
					helper.getLevel().setBlockAndUpdate(step.extraPos(), step.extraState());
				}
			}
		}
		return new Placement(type, site.origin(), site.rotation());
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING, maxTicks = 2400)
	public void soldierLeadsPrisonerToPrison(GameTestHelper helper) {
		Village village = village(helper, 2);
		CitizenEntity soldier = village.citizens().get(0);
		CitizenEntity prisoner = village.citizens().get(1);
		KingdomNetworking.commandCitizen(village.ruler(), soldier.getUUID(), CitizenJob.SOLDIER, null);
		Placement prison = instantBuild(helper, StructureType.PRISON, new BlockPos(2, 1, 2));
		updateVillage(helper, village, v -> v.withPrison(Optional.of(prison)));

		helper.assertTrue(Executions.sentence(village.ruler(), prisoner.getUUID(), Executions.Sentence.PRISON),
			Component.literal("Arrest should be accepted"));
		helper.assertTrue(prisoner.getStatus() == CitizenStatus.ARRESTED, Component.literal("Citizen should be arrested"));

		helper.succeedWhen(() -> {
			helper.assertTrue(prisoner.getStatus() == CitizenStatus.IMPRISONED, Component.literal("Prisoner is not locked up yet"));
			helper.assertTrue(prisoner.blockPosition().equals(prison.prisonCell(prisoner.getPrisonCell())),
				Component.literal("Prisoner should sit in their cell"));
			helper.assertTrue(kingdom(helper, village).citizen(prisoner.getUUID()).map(r -> r.status() == CitizenStatus.IMPRISONED).orElse(false),
				Component.literal("The register should know"));
		});
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING, maxTicks = 3600)
	public void messengerBringsTheSentence(GameTestHelper helper) {
		Village village = village(helper, 3);
		CitizenEntity soldier = village.citizens().get(0);
		CitizenEntity messenger = village.citizens().get(1);
		CitizenEntity condemned = village.citizens().get(2);
		KingdomNetworking.commandCitizen(village.ruler(), soldier.getUUID(), CitizenJob.SOLDIER, null);
		KingdomNetworking.commandCitizen(village.ruler(), messenger.getUUID(), CitizenJob.MESSENGER, null);
		Placement base = instantBuild(helper, StructureType.MILITARY_BASE, new BlockPos(2, 1, 2));
		Placement office = instantBuild(helper, StructureType.POST_OFFICE, new BlockPos(15, 1, 2));
		updateVillage(helper, village, v -> v.withMilitaryBase(Optional.of(new MilitaryBase(base.origin(), base.rotation())))
			.withPostOffice(Optional.of(office)));

		helper.assertTrue(Executions.sentence(village.ruler(), condemned.getUUID(), Executions.Sentence.DEATH),
			Component.literal("Sentence should be accepted"));
		helper.assertTrue(!condemned.hasNotice(), Component.literal("The condemned waits for the messenger"));

		helper.succeedWhen(() -> {
			helper.assertTrue(!condemned.isAlive(), Component.literal("The sentence has not been carried out yet"));
			helper.assertTrue(!messenger.getMainHandItem().is(Items.PAPER), Component.literal("The messenger handed the paper over"));
		});
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING, maxTicks = 200)
	public void villagersBornInTheVillageBecomeCitizens(GameTestHelper helper) {
		Village village = village(helper, 1);
		helper.spawn(EntityTypes.VILLAGER, new BlockPos(10, 1, 10));

		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getEntities(EntityTypes.VILLAGER).isEmpty(), Component.literal("The newcomer is still a plain villager"));
			helper.assertTrue(kingdom(helper, village).citizens().size() == 2, Component.literal("The newcomer should be in the register"));
		});
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING, maxTicks = 1200)
	public void undefendedVillageIsPlundered(GameTestHelper helper) {
		Village village = village(helper, 1);
		List<EnemySoldierEntity> squad = Retaliation.spawnSquad(helper.getLevel(), village.ruler().getUUID(),
			kingdom(helper, village).village(village.id()).orElseThrow(), helper.absolutePos(new BlockPos(3, 1, 3)), 3);

		helper.succeedWhen(() -> helper.assertTrue(squad.stream().anyMatch(e -> e.getPhase() != EnemySoldierEntity.Phase.FIGHT),
			Component.literal("Without defenders the enemies should start plundering")));
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING)
	public void surrenderedEnemyBecomesAPrisoner(GameTestHelper helper) {
		Village village = village(helper, 1);
		EnemySoldierEntity enemy = Retaliation.spawnSquad(helper.getLevel(), village.ruler().getUUID(),
			kingdom(helper, village).village(village.id()).orElseThrow(), helper.absolutePos(new BlockPos(3, 1, 3)), 1).getFirst();

		Retaliation.surrender(helper.getLevel(), enemy);

		helper.assertTrue(!enemy.isAlive(), Component.literal("The enemy soldier should be gone"));
		helper.assertTrue(kingdom(helper, village).citizens().stream().anyMatch(c -> c.status() == CitizenStatus.ARRESTED),
			Component.literal("A captive should be in the register"));
		helper.succeed();
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING)
	public void wagesComeFromTheWarehouse(GameTestHelper helper) {
		Village village = village(helper, 2);
		KingdomNetworking.commandCitizen(village.ruler(), village.citizens().get(0).getUUID(), CitizenJob.SOLDIER, null);
		KingdomNetworking.commandCitizen(village.ruler(), village.citizens().get(1).getUUID(), CitizenJob.FARMER, null);
		BlockPos warehousePos = new BlockPos(21, 1, 15);
		helper.setBlock(warehousePos, ModBlocks.WAREHOUSE);
		WarehouseBlock.register(helper.getLevel(), helper.absolutePos(warehousePos), village.ruler());
		WarehouseBlockEntity warehouse = helper.getBlockEntity(warehousePos, WarehouseBlockEntity.class);
		warehouse.insert(new ItemStack(Items.EMERALD, 10));
		int moodBefore = kingdom(helper, village).village(village.id()).orElseThrow().mood().mood();

		Economy.payday(helper.getLevel(), kingdom(helper, village), kingdom(helper, village).village(village.id()).orElseThrow());

		helper.assertTrue(warehouse.count(s -> s.is(Items.EMERALD)) == 10 - 3, Component.literal("Soldier 2 + farmer 1 should be paid"));
		helper.assertTrue(kingdom(helper, village).village(village.id()).orElseThrow().mood().mood() > moodBefore - 10,
			Component.literal("Paying everybody should not make them unhappy"));
		helper.succeed();
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING)
	public void unpaidMiserableVillageRebels(GameTestHelper helper) {
		Village village = village(helper, 3);
		for (CitizenEntity citizen : village.citizens()) {
			KingdomNetworking.commandCitizen(village.ruler(), citizen.getUUID(), CitizenJob.FARMER, null);
		}
		updateVillage(helper, village, v -> v.withMood(v.mood().withMood(20)));

		Economy.payday(helper.getLevel(), kingdom(helper, village), kingdom(helper, village).village(village.id()).orElseThrow());

		ClaimedVillage after = kingdom(helper, village).village(village.id()).orElseThrow();
		helper.assertTrue(after.mood().rebellion(), Component.literal("The village should rise up"));
		helper.assertTrue(village.citizens().stream().anyMatch(c -> c.getStatus() == CitizenStatus.REBEL), Component.literal("Somebody should rebel"));
		helper.succeed();
	}
}
