package com.architecturoverse.citizen.work;

import com.architecturoverse.kingdom.MineSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The fixed digging order of a mine: a 3-high staircase down from the entrance to
 * {@code bottomY}, then a 2-high main tunnel with side branches every few blocks
 * (a classic strip mine). Positions with {@code torch} get a torch on the floor.
 */
public final class MinePlan {
	public static final int MAIN_TUNNEL_LENGTH = 48;
	public static final int BRANCH_SPACING = 6;
	public static final int BRANCH_LENGTH = 16;
	public static final int TORCH_SPACING = 8;
	/** How far below the entrance the staircase goes, at most. */
	public static final int MAX_DEPTH = 96;

	private static final Map<String, Layout> CACHE = new ConcurrentHashMap<>();

	public record Step(BlockPos pos, boolean torch) {
	}

	/** {@code route} lists the feet positions of every dug column in digging order, i.e. the way through the mine. */
	private record Layout(List<Step> steps, List<BlockPos> route) {
	}

	private MinePlan() {
	}

	public static List<Step> of(MineSite site, int minBuildY) {
		return layout(site, minBuildY).steps();
	}

	/** Feet positions along the mine from the top of the stairs to the last branch. */
	public static List<BlockPos> route(MineSite site, int minBuildY) {
		return layout(site, minBuildY).route();
	}

	private static Layout layout(MineSite site, int minBuildY) {
		int bottomY = Math.max(minBuildY + 8, site.entrance().getY() - 1 - MAX_DEPTH);
		String key = site.entrance().asLong() + "/" + site.facing() + "/" + bottomY;
		return CACHE.computeIfAbsent(key, k -> build(site.entrance(), site.facing(), bottomY));
	}

	private static Layout build(BlockPos entrance, Direction forward, int bottomY) {
		List<Step> steps = new ArrayList<>();
		List<BlockPos> route = new ArrayList<>();
		Direction left = forward.getCounterClockWise();
		Direction right = forward.getClockWise();

		// Staircase: each column one block further and one block lower, 3 blocks tall for head room.
		BlockPos column = entrance;
		int feet = entrance.getY();
		int stair = 0;
		while (feet > bottomY) {
			stair++;
			feet--;
			column = entrance.relative(forward, stair);
			BlockPos floor = new BlockPos(column.getX(), feet, column.getZ());
			steps.add(new Step(floor.above(2), false));
			steps.add(new Step(floor.above(), false));
			steps.add(new Step(floor, stair % TORCH_SPACING == 0));
			route.add(floor);
		}

		// Main tunnel with branches to both sides.
		BlockPos tunnelStart = new BlockPos(column.getX(), feet, column.getZ());
		for (int i = 1; i <= MAIN_TUNNEL_LENGTH; i++) {
			BlockPos floor = tunnelStart.relative(forward, i);
			steps.add(new Step(floor.above(), false));
			steps.add(new Step(floor, i % TORCH_SPACING == 0));
			route.add(floor);
			if (i % BRANCH_SPACING == 0) {
				for (Direction side : new Direction[] {left, right}) {
					for (int b = 1; b <= BRANCH_LENGTH; b++) {
						BlockPos branch = floor.relative(side, b);
						steps.add(new Step(branch.above(), false));
						steps.add(new Step(branch, b % TORCH_SPACING == 0));
						route.add(branch);
					}
				}
			}
		}
		return new Layout(List.copyOf(steps), List.copyOf(route));
	}
}
