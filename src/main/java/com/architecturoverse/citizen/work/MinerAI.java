package com.architecturoverse.citizen.work;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.MineSite;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Digs the village mine step by step following {@link MinePlan}, sealing off water and lava on the way. */
public class MinerAI extends WorkerAI {
	private static final int BLOCKS_PER_TRIP = 64;

	private int blocksSinceDeposit;
	private boolean reportedMissingMine;
	private boolean reportedFinished;

	public MinerAI(CitizenEntity citizen) {
		super(citizen);
	}

	private Optional<MineSite> site() {
		return citizen.getVillage().flatMap(ClaimedVillage::mine);
	}

	@Override
	public boolean hasWork() {
		if (inventoryFull()) {
			return false;
		}
		Optional<MineSite> site = site();
		if (site.isEmpty()) {
			if (!reportedMissingMine) {
				reportedMissingMine = true;
				citizen.notifyRuler("message.architecturoverse.no_mine");
			}
			return false;
		}
		reportedMissingMine = false;
		boolean finished = site.get().progress() >= MinePlan.of(site.get(), level().getMinY()).size();
		if (finished && !reportedFinished) {
			reportedFinished = true;
			citizen.notifyRuler("message.architecturoverse.mine_done");
		}
		if (!finished) {
			reportedFinished = false;
		}
		return !finished && level().isLoaded(site.get().entrance());
	}

	@Override
	public void tick() {
		Optional<MineSite> found = site();
		if (found.isEmpty()) {
			return;
		}
		MineSite site = found.get();
		ServerLevel level = level();
		List<MinePlan.Step> plan = MinePlan.of(site, level.getMinY());
		if (site.progress() >= plan.size()) {
			return;
		}
		MinePlan.Step step = plan.get(site.progress());
		BlockPos pos = step.pos();
		BlockState state = level.getBlockState(pos);

		// A block counts as done only once it is seen empty, so gravel falling back in gets dug again.
		if (state.isAir()) {
			sealFluids(level, pos);
			if (step.torch() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
				level.setBlockAndUpdate(pos, Blocks.TORCH.defaultBlockState());
			}
			advance(site);
			return;
		}
		if (state.getDestroySpeed(level, pos) < 0) {
			advance(site); // bedrock and other unbreakable blocks are left alone
			return;
		}
		if (!state.getFluidState().isEmpty() && !state.blocksMotion()) {
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			return;
		}
		if (walkTo(pos, 4.0) && dig(pos)) {
			blocksSinceDeposit++;
		}
	}

	private void advance(MineSite site) {
		citizen.getKingdomManager().ifPresent(manager -> citizen.getKingdom().ifPresent(kingdom ->
			manager.updateVillage(kingdom, citizen.getVillageId(), v -> v.withMine(Optional.of(site.withProgress(site.progress() + 1))))));
	}

	/** Water and lava next to the tunnel are plugged with cobblestone so the tunnel stays dry. */
	private static void sealFluids(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos side = pos.relative(direction);
			BlockState state = level.getBlockState(side);
			if (!state.getFluidState().isEmpty() && !state.is(Blocks.TORCH)) {
				level.setBlockAndUpdate(side, Blocks.COBBLESTONE.defaultBlockState());
			}
		}
	}

	@Override
	public boolean wantsToDeposit() {
		return blocksSinceDeposit >= BLOCKS_PER_TRIP;
	}

	@Override
	public void onDeposited() {
		blocksSinceDeposit = 0;
	}
}
