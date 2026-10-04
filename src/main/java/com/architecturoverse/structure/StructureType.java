package com.architecturoverse.structure;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Buildings the village can construct block by block. */
public enum StructureType {
	MILITARY_BASE(StructureType::militaryBase);

	/** Anchor: the entrance, inside the doorway. */
	public static final char DOOR = 'D';
	/** Anchor: where soldiers gather. */
	public static final char RALLY = 'R';
	/** Anchor: the workbench the executioner takes the axe from. */
	public static final char WORKBENCH = 'T';
	/** Anchor: the gate of the prison cell. */
	public static final char GATE = 'g';
	/** Anchor: inside the prison cell. */
	public static final char CELL = 'X';

	public static final Codec<StructureType> CODEC = Codec.STRING.xmap(StructureType::byName, StructureType::name);

	private final Supplier<Blueprint> blueprintFactory;
	private Blueprint blueprint;

	StructureType(Supplier<Blueprint> blueprintFactory) {
		this.blueprintFactory = blueprintFactory;
	}

	public Blueprint blueprint() {
		if (blueprint == null) {
			blueprint = blueprintFactory.get();
		}
		return blueprint;
	}

	public Component displayName() {
		return Component.translatable("structure.architecturoverse." + name().toLowerCase());
	}

	public static StructureType byName(String name) {
		for (StructureType type : values()) {
			if (type.name().equals(name)) {
				return type;
			}
		}
		return MILITARY_BASE;
	}

	public static StructureType byId(int id) {
		return values()[Mth.clamp(id, 0, values().length - 1)];
	}

	/**
	 * A stone barracks hall: beds for the soldiers, a workbench, a barred prison cell in the
	 * back corner and a flag on the roof. The doorway is in the middle of the front wall.
	 */
	private static Blueprint militaryBase() {
		String[] floor = {
			"CCCCCCCCCCC",
			"CCCCCCCCCCC",
			"CCCCCCCCCCC",
			"CCCCCCCCCCC",
			"CCCCCCCCCCC",
			"CCCCCCCCCCC",
			"CCCCCCCCCCC",
			"CCCCCCCCCCC",
			"CCCCCCCCCCC"};
		String[] ground = {
			"LSSSSDSSSSL",
			"Sn.......nS",
			"S.........S",
			"S........TS",
			"S....R....S",
			"S......IgIS",
			"Sfff...IXIS",
			"Shhh.n.IIIS",
			"LSSSSSSSSSL"};
		String[] windows = {
			"LSSSSDSSSSL",
			"S.........S",
			"G.........G",
			"S.........S",
			"G.........G",
			"S......IIIS",
			"S......I.IS",
			"S......IIIS",
			"LSSGSSSGSSL"};
		String[] top = {
			"LSSSSSSSSSL",
			"S.........S",
			"S.........S",
			"S.........S",
			"S.........S",
			"S......IIIS",
			"S......IIIS",
			"S......IIIS",
			"LSSSSSSSSSL"};
		String[] roof = {
			"PPPPPPPPPPP",
			"PPPPPPPPPPP",
			"PPPPPPPPPPP",
			"PPPPPPPPPPP",
			"PPPPPPPPPPP",
			"PPPPPPPPPPP",
			"PPPPPPPPPPP",
			"PPPPPPPPPPP",
			"PPPPPPPPPPP"};
		String[] pole = {
			"           ",
			" F         ",
			"           ",
			"           ",
			"           ",
			"           ",
			"           ",
			"           ",
			"           "};
		String[] flag = {
			"           ",
			" FW        ",
			"           ",
			"           ",
			"           ",
			"           ",
			"           ",
			"           ",
			"           "};

		var air = Blocks.AIR.defaultBlockState();
		var bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		Map<Character, Blueprint.Key> palette = Map.ofEntries(
			Map.entry('.', Blueprint.Key.block(air, Material.NONE)),
			Map.entry('C', Blueprint.Key.block(Blocks.COBBLESTONE.defaultBlockState(), Material.STONE)),
			Map.entry('S', Blueprint.Key.block(Blocks.STONE_BRICKS.defaultBlockState(), Material.STONE)),
			Map.entry('L', Blueprint.Key.block(Blocks.OAK_LOG.defaultBlockState(), Material.WOOD)),
			Map.entry('P', Blueprint.Key.block(Blocks.OAK_PLANKS.defaultBlockState(), Material.WOOD)),
			Map.entry('G', Blueprint.Key.block(Blocks.GLASS_PANE.defaultBlockState(), Material.STONE)),
			Map.entry('I', Blueprint.Key.block(Blocks.IRON_BARS.defaultBlockState(), Material.STONE)),
			Map.entry('F', Blueprint.Key.block(Blocks.OAK_FENCE.defaultBlockState(), Material.WOOD)),
			Map.entry('W', Blueprint.Key.block(Blocks.WOOL.pick(DyeColor.RED).defaultBlockState(), Material.WOOD)),
			Map.entry('n', Blueprint.Key.block(Blocks.LANTERN.defaultBlockState(), Material.WOOD)),
			Map.entry('h', Blueprint.Key.block(bed.setValue(BedBlock.PART, BedPart.HEAD), Material.WOOD)),
			Map.entry('f', Blueprint.Key.block(bed.setValue(BedBlock.PART, BedPart.FOOT), Material.WOOD)),
			Map.entry(DOOR, Blueprint.Key.anchor(air, Material.NONE, DOOR)),
			Map.entry(RALLY, Blueprint.Key.anchor(air, Material.NONE, RALLY)),
			Map.entry(CELL, Blueprint.Key.anchor(air, Material.NONE, CELL)),
			Map.entry(WORKBENCH, Blueprint.Key.anchor(Blocks.CRAFTING_TABLE.defaultBlockState(), Material.WOOD, WORKBENCH)),
			Map.entry(GATE, Blueprint.Key.anchor(Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH),
				Material.WOOD, GATE))
		);
		return Blueprint.parse(new String[][] {floor, ground, windows, top, roof, pole, flag}, palette, Map.of('h', 'f'));
	}
}
