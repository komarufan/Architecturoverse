package com.architecturoverse.citizen.goal;

import com.architecturoverse.block.WarehouseBlockEntity;
import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenMode;
import com.architecturoverse.citizen.work.WorkerAI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Runs the citizen's job while it is ordered to work, and carries the loot to the
 * village warehouse whenever the pockets are full or the job asks for it.
 */
public class WorkGoal extends Goal {
	private final CitizenEntity citizen;
	private boolean depositing;
	private boolean idle;

	public WorkGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (citizen.getMode() != CitizenMode.WORK || citizen.getTarget() != null) {
			return false;
		}
		WorkerAI ai = citizen.getWorkerAI();
		idle = ai == null || !ai.hasWork();
		return !idle || shouldDeposit(ai);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		WorkerAI ai = citizen.getWorkerAI();
		if (ai != null) {
			ai.stop();
		}
		depositing = false;
	}

	@Override
	public void tick() {
		WorkerAI ai = citizen.getWorkerAI();
		if (depositing || shouldDeposit(ai)) {
			depositing = true;
			if (deposit(ai)) {
				depositing = false;
			}
			return;
		}
		if (ai != null && !idle) {
			ai.tick();
		}
	}

	private boolean shouldDeposit(@Nullable WorkerAI ai) {
		SimpleContainer inventory = citizen.getInventory();
		if (!hasSomethingToDeposit(ai)) {
			return false;
		}
		if (citizen.getWarehousePos().isEmpty()) {
			if (ai != null && freeSlots(inventory) == 0) {
				citizen.notifyRuler("message.architecturoverse.no_warehouse");
			}
			return false;
		}
		if (ai == null || idle) {
			return true; // nothing left to do (or not a worker any more): hand in what was gathered
		}
		return freeSlots(inventory) <= 2 || ai.wantsToDeposit();
	}

	/** Walks to the warehouse and empties the inventory; returns true when the trip is over. */
	private boolean deposit(@Nullable WorkerAI ai) {
		Optional<BlockPos> warehousePos = citizen.getWarehousePos();
		if (warehousePos.isEmpty()) {
			return true;
		}
		if (!citizen.walkTo(warehousePos.get(), 2.5)) {
			return false;
		}
		if (!(citizen.level().getBlockEntity(warehousePos.get()) instanceof WarehouseBlockEntity warehouse)) {
			return true;
		}
		SimpleContainer inventory = citizen.getInventory();
		Map<Item, Integer> kept = new HashMap<>();
		boolean warehouseFull = false;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			int keep = ai == null ? 0 : Math.max(0, ai.keepCount(stack) - kept.getOrDefault(stack.getItem(), 0));
			int toKeep = Math.min(keep, stack.getCount());
			kept.merge(stack.getItem(), toKeep, Integer::sum);
			if (toKeep == stack.getCount()) {
				continue;
			}
			ItemStack moving = stack.split(stack.getCount() - toKeep);
			ItemStack rest = warehouse.insert(moving);
			if (!rest.isEmpty()) {
				warehouseFull = true;
				stack.grow(rest.getCount());
			}
		}
		inventory.setChanged();
		if (warehouseFull) {
			citizen.notifyRuler("message.architecturoverse.warehouse_full");
		}
		if (ai != null) {
			ai.onDeposited();
		}
		return true;
	}

	/** True if the inventory holds more than the worker wants to keep for itself. */
	private boolean hasSomethingToDeposit(@Nullable WorkerAI ai) {
		SimpleContainer inventory = citizen.getInventory();
		Map<Item, Integer> allowance = new HashMap<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			int left = allowance.computeIfAbsent(stack.getItem(), item -> ai == null ? 0 : ai.keepCount(stack));
			if (stack.getCount() > left) {
				return true;
			}
			allowance.put(stack.getItem(), left - stack.getCount());
		}
		return false;
	}

	private static int freeSlots(SimpleContainer inventory) {
		int free = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).isEmpty()) {
				free++;
			}
		}
		return free;
	}
}
