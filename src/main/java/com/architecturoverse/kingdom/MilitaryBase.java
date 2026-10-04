package com.architecturoverse.kingdom;

import com.architecturoverse.structure.StructureType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

/** A finished military base; key spots are looked up from the blueprint anchors. */
public record MilitaryBase(BlockPos origin, Rotation rotation) {
	public static final Codec<MilitaryBase> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.fieldOf("origin").forGetter(MilitaryBase::origin),
		Rotation.CODEC.fieldOf("rotation").forGetter(MilitaryBase::rotation)
	).apply(i, MilitaryBase::new));

	public BlockPos anchor(char anchor) {
		return StructureType.MILITARY_BASE.blueprint().anchor(anchor, origin, rotation);
	}

	public BlockPos rally() {
		return anchor(StructureType.RALLY);
	}

	public BlockPos door() {
		return anchor(StructureType.DOOR);
	}

	public BlockPos workbench() {
		return anchor(StructureType.WORKBENCH);
	}

	public BlockPos gate() {
		return anchor(StructureType.GATE);
	}

	public BlockPos cell() {
		return anchor(StructureType.CELL);
	}
}
