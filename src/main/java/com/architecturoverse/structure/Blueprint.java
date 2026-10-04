package com.architecturoverse.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A building described as text layers, bottom to top. In each layer the first row is the
 * front (north side, where the entrance is) and each character is looked up in the palette.
 * A space means "leave the world as it is"; anchors mark points the AI needs to find later.
 */
public final class Blueprint {
	/** A block of the blueprint. {@code extra} is the second half of two-block things like beds. */
	public record Entry(BlockPos pos, BlockState state, Material material, @Nullable BlockPos extraPos, @Nullable BlockState extraState) {
	}

	/** What a palette character stands for. */
	public record Key(BlockState state, Material material, @Nullable Character anchor) {
		public static Key block(BlockState state, Material material) {
			return new Key(state, material, null);
		}

		public static Key anchor(BlockState state, Material material, char anchor) {
			return new Key(state, material, anchor);
		}
	}

	private final int width;
	private final int depth;
	private final int height;
	private final List<Entry> entries;
	private final Map<Character, BlockPos> anchors;

	private Blueprint(int width, int depth, int height, List<Entry> entries, Map<Character, BlockPos> anchors) {
		this.width = width;
		this.depth = depth;
		this.height = height;
		this.entries = List.copyOf(entries);
		this.anchors = Map.copyOf(anchors);
	}

	/**
	 * Parses layers of rows. {@code pairs} maps the first half of a two-block thing (e.g. a
	 * bed head) to the character of its other half, which then is not placed on its own.
	 */
	public static Blueprint parse(String[][] layers, Map<Character, Key> palette, Map<Character, Character> pairs) {
		int height = layers.length;
		int depth = layers[0].length;
		int width = layers[0][0].length();
		Map<BlockPos, Character> chars = new HashMap<>();
		for (int y = 0; y < height; y++) {
			if (layers[y].length != depth) {
				throw new IllegalArgumentException("Layer " + y + " has " + layers[y].length + " rows, expected " + depth);
			}
			for (int z = 0; z < depth; z++) {
				String row = layers[y][z];
				if (row.length() != width) {
					throw new IllegalArgumentException("Row " + z + " of layer " + y + " is " + row.length() + " wide, expected " + width);
				}
				for (int x = 0; x < width; x++) {
					chars.put(new BlockPos(x, y, z), row.charAt(x));
				}
			}
		}

		List<Entry> entries = new ArrayList<>();
		Map<Character, BlockPos> anchors = new HashMap<>();
		for (int y = 0; y < height; y++) {
			for (int z = 0; z < depth; z++) {
				for (int x = 0; x < width; x++) {
					BlockPos pos = new BlockPos(x, y, z);
					char c = chars.get(pos);
					if (c == ' ' || pairs.containsValue(c)) {
						continue;
					}
					Key key = palette.get(c);
					if (key == null) {
						throw new IllegalArgumentException("Unknown blueprint character '" + c + "' at " + pos);
					}
					if (key.anchor() != null) {
						anchors.putIfAbsent(key.anchor(), pos);
					}
					BlockPos extraPos = null;
					BlockState extraState = null;
					Character other = pairs.get(c);
					if (other != null) {
						for (BlockPos near : List.of(pos.north(), pos.south(), pos.east(), pos.west())) {
							if (chars.get(near) != null && chars.get(near) == other) {
								extraPos = near;
								extraState = palette.get(other).state();
							}
						}
					}
					entries.add(new Entry(pos, key.state(), key.material(), extraPos, extraState));
				}
			}
		}
		return new Blueprint(width, depth, height, entries, anchors);
	}

	public int width() {
		return width;
	}

	public int depth() {
		return depth;
	}

	public int height() {
		return height;
	}

	/** Entries in building order: bottom layer first, front row first. */
	public List<Entry> entries() {
		return entries;
	}

	/** World position of a blueprint position for a building placed at {@code origin} with {@code rotation}. */
	public static BlockPos toWorld(BlockPos relative, BlockPos origin, Rotation rotation) {
		return origin.offset(relative.rotate(rotation));
	}

	public BlockPos anchor(char anchor, BlockPos origin, Rotation rotation) {
		BlockPos relative = anchors.get(anchor);
		if (relative == null) {
			throw new IllegalArgumentException("Blueprint has no anchor '" + anchor + "'");
		}
		return toWorld(relative, origin, rotation);
	}

	/** World positions of every column of the footprint. */
	public List<BlockPos> footprint(BlockPos origin, Rotation rotation) {
		List<BlockPos> columns = new ArrayList<>();
		for (int z = 0; z < depth; z++) {
			for (int x = 0; x < width; x++) {
				columns.add(toWorld(new BlockPos(x, 0, z), origin, rotation));
			}
		}
		return columns;
	}
}
