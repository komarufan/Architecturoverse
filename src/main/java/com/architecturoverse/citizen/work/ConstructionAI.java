package com.architecturoverse.citizen.work;

import com.architecturoverse.block.WarehouseBlockEntity;
import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.kingdom.BuildOrder;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Construction;
import com.architecturoverse.structure.ConstructionPlan;
import com.architecturoverse.structure.Material;
import com.architecturoverse.village.Constructions;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Works on the village building site: takes a block from the plan, brings the wood or stone
 * it needs (from the warehouse, or by felling trees and digging in the mine when the warehouse
 * is empty), clears the spot and puts the block in place.
 */
public class ConstructionAI extends WorkerAI {
	private static final int CARRY = 16;
	private static final int GATHER_TARGET = 16;
	private static final int PLACE_TICKS = 6;

	private final LumberjackAI woodcutter;
	private final MinerAI quarry;

	private int claimed = -1;
	private @Nullable Construction claimedSite;
	private @Nullable Material gathering;
	private @Nullable Material fetching;
	private int placeCooldown;
	private int obtainCooldown;

	public ConstructionAI(CitizenEntity citizen) {
		super(citizen);
		this.woodcutter = new LumberjackAI(citizen);
		this.quarry = new MinerAI(citizen);
	}

	@Override
	public boolean hasWork() {
		return Constructions.siteOf(citizen) != null;
	}

	@Override
	public void tick() {
		Construction site = Constructions.siteOf(citizen);
		if (site == null) {
			return;
		}
		ServerLevel level = level();
		if (fetching != null) {
			fetch(fetching);
			return;
		}
		if (gathering != null) {
			gather(gathering);
			return;
		}
		if (claimed < 0 || !sameSite(site, claimedSite)) {
			claimed = Constructions.claimNext(citizen, level, s -> s.cost() == Material.NONE || count(s.cost()) > 0);
			claimedSite = site;
			if (claimed < 0) {
				Material needed = Constructions.neededNext(citizen, level);
				if (needed != Material.NONE && count(needed) == 0) {
					obtain(site, needed);
				}
				return;
			}
		}
		ConstructionPlan.Step step = ConstructionPlan.of(site).get(claimed);
		if (step.isDone(level)) {
			releaseClaim();
			return;
		}
		Material cost = step.cost();
		if (cost != Material.NONE && count(cost) == 0) {
			releaseClaim();
			obtain(site, cost);
			return;
		}
		if (!walkTo(step.pos(), 5.0)) {
			return;
		}
		work(level, step, cost);
	}

	private void work(ServerLevel level, ConstructionPlan.Step step, Material cost) {
		BlockPos pos = step.pos();
		BlockState current = level.getBlockState(pos);
		if (!current.getFluidState().isEmpty() && !current.blocksMotion()) {
			level.setBlockAndUpdate(pos, step.foundation() ? step.state() : Blocks.AIR.defaultBlockState());
			return;
		}
		if (step.state().isAir()) {
			if (dig(pos)) {
				releaseClaim();
			}
			return;
		}
		if (!step.foundation() && !current.isAir() && !current.canBeReplaced()) {
			dig(pos); // something else stands where this block goes
			return;
		}
		if (--placeCooldown > 0) {
			return;
		}
		placeCooldown = PLACE_TICKS;
		if (!step.state().getCollisionShape(level, pos).isEmpty() && somebodyInTheWay(level, pos)) {
			stepAside();
			releaseClaim();
			return;
		}
		if (cost != Material.NONE && !consume(cost)) {
			return;
		}
		BlockState state = Block.updateFromNeighbourShapes(step.state(), level, pos);
		level.setBlockAndUpdate(pos, state);
		if (step.extraPos() != null && step.extraState() != null) {
			level.setBlockAndUpdate(step.extraPos(), step.extraState());
		}
		level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
		citizen.swing(InteractionHand.MAIN_HAND);
		citizen.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		releaseClaim();
	}

	/**
	 * Decides how to get a material: from the warehouse if it has some; otherwise wait if fellow
	 * builders already carry enough for the rest of the site, and only else go and gather it.
	 */
	private void obtain(Construction site, Material material) {
		if (--obtainCooldown > 0) {
			return;
		}
		obtainCooldown = 40;
		if (warehouseCount(material) > 0) {
			fetching = material;
		} else if (carriedInVillage(material) < Constructions.remaining(level(), site).getOrDefault(material, 0)) {
			gathering = material;
		}
	}

