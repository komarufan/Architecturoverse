package com.architecturoverse.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Picks spots for buildings that builders put up on their own. */
public final class SiteFinder {
	private static final int WAREHOUSE_MIN_DISTANCE = 3;
	private static final int WAREHOUSE_MAX_DISTANCE = 16;
	private static final int WAREHOUSE_MAX_HEIGHT_DIFFERENCE = 4;
	private static final int[] MINE_DISTANCES = {28, 36, 20, 44};

	/** Where to put a block and which way its front should face. */
	public record Site(BlockPos pos, Direction facing) {
	}

	private SiteFinder() {
	}

	/** A free spot on solid ground close to the bell, its front turned towards the bell. */
	public static Optional<Site> warehouse(ServerLevel level, BlockPos center) {
		List<BlockPos> candidates = new ArrayList<>();
		for (int dx = -WAREHOUSE_MAX_DISTANCE; dx <= WAREHOUSE_MAX_DISTANCE; dx++) {
			for (int dz = -WAREHOUSE_MAX_DISTANCE; dz <= WAREHOUSE_MAX_DISTANCE; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) >= WAREHOUSE_MIN_DISTANCE) {
					candidates.add(center.offset(dx, 0, dz));
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
		for (BlockPos column : candidates) {
			Optional<BlockPos> ground = surface(level, column);
			if (ground.isPresent() && Math.abs(ground.get().getY() - center.getY()) <= WAREHOUSE_MAX_HEIGHT_DIFFERENCE
				&& isBuildable(level, ground.get()) && !nextToDoor(level, ground.get())) {
				return Optional.of(new Site(ground.get(), towards(ground.get(), center)));
			}
		}
		return Optional.empty();
	}

	/**
	 * A spot near the edge of the village on natural ground. The entrance's doorway faces the
	 * village and the miners dig away from it, so the tunnel never runs under the houses.
	 */
	public static Optional<Site> mine(ServerLevel level, BlockPos center) {
		for (int distance : MINE_DISTANCES) {
			for (int step = 0; step < 8; step++) {
				double angle = Math.PI / 4 * step;
				BlockPos column = center.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
				Optional<BlockPos> ground = surface(level, column);
				if (ground.isEmpty() || !isBuildable(level, ground.get())) {
					continue;
				}
				BlockState below = level.getBlockState(ground.get().below());
				if (below.is(BlockTags.SUBSTRATE_OVERWORLD) || below.is(BlockTags.BASE_STONE_OVERWORLD) || below.is(BlockTags.SAND)) {
					return Optional.of(new Site(ground.get(), towards(ground.get(), center)));
				}
			}
		}
		return Optional.empty();
	}

	/** The first free block above the ground at this column, if the chunk is loaded. */
	private static Optional<BlockPos> surface(ServerLevel level, BlockPos column) {
		if (!level.isLoaded(column)) {
			return Optional.empty();
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		return Optional.of(new BlockPos(column.getX(), y, column.getZ()));
	}

	/** Solid, dry ground that is not a village path or field, with room for the block. */
	public static boolean isBuildable(ServerLevel level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		BlockState here = level.getBlockState(pos);
		return below.isFaceSturdy(level, pos.below(), Direction.UP)
			&& below.getFluidState().isEmpty()
			&& !below.is(Blocks.DIRT_PATH)
			&& !(below.getBlock() instanceof FarmlandBlock)
			&& !below.is(BlockTags.LEAVES)
			&& (here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty()
			&& level.getBlockState(pos.above()).isAir();
	}

	private static boolean nextToDoor(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(pos.relative(direction)).is(BlockTags.DOORS)) {
				return true;
			}
		}
		return false;
	}

	/** The horizontal direction pointing from {@code from} roughly towards {@code to}. */
	private static Direction towards(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}
}
