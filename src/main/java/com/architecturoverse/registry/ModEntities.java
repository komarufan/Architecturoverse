package com.architecturoverse.registry;

import com.architecturoverse.Architecturoverse;
import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.military.EnemySoldierEntity;
import com.architecturoverse.trade.TraderEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<CitizenEntity> CITIZEN = register("citizen",
		EntityType.Builder.of(CitizenEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
	public static final EntityType<TraderEntity> TRADER = register("trader",
		EntityType.Builder.of(TraderEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
	public static final EntityType<EnemySoldierEntity> ENEMY_SOLDIER = register("enemy_soldier",
		EntityType.Builder.of(EnemySoldierEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	private ModEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Architecturoverse.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(CITIZEN, CitizenEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(TRADER, TraderEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ENEMY_SOLDIER, EnemySoldierEntity.createAttributes());
	}
}
