package com.architecturoverse.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Ring a village bell with it to seize power. The capture itself lives in {@link com.architecturoverse.village.CaptureHandler}. */
public class ScepterItem extends Item {
	public ScepterItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.architecturoverse.scepter.tooltip1").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.architecturoverse.scepter.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
