package com.architecturoverse.trade;

import java.util.List;
import java.util.Map;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What the travelling merchant pays for goods, in emeralds per item. */
public final class TradePrices {
	private static final double DEFAULT_PRICE = 1.0 / 32;
	private static final Map<Item, Double> PRICES = Map.ofEntries(
		Map.entry(Items.DIAMOND, 4.0),
		Map.entry(Items.GOLD_INGOT, 1.0),
		Map.entry(Items.RAW_GOLD, 0.5),
		Map.entry(Items.IRON_INGOT, 0.5),
		Map.entry(Items.RAW_IRON, 1.0 / 3),
		Map.entry(Items.COPPER_INGOT, 0.1),
		Map.entry(Items.RAW_COPPER, 1.0 / 12),
		Map.entry(Items.COAL, 1.0 / 8),
		Map.entry(Items.LAPIS_LAZULI, 0.1),
		Map.entry(Items.REDSTONE, 1.0 / 20),
		Map.entry(Items.COBBLESTONE, 1.0 / 64),
		Map.entry(Items.COBBLED_DEEPSLATE, 1.0 / 64),
		Map.entry(Items.DIRT, 1.0 / 128),
		Map.entry(Items.GRAVEL, 1.0 / 128),
		Map.entry(Items.WHEAT, 1.0 / 20),
		Map.entry(Items.CARROT, 1.0 / 24),
		Map.entry(Items.POTATO, 1.0 / 24),
		Map.entry(Items.BEETROOT, 1.0 / 16),
		Map.entry(Items.APPLE, 1.0 / 8),
		Map.entry(Items.WHEAT_SEEDS, 1.0 / 64),
		Map.entry(Items.STICK, 1.0 / 128)
	);

	private TradePrices() {
	}

	public static double priceOf(ItemStack stack) {
		Double price = PRICES.get(stack.getItem());
		if (price != null) {
			return price;
		}
		if (stack.is(ItemTags.LOGS)) {
			return 1.0 / 16;
		}
		if (stack.is(ItemTags.PLANKS) || stack.is(ItemTags.SAPLINGS)) {
			return 1.0 / 64;
		}
		return DEFAULT_PRICE;
	}

	/** Whole emeralds paid for all these goods together. */
	public static int value(List<ItemStack> goods) {
		double total = 0;
		for (ItemStack stack : goods) {
			total += priceOf(stack) * stack.getCount();
		}
		return (int) Math.floor(total + 1.0E-9);
	}
}
