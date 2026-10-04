package com.architecturoverse.kingdom;

import com.architecturoverse.structure.StructureType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

/**
 * A building site in progress. Blocks before {@code pointer} in the construction plan are
 * known to be finished, so workers do not rescan them.
 */
public record Construction(StructureType type, BlockPos origin, Rotation rotation, int pointer) {
	public static final Codec<Construction> CODEC = RecordCodecBuilder.create(i -> i.group(
		StructureType.CODEC.fieldOf("type").forGetter(Construction::type),
		BlockPos.CODEC.fieldOf("origin").forGetter(Construction::origin),
		Rotation.CODEC.fieldOf("rotation").forGetter(Construction::rotation),
		Codec.INT.fieldOf("pointer").forGetter(Construction::pointer)
	).apply(i, Construction::new));

	public Construction withPointer(int newPointer) {
		return new Construction(type, origin, rotation, newPointer);
	}
}
