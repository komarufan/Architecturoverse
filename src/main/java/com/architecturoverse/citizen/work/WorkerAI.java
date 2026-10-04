package com.architecturoverse.citizen.work;

import com.architecturoverse.citizen.CitizenEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shared toolkit for working citizens: walking, digging blocks at a hardness-based pace and keeping the loot. */
public abstract class WorkerAI {
	protected final CitizenEntity citizen;

	private @Nullable BlockPos digPos;
	private int digProgress;

	protected WorkerAI(CitizenEntity citizen) {
		this.citizen = citizen;
	}

	/** Whether there is something to do right now. May search for work, so keep it cheap or rate-limited. */
	public abstract boolean hasWork();

	public abstract void tick();

	/** Whether the worker should empty its inventory into the warehouse now, even if it is not full. */
	public boolean wantsToDeposit() {
		return false;
	}

	/** Called after the inventory was emptied into the warehouse. */
	public void onDeposited() {
	}

	/** How many items of this kind the worker keeps when depositing (e.g. seeds for replanting). */
	public int keepCount(ItemStack stack) {
		return 0;
	}

	public void stop() {
		citizen.getWalker().reset();
		cancelDigging();
	}

	protected ServerLevel level() {
		return (ServerLevel) citizen.level();
	}

	protected boolean inventoryFull() {
		for (int i = 0; i < citizen.getInventory().getContainerSize(); i++) {
			if (citizen.getInventory().getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	protected boolean walkTo(BlockPos target, double reach) {
		return citizen.walkTo(target, reach);
	}

	/**
	 * Works on breaking the block; call every tick while in reach. Returns true when the
	 * block is gone and its drops are in the citizen's inventory.
	 */
	protected boolean dig(BlockPos pos) {
		ServerLevel level = level();
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			cancelDigging();
			return true;
		}
		if (!pos.equals(digPos)) {
			cancelDigging();
			digPos = pos.immutable();
			digProgress = 0;
		}
		int needed = Math.max(6, (int) (state.getDestroySpeed(level, pos) * 12));
		citizen.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		if (digProgress % 6 == 0) {
			citizen.swing(InteractionHand.MAIN_HAND);
		}
		digProgress++;
		level.destroyBlockProgress(citizen.getId(), pos, Math.min(9, digProgress * 10 / needed));
		if (digProgress < needed) {
			return false;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, citizen, citizen.getMainHandItem());
		level.destroyBlock(pos, false, citizen);
		drops.forEach(this::keep);
		cancelDigging();
		return true;
	}

	protected void cancelDigging() {
		if (digPos != null) {
			level().destroyBlockProgress(citizen.getId(), digPos, -1);
			digPos = null;
		}
		digProgress = 0;
	}

	/** Adds the stack to the citizen's inventory; whatever does not fit is dropped at its feet. */
	protected void keep(ItemStack stack) {
		ItemStack rest = citizen.getInventory().addItem(stack);
		if (!rest.isEmpty()) {
			Block.popResource(level(), citizen.blockPosition(), rest);
		}
	}

	/** Picks up loose items around a spot, e.g. saplings and apples falling from leaves. */
	protected void collectItems(BlockPos center, double radius) {
		for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(radius), ItemEntity::isAlive)) {
			ItemStack rest = citizen.getInventory().addItem(item.getItem().copy());
			if (rest.isEmpty()) {
				item.discard();
			} else {
				item.setItem(rest);
			}
		}
	}
}
