package com.architecturoverse.village;

import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.structure.Blueprint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Picks spots for buildings that builders put up on their own. */
public final class SiteFinder {
	private static final int WAREHOUSE_MIN_DISTANCE = 3;
	private static final int WAREHOUSE_MAX_DISTANCE = 16;
	private static final int WAREHOUSE_MAX_HEIGHT_DIFFERENCE = 4;
	private static final int[] MINE_DISTANCES = {28, 36, 20, 44};
	private static final int STRUCTURE_MIN_DISTANCE = 12;
	private static final int MAX_UNEVENNESS = 2;

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

	/** Where a building goes: the blueprint's origin corner (at floor level) and how it is turned. */
	public record StructureSite(BlockPos origin, Rotation rotation) {
	}

	/**
	 * A flat patch of natural ground between the houses and the village edge, big enough for
	 * the blueprint, with nothing built on it. The entrance is turned towards the bell.
	 */
	public static Optional<StructureSite> structure(ServerLevel level, BlockPos center, Blueprint blueprint) {
		int size = Math.max(blueprint.width(), blueprint.depth());
		for (int distance = STRUCTURE_MIN_DISTANCE + size / 2; distance <= ClaimedVillage.RADIUS - size / 2; distance += 3) {
			int points = Math.max(8, distance / 2);
			for (int i = 0; i < points; i++) {
				double angle = 2 * Math.PI * i / points;
				BlockPos middle = center.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
				Direction front = towards(middle, center);
				Rotation rotation = rotationFacing(front);
				// Blueprint origin is the front-left corner; shift so the building is centered on `middle`.
				BlockPos half = new BlockPos(blueprint.width() / 2, 0, blueprint.depth() / 2).rotate(rotation);
				BlockPos corner = middle.subtract(half);
				Optional<Integer> floorY = flatFloor(level, blueprint.footprint(corner, rotation));
				if (floorY.isPresent()) {
					BlockPos origin = new BlockPos(corner.getX(), floorY.get(), corner.getZ());
					if (isFree(level, blueprint, origin, rotation)) {
						return Optional.of(new StructureSite(origin, rotation));
					}
				}
			}
		}
		return Optional.empty();
	}

	/** The rotation that turns the blueprint's front (north) towards {@code front}. */
	private static Rotation rotationFacing(Direction front) {
		return switch (front) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/**
	 * The floor height for the footprint (the most common ground height) if the ground there is
	 * natural, dry and varies by at most {@link #MAX_UNEVENNESS} blocks.
	 */
	private static Optional<Integer> flatFloor(ServerLevel level, List<BlockPos> footprint) {
		Map<Integer, Integer> heights = new HashMap<>();
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (BlockPos column : footprint) {
			if (!level.isLoaded(column)) {
				return Optional.empty();
			}
			int y = groundY(level, column);
			BlockState ground = level.getBlockState(new BlockPos(column.getX(), y, column.getZ()));
			if (!isNaturalGround(ground) || ground.is(Blocks.DIRT_PATH) || ground.getBlock() instanceof FarmlandBlock) {
				return Optional.empty();
			}
			min = Math.min(min, y);
			max = Math.max(max, y);
			heights.merge(y, 1, Integer::sum);
		}
		if (max - min > MAX_UNEVENNESS) {
			return Optional.empty();
		}
		return heights.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
	}

	/** Highest ground block of the column, looking through trees and plants. */
	private static int groundY(ServerLevel level, BlockPos column) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(column.getX(),
			level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1, column.getZ());
		while (pos.getY() > level.getMinY()) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.canBeReplaced()) {
				break;
			}
			pos.move(Direction.DOWN);
		}
		return pos.getY();
	}

	/** Nothing but air, plants and trees where the building's rooms will be. */
	private static boolean isFree(ServerLevel level, Blueprint blueprint, BlockPos origin, Rotation rotation) {
		for (BlockPos column : blueprint.footprint(origin, rotation)) {
			for (int y = 1; y < blueprint.height(); y++) {
				BlockState state = level.getBlockState(column.above(y));
				if (!state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
					&& !isNaturalGround(state) || !state.getFluidState().isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean isNaturalGround(BlockState state) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND)
			|| state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.CLAY);
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
