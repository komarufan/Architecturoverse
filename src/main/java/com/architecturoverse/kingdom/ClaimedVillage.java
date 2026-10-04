package com.architecturoverse.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** A village that belongs to a kingdom. The center is the bell the ruler rang with the scepter. */
public record ClaimedVillage(int id, ResourceKey<Level> dimension, BlockPos center) {
	/** Villagers, golems and citizens within this horizontal distance of the bell belong to the village. */
	public static final int RADIUS = 48;

	public static final Codec<ClaimedVillage> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("id").forGetter(ClaimedVillage::id),
		Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ClaimedVillage::dimension),
		BlockPos.CODEC.fieldOf("center").forGetter(ClaimedVillage::center)
	).apply(i, ClaimedVillage::new));

	/** True if the position is in the same dimension and within {@code radius} blocks horizontally. */
	public boolean contains(ResourceKey<Level> dim, BlockPos pos, int radius) {
		return dimension.equals(dim)
			&& Math.abs(center.getX() - pos.getX()) <= radius
			&& Math.abs(center.getZ() - pos.getZ()) <= radius;
	}
}
