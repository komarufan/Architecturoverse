package com.architecturoverse.citizen.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Walks a mob to a block with vanilla pathfinding. If the mob makes no progress for a
 * while (blocked path, fell into a hole) it hops to a free spot near the target.
 */
public class Walker {
	private static final int STUCK_TICKS_BEFORE_TELEPORT = 160;
	private static final double SPEED = 0.6;

	private final PathfinderMob mob;
	private @Nullable BlockPos target;
	private double bestDistanceSq;
	private int stuckTicks;
	private int repathCooldown;

	public Walker(PathfinderMob mob) {
		this.mob = mob;
	}

	/** Moves towards the block; returns true once the mob is within {@code reach} of its center. */
	public boolean walkTo(BlockPos destination, double reach) {
		double distanceSq = mob.distanceToSqr(Vec3.atCenterOf(destination));
		if (distanceSq <= reach * reach) {
			mob.getNavigation().stop();
			target = null;
			return true;
		}
		if (!destination.equals(target)) {
			target = destination.immutable();
			bestDistanceSq = Double.MAX_VALUE;
			stuckTicks = 0;
			repathCooldown = 0;
		}
		if (--repathCooldown <= 0 || mob.getNavigation().isDone()) {
			mob.getNavigation().moveTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, SPEED);
			repathCooldown = 40;
		}
		if (distanceSq < bestDistanceSq - 0.25) {
			bestDistanceSq = distanceSq;
			stuckTicks = 0;
		} else if (++stuckTicks > STUCK_TICKS_BEFORE_TELEPORT) {
			stuckTicks = 0;
			bestDistanceSq = Double.MAX_VALUE;
			teleportNear(destination);
		}
		return false;
	}

	public void reset() {
		target = null;
		mob.getNavigation().stop();
	}

	/** Jumps to the free spot closest to the target, like a pet catching up with its owner. */
	public boolean teleportNear(BlockPos destination) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(destination.offset(-3, -3, -3), destination.offset(3, 3, 3))) {
			if (canStandAt(mob.level(), pos)) {
				double distance = pos.distSqr(destination);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}
		if (best == null) {
			return false;
		}
		mob.getNavigation().stop();
		mob.snapTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, mob.getYRot(), mob.getXRot());
		return true;
	}

	public static boolean canStandAt(Level level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.isFaceSturdy(level, pos.below(), Direction.UP) && isPassable(level, pos) && isPassable(level, pos.above());
	}

	private static boolean isPassable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.blocksMotion() && state.getFluidState().isEmpty();
	}
}
