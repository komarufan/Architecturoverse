package com.architecturoverse.structure;

import com.architecturoverse.kingdom.Construction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The ordered block-by-block work list for a building site: first fill holes under the
 * floor with dirt, then every blueprint block layer by layer from the bottom up. Air steps
 * clear whatever grows or stands where the rooms will be.
 */
public final class ConstructionPlan {
	private static final int FOUNDATION_DEPTH = 3;
	private static final Map<Construction, List<Step>> CACHE = new ConcurrentHashMap<>();

	/**
	 * One block of work. {@code foundation} steps only fill empty space; {@code extra} is the
	 * other half of two-block things (beds), placed together with the main block.
	 */
	public record Step(BlockPos pos, BlockState state, Material material, boolean foundation,
		@Nullable BlockPos extraPos, @Nullable BlockState extraState) {

		/** Whether the world already looks like this step wants it to. */
		public boolean isDone(ServerLevel level) {
			BlockState current = level.getBlockState(pos);
			if (foundation) {
				return current.blocksMotion();
			}
			if (state.isAir()) {
				return current.isAir();
			}
			if (!current.is(state.getBlock())) {
				return false;
			}
			return extraPos == null || extraState == null || level.getBlockState(extraPos).is(extraState.getBlock());
		}

		/** What placing this step costs; clearing and foundation dirt are free. */
		public Material cost() {
			return foundation || state.isAir() ? Material.NONE : material;
		}
	}

	private ConstructionPlan() {
	}

	public static List<Step> of(Construction site) {
		return CACHE.computeIfAbsent(new Construction(site.type(), site.origin(), site.rotation(), 0), ConstructionPlan::build);
	}

	private static List<Step> build(Construction site) {
		Blueprint blueprint = site.type().blueprint();
		List<Step> steps = new ArrayList<>();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		for (int depth = FOUNDATION_DEPTH; depth >= 1; depth--) {
			for (BlockPos column : blueprint.footprint(site.origin(), site.rotation())) {
				steps.add(new Step(column.below(depth), dirt, Material.NONE, true, null, null));
			}
		}
		for (Blueprint.Entry entry : blueprint.entries()) {
			BlockPos pos = Blueprint.toWorld(entry.pos(), site.origin(), site.rotation());
			BlockState state = entry.state().rotate(site.rotation());
			BlockPos extraPos = entry.extraPos() == null ? null : Blueprint.toWorld(entry.extraPos(), site.origin(), site.rotation());
			BlockState extraState = entry.extraState() == null ? null : entry.extraState().rotate(site.rotation());
			steps.add(new Step(pos, state, entry.material(), false, extraPos, extraState));
		}
		return List.copyOf(steps);
	}
}
