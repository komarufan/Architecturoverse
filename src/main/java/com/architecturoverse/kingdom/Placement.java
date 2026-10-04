package com.architecturoverse.kingdom;

import com.architecturoverse.structure.StructureType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

/** Where a finished building of the village stands; key spots come from its blueprint anchors. */
public record Placement(StructureType type, BlockPos origin, Rotation rotation) {
	public static final Codec<Placement> CODEC = RecordCodecBuilder.create(i -> i.group(
		StructureType.CODEC.fieldOf("type").forGetter(Placement::type),
		BlockPos.CODEC.fieldOf("origin").forGetter(Placement::origin),
		Rotation.CODEC.fieldOf("rotation").forGetter(Placement::rotation)
	).apply(i, Placement::new));

	public BlockPos anchor(char anchor) {
		return type.blueprint().anchor(anchor, origin, rotation);
	}

	public BlockPos door() {
		return anchor(StructureType.DOOR);
	}

	/** Prison only: inside cell {@code index} (0-3). */
	public BlockPos prisonCell(int index) {
		return anchor(StructureType.PRISON_CELLS.charAt(index));
	}

	/** Prison only: the gate of cell {@code index} (0-3). */
	public BlockPos prisonGate(int index) {
		return anchor(StructureType.PRISON_GATES.charAt(index));
	}

	public static int prisonCells() {
		return StructureType.PRISON_CELLS.length();
	}
}
