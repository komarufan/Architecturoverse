package com.architecturoverse.registry;

import com.architecturoverse.Architecturoverse;
import com.architecturoverse.block.MineEntranceBlock;
import com.architecturoverse.block.WarehouseBlock;
import com.architecturoverse.block.WarehouseBlockEntity;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final Block WAREHOUSE = register("warehouse", WarehouseBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
	public static final Block MINE_ENTRANCE = register("mine_entrance", MineEntranceBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE));

	public static final BlockEntityType<WarehouseBlockEntity> WAREHOUSE_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE, Architecturoverse.id("warehouse"),
		new BlockEntityType<>(WarehouseBlockEntity::new, Set.of(WAREHOUSE)));

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Architecturoverse.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Architecturoverse.id(name));
		BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		return block;
	}

	public static void init() {
		// Loads the class so the static registrations above run.
	}
}
