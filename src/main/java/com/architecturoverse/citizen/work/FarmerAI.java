package com.architecturoverse.citizen.work;

import com.architecturoverse.citizen.CitizenEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Harvests ripe crops on the village fields, replants them and sows empty farmland. */
public class FarmerAI extends WorkerAI {
	private static final int SEARCH_RADIUS = 48;
	private static final int MAX_TARGETS = 24;
	private static final int HARVESTS_PER_TRIP = 24;
	private static final int SEEDS_TO_KEEP = 16;

	private final Deque<BlockPos> targets = new ArrayDeque<>();
	private int searchCooldown;
	private int harvestsSinceDeposit;

	public FarmerAI(CitizenEntity citizen) {
		super(citizen);
	}

	@Override
	public boolean hasWork() {
		if (inventoryFull()) {
			return false;
		}
		if (!targets.isEmpty()) {
			return true;
		}
		if (--searchCooldown > 0) {
			return false;
		}
		searchCooldown = 200;
		findFields();
		return !targets.isEmpty();
	}

	@Override
	public void tick() {
		BlockPos target = targets.peek();
		if (target == null) {
			return;
		}
		ServerLevel level = level();
		BlockState state = level.getBlockState(target);
		boolean ripe = isRipe(state);
		boolean sowable = canSow(level, target);
		if (!ripe && !sowable) {
			targets.poll();
			return;
		}
		if (!walkTo(target, 2.0)) {
			return;
		}
		if (ripe) {
			CropBlock crop = (CropBlock) state.getBlock();
			if (dig(target)) {
				harvestsSinceDeposit++;
				if (takeOne(crop.asItem().getDefaultInstance())) {
					level.setBlockAndUpdate(target, crop.defaultBlockState());
				}
				targets.poll();
			}
		} else {
			sow(level, target);
			targets.poll();
		}
	}

	@Override
	public boolean wantsToDeposit() {
		return harvestsSinceDeposit >= HARVESTS_PER_TRIP || targets.isEmpty() && harvestsSinceDeposit > 0;
	}

	@Override
	public void onDeposited() {
		harvestsSinceDeposit = 0;
	}

	@Override
	public int keepCount(ItemStack stack) {
		return isSeed(stack) ? SEEDS_TO_KEEP : 0;
	}

	private static boolean isRipe(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	private boolean canSow(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).getBlock() instanceof FarmlandBlock && findSeed() >= 0;
	}

	private void sow(ServerLevel level, BlockPos pos) {
		int slot = findSeed();
		if (slot < 0) {
			return;
		}
		ItemStack seed = citizen.getInventory().getItem(slot);
		BlockItem item = (BlockItem) seed.getItem();
		level.setBlockAndUpdate(pos, item.getBlock().defaultBlockState());
		seed.shrink(1);
	}

	private static boolean isSeed(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock;
	}

	private int findSeed() {
		SimpleContainer inventory = citizen.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (isSeed(inventory.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	private boolean takeOne(ItemStack wanted) {
		SimpleContainer inventory = citizen.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (ItemStack.isSameItem(stack, wanted)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private void findFields() {
		ServerLevel level = level();
		BlockPos origin = citizen.hasHome() ? citizen.getHomePosition() : citizen.blockPosition();
		boolean haveSeeds = findSeed() >= 0;
		List<BlockPos> found = new ArrayList<>();
		BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
				column.set(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				column.setY(level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1);
				BlockState state = level.getBlockState(column);
				if (isRipe(state)) {
					found.add(column.immutable());
				} else if (haveSeeds && state.getBlock() instanceof FarmlandBlock) {
					found.add(column.above().immutable());
				}
			}
		}
		// Visit fields in a chain of nearest neighbours instead of zig-zagging across the village.
		BlockPos from = citizen.blockPosition();
		while (!found.isEmpty() && targets.size() < MAX_TARGETS) {
			BlockPos start = from;
			BlockPos next = found.stream().min(Comparator.comparingDouble(p -> p.distSqr(start))).orElseThrow();
			found.remove(next);
			targets.add(next);
			from = next;
		}
	}
}