	/** How much of the material the village's citizens around here have in their pockets. */
	private int carriedInVillage(Material material) {
		int total = 0;
		for (CitizenEntity other : level().getEntitiesOfClass(CitizenEntity.class, citizen.getBoundingBox().inflate(64.0),
			c -> c.getVillageId() == citizen.getVillageId() && c.getRuler() != null && c.getRuler().equals(citizen.getRuler()))) {
			for (ItemStack stack : other.getInventory().getItems()) {
				if (material.accepts(stack)) {
					total += stack.getCount();
				}
			}
		}
		return total;
	}

	/** Brings materials from the warehouse. */
	private void fetch(Material material) {
		Optional<BlockPos> warehousePos = citizen.getWarehousePos();
		if (warehousePos.isEmpty()) {
			fetching = null;
			gathering = material;
			return;
		}
		if (!walkTo(warehousePos.get(), 2.5)) {
			return;
		}
		fetching = null;
		if (level().getBlockEntity(warehousePos.get()) instanceof WarehouseBlockEntity warehouse) {
			warehouse.take(material::accepts, CARRY).forEach(this::keep);
		}
		if (count(material) == 0) {
			gathering = material;
		}
	}

	/** Nothing in the warehouse: get the material yourself. */
	private void gather(Material material) {
		if (count(material) >= GATHER_TARGET || inventoryFull()) {
			gathering = null;
			return;
		}
		if (warehouseCount(material) > 0) {
			gathering = null;
			fetching = material;
			return;
		}
		WorkerAI gatherer = switch (material) {
			case WOOD -> woodcutter;
			case STONE -> quarry;
			case NONE -> null;
		};
		if (gatherer == null) {
			gathering = null;
			return;
		}
		if (material == Material.STONE && citizen.getVillage().flatMap(ClaimedVillage::mine).isEmpty()) {
			requestMine();
		}
		if (gatherer.hasWork()) {
			gatherer.tick();
		} else if (count(material) > 0) {
			gathering = null; // build with what we have
		} else {
			citizen.notifyRuler(material == Material.WOOD ? "message.architecturoverse.need_wood" : "message.architecturoverse.need_stone");
		}
	}

	/** Stone comes from the mine; if the village has none, the builders get an order to found one. */
	private void requestMine() {
		citizen.getKingdomManager().ifPresent(manager -> citizen.getKingdom().ifPresent(kingdom ->
			manager.updateVillage(kingdom, citizen.getVillageId(), v ->
				v.mine().isPresent() || v.orders().contains(BuildOrder.MINE) ? v : v.withOrder(BuildOrder.MINE))));
	}

	private int warehouseCount(Material material) {
		Optional<BlockPos> warehousePos = citizen.getWarehousePos();
		if (warehousePos.isPresent() && level().isLoaded(warehousePos.get())
			&& level().getBlockEntity(warehousePos.get()) instanceof WarehouseBlockEntity warehouse) {
			return warehouse.count(material::accepts);
		}
		return 0;
	}

	private int count(Material material) {
		SimpleContainer inventory = citizen.getInventory();
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (material.accepts(inventory.getItem(i))) {
				count += inventory.getItem(i).getCount();
			}
		}
		return count;
	}

	private boolean consume(Material material) {
		SimpleContainer inventory = citizen.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (material.accepts(stack)) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}

	private static boolean somebodyInTheWay(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty();
	}

	private void stepAside() {
		Vec3 spot = DefaultRandomPos.getPos(citizen, 5, 2);
		if (spot != null) {
			citizen.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.6);
		}
	}

	private void releaseClaim() {
		if (claimed >= 0 && claimedSite != null) {
			Constructions.release(claimedSite, claimed);
		}
		claimed = -1;
	}

	private static boolean sameSite(Construction a, @Nullable Construction b) {
		return b != null && a.type() == b.type() && a.origin().equals(b.origin());
	}

	/** Building materials stay in the pockets when visiting the warehouse. */
	@Override
	public int keepCount(ItemStack stack) {
		return Material.WOOD.accepts(stack) || Material.STONE.accepts(stack) ? Integer.MAX_VALUE : 0;
	}

	@Override
	public void stop() {
		super.stop();
		woodcutter.stop();
		quarry.stop();
		releaseClaim();
	}
}
