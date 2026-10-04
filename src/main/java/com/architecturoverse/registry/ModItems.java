package com.architecturoverse.registry;

import com.architecturoverse.Architecturoverse;
import com.architecturoverse.item.RulerBookItem;
import com.architecturoverse.item.ScepterItem;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item SCEPTER = register("scepter", ScepterItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item RULER_BOOK = register("ruler_book", RulerBookItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Architecturoverse.id("main"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.architecturoverse"))
			.icon(() -> new ItemStack(SCEPTER))
			.displayItems((parameters, output) -> {
				output.accept(SCEPTER);
				output.accept(RULER_BOOK);
			})
			.build());

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Architecturoverse.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		// Loads the class so the static registrations above run.
	}
}
