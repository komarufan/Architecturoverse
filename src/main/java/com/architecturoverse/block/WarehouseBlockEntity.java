package com.architecturoverse.block;

import com.architecturoverse.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The village storehouse: a double-chest sized inventory that workers fill and builders take from. */
public class WarehouseBlockEntity extends BaseContainerBlockEntity {
	public static final int SIZE = 54;

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	public WarehouseBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.WAREHOUSE_ENTITY, pos, state);
	}

	/** Puts as much of the stack as fits into the warehouse and returns what did not fit. */
	public ItemStack insert(ItemStack stack) {
		ItemStack remainder = HopperBlockEntity.addItem(null, this, stack, null);
		setChanged();
		return remainder;
	}

	/** Takes up to {@code max} items matching the filter, as stacks. */
	public List<ItemStack> take(Predicate<ItemStack> filter, int max) {
		List<ItemStack> taken = new ArrayList<>();
		int left = max;
		for (int i = 0; i < SIZE && left > 0; i++) {
			ItemStack stack = items.get(i);
			if (!stack.isEmpty() && filter.test(stack)) {
				ItemStack part = stack.split(Math.min(left, stack.getCount()));
				left -= part.getCount();
				taken.add(part);
			}
		}
		if (!taken.isEmpty()) {
			setChanged();
		}
		return taken;
	}

	public int count(Predicate<ItemStack> filter) {
		int count = 0;
		for (ItemStack stack : items) {
			if (!stack.isEmpty() && filter.test(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.architecturoverse.warehouse");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> newItems) {
		this.items = newItems;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return ChestMenu.sixRows(containerId, inventory, this);
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}
}
