package com.architecturoverse.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/**
 * What a block of a structure costs. Kept deliberately coarse so the village only has
 * to gather two things: wood (logs or planks) and stone (cobblestone and the like).
 */
public enum Material {
	NONE,
	WOOD,
	STONE;

	/** Whether this item can be spent on a block of this material. */
	public boolean accepts(ItemStack stack) {
		return switch (this) {
			case NONE -> false;
			case WOOD -> stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS);
			case STONE -> stack.is(ItemTags.STONE_CRAFTING_MATERIALS);
		};
	}

	public Component displayName() {
		return Component.translatable("material.architecturoverse." + name().toLowerCase());
	}
}
