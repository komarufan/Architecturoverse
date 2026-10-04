package com.architecturoverse.citizen.work;

import com.architecturoverse.citizen.CitizenEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/** Finds natural trees around the village, fells them log by log and replants a sapling. */
public class LumberjackAI extends WorkerAI {
	private static final int NEAR_RADIUS = 40;
	private static final int FAR_RADIUS = 64;
	private static final int MAX_TREE_LOGS = 200;
	private static final int TREES_PER_TRIP = 3;

	private @Nullable BlockPos treeBase;
	private @Nullable Block logBlock;
	private final List<BlockPos> logs = new ArrayList<>();
	private int searchCooldown;
	private int treesSinceDeposit;

	public LumberjackAI(CitizenEntity citizen) {
		super(citizen);
	}

	@Override
	public boolean hasWork() {
		if (inventoryFull()) {
			return false;
		}
		if (treeBase != null) {
			return true;
		}
		if (--searchCooldown > 0) {
			return false;
		}
		searchCooldown = 100;
		if (!findTree(NEAR_RADIUS)) {
			findTree(FAR_RADIUS);
		}
		return treeBase != null;
	}

	@Override
	public void tick() {
		if (treeBase == null) {
			return;
		}
		ServerLevel level = level();
		logs.removeIf(pos -> !level.getBlockState(pos).is(BlockTags.LOGS));
		if (logs.isEmpty()) {
			finishTree();
			return;
		}
		if (walkTo(treeBase, 3.0) && dig(logs.getFirst())) {
			logs.removeFirst();
		}
	}

	@Override
	public boolean wantsToDeposit() {
		return treesSinceDeposit >= TREES_PER_TRIP;
	}

	@Override
	public void onDeposited() {
		treesSinceDeposit = 0;
	}

	private void finishTree() {
		BlockPos base = treeBase;
		treeBase = null;
		treesSinceDeposit++;
		if (base == null) {
			return;
		}
		replant(base);
		collectItems(base, 7.0);
	}

	private void replant(BlockPos base) {
		ServerLevel level = level();
		if (logBlock == null || !level.getBlockState(base).isAir() || !level.getBlockState(base.below()).is(BlockTags.SUBSTRATE_OVERWORLD)) {
			return;
		}
		saplingFor(logBlock).ifPresent(sapling -> level.setBlockAndUpdate(base, sapling.defaultBlockState()));
	}

	/** oak_log becomes oak_sapling, and so on; logs without a matching sapling are not replanted. */
	private static Optional<Block> saplingFor(Block log) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(log);
		String path = id.getPath().replace("stripped_", "").replace("_log", "_sapling").replace("_wood", "_sapling");
		return BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), path))
			.filter(block -> block instanceof SaplingBlock);
	}

	/** Picks the nearest real tree within {@code radius} of the village; returns false if there is none. */
	private boolean findTree(int radius) {
		ServerLevel level = level();
		BlockPos origin = citizen.hasHome() ? citizen.getHomePosition() : citizen.blockPosition();
		List<BlockPos> candidates = new ArrayList<>();
		BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				column.set(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
				column.setY(top);
				if (level.getBlockState(column).is(BlockTags.LOGS)) {
					candidates.add(column.immutable());
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(citizen.blockPosition())));
		for (BlockPos top : candidates) {
			BlockPos base = trunkBase(level, top);
			if (base != null && hasNaturalLeaves(level, top)) {
				treeBase = base;
				logBlock = level.getBlockState(base).getBlock();
				collectLogs(level, base);
				return true;
			}
		}
		return false;
	}

	/** Walks down the trunk; a real tree stands on dirt. */
	private static @Nullable BlockPos trunkBase(ServerLevel level, BlockPos top) {
		BlockPos pos = top;
		for (int i = 0; i < 40 && level.getBlockState(pos.below()).is(BlockTags.LOGS); i++) {
			pos = pos.below();
		}
		return level.getBlockState(pos.below()).is(BlockTags.SUBSTRATE_OVERWORLD) ? pos : null;
	}

	/** Village houses use logs too, but only trees have leaves that grew naturally. */
	private static boolean hasNaturalLeaves(ServerLevel level, BlockPos top) {
		for (BlockPos pos : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
				return true;
			}
		}
		return false;
	}

	private void collectLogs(ServerLevel level, BlockPos base) {
		logs.clear();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(base);
		seen.add(base);
		while (!queue.isEmpty() && logs.size() < MAX_TREE_LOGS) {
			BlockPos pos = queue.poll();
			logs.add(pos);
			for (BlockPos next : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) {
				if (!seen.contains(next) && level.getBlockState(next).is(BlockTags.LOGS)) {
					BlockPos immutable = next.immutable();
					seen.add(immutable);
					queue.add(immutable);
				}
			}
		}
		// Fell from the top so the trunk never floats.
		logs.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
	}
}
