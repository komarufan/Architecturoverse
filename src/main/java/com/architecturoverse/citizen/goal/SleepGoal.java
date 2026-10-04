package com.architecturoverse.citizen.goal;

import com.architecturoverse.citizen.CitizenEntity;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * An exhausted worker looks for a free bed in the village, sleeps until fully rested and then
 * goes back to work. Without a free bed it rests on the spot, which takes twice as long.
 */
public class SleepGoal extends Goal {
	private static final int BED_SEARCH_RADIUS = 48;
	/** Beds taken by a citizen on the way to them, so two tired workers do not race for the same one. */
	private static final Map<BlockPos, UUID> RESERVED = new HashMap<>();

	private final CitizenEntity citizen;
	private @Nullable BlockPos bed;
	private boolean searched;

	public SleepGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		return citizen.isResting() && citizen.getTarget() == null;
	}

	@Override
	public boolean canContinueToUse() {
		return citizen.isResting();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		searched = false;
		bed = null;
	}

	@Override
	public void stop() {
		if (citizen.isSleeping()) {
			citizen.stopSleeping();
		}
		if (bed != null) {
			RESERVED.remove(bed);
			bed = null;
		}
	}

	@Override
	public void tick() {
		ServerLevel level = (ServerLevel) citizen.level();
		if (!searched) {
			searched = true;
			bed = findBed(level).orElse(null);
			if (bed != null) {
				RESERVED.put(bed, citizen.getUUID());
			} else {
				citizen.notifyRuler("message.architecturoverse.no_beds");
			}
		}
		if (citizen.isSleeping()) {
			citizen.recover(1.0F);
			return;
		}
		if (bed != null && isFreeBed(level, bed)) {
			if (citizen.walkTo(bed, 1.6)) {
				citizen.startSleeping(bed);
			}
			return;
		}
		bed = null;
		// No bed: sit down where you are and catch your breath.
		citizen.getNavigation().stop();
		citizen.recover(0.5F);
		if (citizen.tickCount % 40 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, citizen.getX(), citizen.getY() + 2.0, citizen.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	private Optional<BlockPos> findBed(ServerLevel level) {
		BlockPos center = citizen.hasHome() ? citizen.getHomePosition() : citizen.blockPosition();
		return level.getPoiManager().findClosest(
			type -> type.is(PoiTypes.HOME),
			pos -> isFreeBed(level, pos),
			citizen.blockPosition().closerThan(center, BED_SEARCH_RADIUS) ? citizen.blockPosition() : center,
			BED_SEARCH_RADIUS,
			PoiManager.Occupancy.ANY);
	}

	private boolean isFreeBed(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.OCCUPIED)) {
			return false;
		}
		UUID holder = RESERVED.get(pos);
		return holder == null || holder.equals(citizen.getUUID());
	}
}
