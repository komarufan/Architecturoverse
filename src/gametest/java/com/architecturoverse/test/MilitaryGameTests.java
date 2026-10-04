package com.architecturoverse.test;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.kingdom.ArmyOrder;
import com.architecturoverse.kingdom.Construction;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MilitaryBase;
import com.architecturoverse.network.KingdomNetworking;
import com.architecturoverse.registry.ModEntities;
import com.architecturoverse.structure.ConstructionPlan;
import com.architecturoverse.structure.StructureType;
import com.architecturoverse.village.Army;
import com.architecturoverse.village.CaptureHandler;
import com.architecturoverse.village.Executions;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

public class MilitaryGameTests {
	private static final String ARENA = "architecturoverse-gametest:arena";
	private static final BlockPos BELL = new BlockPos(20, 1, 20);

	private record Village(FakePlayer ruler, List<CitizenEntity> citizens) {
	}

	/** A stone arena floor and a captured village with {@code count} citizens near the bell. */
	private static Village village(GameTestHelper helper, int count) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		helper.setBlock(BELL, Blocks.BELL);
		FakePlayer ruler = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "General"));
		for (int i = 0; i < count; i++) {
			helper.spawn(EntityTypes.VILLAGER, new BlockPos(17 + i, 1, 17));
		}
		CaptureHandler.tryCapture(ruler, helper.getLevel(), helper.absolutePos(BELL));
		return new Village(ruler, helper.getEntities(ModEntities.CITIZEN));
	}

	/** Puts every block of the military base in place at once and registers it. */
	private static MilitaryBase instantBase(GameTestHelper helper, FakePlayer ruler, CitizenEntity anyone) {
		Construction site = new Construction(StructureType.MILITARY_BASE, helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, 0);
		for (ConstructionPlan.Step step : ConstructionPlan.of(site)) {
			if (!step.foundation()) {
				helper.getLevel().setBlockAndUpdate(step.pos(), step.state());
				if (step.extraPos() != null && step.extraState() != null) {
					helper.getLevel().setBlockAndUpdate(step.extraPos(), step.extraState());
				}
			}
		}
		MilitaryBase base = new MilitaryBase(site.origin(), site.rotation());
		KingdomManager manager = KingdomManager.get(helper.getLevel().getServer());
		manager.kingdom(ruler.getUUID()).ifPresent(k -> manager.updateVillage(k, anyone.getVillageId(), v -> v.withMilitaryBase(Optional.of(base))));
		return base;
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING, maxTicks = 2400)
	public void condemnedCitizenIsExecutedAtTheBase(GameTestHelper helper) {
		Village village = village(helper, 2);
		CitizenEntity soldier = village.citizens().get(0);
		CitizenEntity prisoner = village.citizens().get(1);
		KingdomNetworking.commandCitizen(village.ruler(), soldier.getUUID(), CitizenJob.SOLDIER, null);
		MilitaryBase base = instantBase(helper, village.ruler(), soldier);

		helper.assertTrue(Executions.sentence(village.ruler(), prisoner.getUUID(), Executions.Sentence.DEATH), Component.literal("Sentence should be accepted"));
		helper.assertTrue(prisoner.isCondemned(), Component.literal("Citizen should be condemned"));

		helper.succeedWhen(() -> {
			helper.assertTrue(!prisoner.isAlive(), Component.literal("Prisoner still alive"));
			helper.assertTrue(prisoner.position().distanceToSqr(Vec3.atBottomCenterOf(base.cell())) < 4.0,
				Component.literal("The execution should happen in the cell"));
			helper.assertTrue(KingdomManager.get(helper.getLevel().getServer()).kingdom(village.ruler().getUUID())
				.flatMap(k -> k.citizen(prisoner.getUUID())).isEmpty(), Component.literal("The executed citizen should leave the register"));
			helper.assertTrue(soldier.getMainHandItem().is(Items.IRON_SWORD), Component.literal("The executioner should take up the sword again"));
		});
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING, maxTicks = 1200)
	public void soldiersGoToTheBaseOnOrder(GameTestHelper helper) {
		Village village = village(helper, 2);
		for (CitizenEntity citizen : village.citizens()) {
			KingdomNetworking.commandCitizen(village.ruler(), citizen.getUUID(), CitizenJob.SOLDIER, null);
		}
		MilitaryBase base = instantBase(helper, village.ruler(), village.citizens().getFirst());

		helper.assertTrue(Army.order(village.ruler(), village.citizens().getFirst().getVillageId(), ArmyOrder.BASE),
			Component.literal("Order should be accepted"));

		helper.succeedWhen(() -> {
			for (CitizenEntity soldier : village.citizens()) {
				helper.assertTrue(soldier.position().distanceToSqr(Vec3.atBottomCenterOf(base.rally())) < 4.0 * 4.0,
					Component.literal(soldier.getPlainTextName() + " is not at the base yet"));
			}
		});
	}

	@GameTest(structure = ARENA, padding = TestKingdoms.PADDING, maxTicks = 1200)
	public void exhaustedWorkerSleepsInABed(GameTestHelper helper) {
		Village village = village(helper, 1);
		CitizenEntity worker = village.citizens().getFirst();
		KingdomNetworking.commandCitizen(village.ruler(), worker.getUUID(), CitizenJob.LUMBERJACK, null);
		BlockState bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.BLUE).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		helper.setBlock(new BlockPos(10, 1, 11), bed.setValue(BedBlock.PART, BedPart.HEAD));
		helper.setBlock(new BlockPos(10, 1, 10), bed.setValue(BedBlock.PART, BedPart.FOOT));
		while (!worker.isResting()) {
			worker.tire();
		}

		helper.succeedWhen(() -> helper.assertTrue(worker.isSleeping(), Component.literal("The tired worker should be asleep in the bed")));
	}
}
