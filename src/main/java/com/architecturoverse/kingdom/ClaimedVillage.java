package com.architecturoverse.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A village that belongs to a kingdom. The center is the bell the ruler rang with the scepter.
 * {@code orders} is the builders' to-do list (oldest first), {@code construction} the building
 * site the whole village is working on, and {@code armyOrder}/{@code rallyPoint} tell the
 * village soldiers where to be.
 */
public record ClaimedVillage(
	int id,
	ResourceKey<Level> dimension,
	BlockPos center,
	Optional<BlockPos> warehouse,
	Optional<MineSite> mine,
	List<BuildOrder> orders,
	Optional<Construction> construction,
	Optional<MilitaryBase> militaryBase,
	ArmyOrder armyOrder,
	Optional<BlockPos> rallyPoint
) {
	/** Villagers, golems and citizens within this horizontal distance of the bell belong to the village. */
	public static final int RADIUS = 48;
	/** Mines may be placed this far from the village center, since good stone is rarely inside the village. */
	public static final int MINE_RANGE = 96;

	public static final Codec<ClaimedVillage> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("id").forGetter(ClaimedVillage::id),
		Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ClaimedVillage::dimension),
		BlockPos.CODEC.fieldOf("center").forGetter(ClaimedVillage::center),
		BlockPos.CODEC.optionalFieldOf("warehouse").forGetter(ClaimedVillage::warehouse),
		MineSite.CODEC.optionalFieldOf("mine").forGetter(ClaimedVillage::mine),
		BuildOrder.CODEC.listOf().optionalFieldOf("orders", List.of()).forGetter(ClaimedVillage::orders),
		Construction.CODEC.optionalFieldOf("construction").forGetter(ClaimedVillage::construction),
		MilitaryBase.CODEC.optionalFieldOf("military_base").forGetter(ClaimedVillage::militaryBase),
		ArmyOrder.CODEC.optionalFieldOf("army_order", ArmyOrder.PATROL).forGetter(ClaimedVillage::armyOrder),
		BlockPos.CODEC.optionalFieldOf("rally_point").forGetter(ClaimedVillage::rallyPoint)
	).apply(i, ClaimedVillage::new));

	public ClaimedVillage {
		orders = List.copyOf(orders);
	}

	public ClaimedVillage(int id, ResourceKey<Level> dimension, BlockPos center) {
		this(id, dimension, center, Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
			ArmyOrder.PATROL, Optional.empty());
	}

	/** True if the position is in the same dimension and within {@code radius} blocks horizontally. */
	public boolean contains(ResourceKey<Level> dim, BlockPos pos, int radius) {
		return dimension.equals(dim)
			&& Math.abs(center.getX() - pos.getX()) <= radius
			&& Math.abs(center.getZ() - pos.getZ()) <= radius;
	}

	public ClaimedVillage withWarehouse(Optional<BlockPos> newWarehouse) {
		return new ClaimedVillage(id, dimension, center, newWarehouse, mine, orders, construction, militaryBase, armyOrder, rallyPoint);
	}

	public ClaimedVillage withMine(Optional<MineSite> newMine) {
		return new ClaimedVillage(id, dimension, center, warehouse, newMine, orders, construction, militaryBase, armyOrder, rallyPoint);
	}

	public ClaimedVillage withOrder(BuildOrder order) {
		List<BuildOrder> newOrders = new ArrayList<>(orders);
		newOrders.add(order);
		return new ClaimedVillage(id, dimension, center, warehouse, mine, newOrders, construction, militaryBase, armyOrder, rallyPoint);
	}

	public ClaimedVillage withoutOrder(BuildOrder order) {
		List<BuildOrder> newOrders = new ArrayList<>(orders);
		newOrders.remove(order);
		return new ClaimedVillage(id, dimension, center, warehouse, mine, newOrders, construction, militaryBase, armyOrder, rallyPoint);
	}

	public ClaimedVillage withConstruction(Optional<Construction> newConstruction) {
		return new ClaimedVillage(id, dimension, center, warehouse, mine, orders, newConstruction, militaryBase, armyOrder, rallyPoint);
	}

	public ClaimedVillage withMilitaryBase(Optional<MilitaryBase> newBase) {
		return new ClaimedVillage(id, dimension, center, warehouse, mine, orders, construction, newBase, armyOrder, rallyPoint);
	}

	public ClaimedVillage withArmyOrder(ArmyOrder newOrder, Optional<BlockPos> newRallyPoint) {
		return new ClaimedVillage(id, dimension, center, warehouse, mine, orders, construction, militaryBase, newOrder, newRallyPoint);
	}
}
