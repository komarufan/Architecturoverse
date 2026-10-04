package com.architecturoverse.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** A mine entrance placed by the ruler; miners dig the plan from {@code entrance} in {@code facing} direction. */
public record MineSite(BlockPos entrance, Direction facing, int progress) {
	public static final Codec<MineSite> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.fieldOf("entrance").forGetter(MineSite::entrance),
		Direction.CODEC.fieldOf("facing").forGetter(MineSite::facing),
		Codec.INT.fieldOf("progress").forGetter(MineSite::progress)
	).apply(i, MineSite::new));

	public MineSite withProgress(int newProgress) {
		return new MineSite(entrance, facing, newProgress);
	}
}
