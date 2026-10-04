package com.architecturoverse.citizen.work;

import com.architecturoverse.kingdom.MineSite;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla pathfinding gives up after a few dozen blocks, which is far less than the way
 * down a deep mine. Inside a mine citizens therefore walk the mine's own route in short
 * hops, which the pathfinder always manages.
 */
public final class MineRoute {
	/** Route points per hop; a few stairs down or a few tunnel blocks along. */
	private static final int HOP = 8;
	/** How close a position must be to the route to count as inside the mine. */
	private static final double ON_ROUTE_DISTANCE_SQ = 3.0 * 3.0;

	private MineRoute() {
	}

	/**
	 * The next point to walk to on the way from {@code from} to {@code to}, or empty if a
	 * direct walk is short enough. Works both ways: into the mine, along it and back out.
	 */
	public static Optional<BlockPos> waypoint(MineSite site, int minBuildY, Vec3 from, BlockPos to) {
		List<BlockPos> route = MinePlan.route(site, minBuildY);
		int current = nearest(route, from);
		int target = nearest(route, Vec3.atCenterOf(to));
		if (current < 0 && target < 0) {
			return Optional.empty();
		}
		if (current < 0) {
			// Entering from the surface: head for the stairs first.
			return target <= HOP ? Optional.empty() : Optional.of(route.get(HOP));
		}
		if (target < 0) {
			// Leaving: climb back up towards the entrance.
			return current <= HOP ? Optional.empty() : Optional.of(route.get(current - HOP));
		}
		if (Math.abs(target - current) <= HOP) {
			return Optional.empty();
		}
		return Optional.of(route.get(current + Integer.signum(target - current) * HOP));
	}

	private static int nearest(List<BlockPos> route, Vec3 pos) {
		int best = -1;
		double bestDistance = ON_ROUTE_DISTANCE_SQ;
		for (int i = 0; i < route.size(); i++) {
			double distance = Vec3.atBottomCenterOf(route.get(i)).distanceToSqr(pos);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}
}
