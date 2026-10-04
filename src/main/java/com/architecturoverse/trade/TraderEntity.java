package com.architecturoverse.trade;

import com.architecturoverse.block.WarehouseBlockEntity;
import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.work.Walker;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.village.Retaliation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A travelling merchant called by the ruler. Walks to the village warehouse, buys every item
 * there except emeralds, pays in emeralds and walks off again. Killing him on the way pays off
 * (his purse and his cargo drop) but his patrons from the neighbouring village will take revenge.
 */
public class TraderEntity extends PathfinderMob implements InventoryCarrier {
	private static final int MAX_LIFE_TICKS = 20 * 60 * 6;
	private static final int LEAVE_DISTANCE = 64;

	private @Nullable UUID ruler;
	private int villageId = -1;
	private boolean traded;
	private int purse;
	private int lifeTicks;
	private @Nullable BlockPos leaveTarget;
	private final SimpleContainer cargo = new SimpleContainer(54);
	private final Walker walker = new Walker(this);

	public TraderEntity(EntityType<? extends TraderEntity> type, Level level) {
		super(type, level);
		this.getNavigation().setCanOpenDoors(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 30.0)
			.add(Attributes.MOVEMENT_SPEED, 0.5)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	/** Called once right after spawning. */
	public void hire(UUID ruler, int villageId, int purse) {
		this.ruler = ruler;
		this.villageId = villageId;
		this.purse = purse;
		this.setCustomName(Component.translatable("entity.architecturoverse.trader"));
	}

	public @Nullable UUID getRuler() {
		return ruler;
	}

	public int getVillageId() {
		return villageId;
	}

	public boolean hasTraded() {
		return traded;
	}

	@Override
	public SimpleContainer getInventory() {
		return cargo;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 0.8));
		this.goalSelector.addGoal(2, new TravelGoal());
		this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
	}

	private Optional<ClaimedVillage> village() {
		if (ruler == null || !(level() instanceof ServerLevel serverLevel)) {
			return Optional.empty();
		}
		return KingdomManager.get(serverLevel.getServer()).kingdom(ruler).flatMap(k -> k.village(villageId));
	}

	private @Nullable ServerPlayer rulerPlayer() {
		return ruler != null && level() instanceof ServerLevel serverLevel ? serverLevel.getServer().getPlayerList().getPlayer(ruler) : null;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (++lifeTicks > MAX_LIFE_TICKS) {
			discard();
		}
	}

	/** Takes all goods from the warehouse and pays for them with emeralds. */
	void trade(ServerLevel level, WarehouseBlockEntity warehouse) {
		traded = true;
		List<ItemStack> bought = new ArrayList<>();
		for (ItemStack stack : warehouse.take(s -> !s.is(Items.EMERALD), Integer.MAX_VALUE)) {
			bought.add(stack.copy());
			cargo.addItem(stack);
		}
		int price = TradePrices.value(bought);
		int left = price;
		while (left > 0) {
			ItemStack emeralds = new ItemStack(Items.EMERALD, Math.min(64, left));
			left -= emeralds.getCount();
			ItemStack rest = warehouse.insert(emeralds);
			if (!rest.isEmpty()) {
				Block.popResource(level, warehouse.getBlockPos().above(), rest);
			}
		}
		level.playSound(null, blockPosition(), SoundEvents.WANDERING_TRADER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		int items = bought.stream().mapToInt(ItemStack::getCount).sum();
		ServerPlayer player = rulerPlayer();
		if (player != null) {
			player.sendSystemMessage(items == 0
				? Component.translatable("message.architecturoverse.trader_nothing", villageId).withStyle(ChatFormatting.YELLOW)
				: Component.translatable("message.architecturoverse.trader_paid", items, price, villageId).withStyle(ChatFormatting.GREEN));
		}
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel serverLevel && ruler != null && isRulersDoing(source.getEntity())) {
			ServerPlayer player = rulerPlayer();
			if (player != null) {
				player.sendSystemMessage(Component.translatable("message.architecturoverse.trader_robbed", purse).withStyle(ChatFormatting.GOLD));
			}
			Retaliation.schedule(serverLevel, ruler, villageId);
		}
		super.die(source);
	}

	/** Killed by the ruler or by one of the ruler's citizens. */
	private boolean isRulersDoing(@Nullable Entity killer) {
		if (killer instanceof Player player) {
			return player.getUUID().equals(ruler);
		}
		return killer instanceof CitizenEntity citizen && ruler != null && ruler.equals(citizen.getRuler());
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		int left = purse;
		while (left > 0) {
			int count = Math.min(64, left);
			spawnAtLocation(level, new ItemStack(Items.EMERALD, count));
			left -= count;
		}
		for (ItemStack stack : cargo.removeAllItems()) {
			spawnAtLocation(level, stack);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (ruler != null) {
			output.store("Ruler", UUIDUtil.CODEC, ruler);
		}
		output.putInt("Village", villageId);
		output.putBoolean("Traded", traded);
		output.putInt("Purse", purse);
		output.putInt("Life", lifeTicks);
		writeInventoryToTag(output);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		ruler = input.read("Ruler", UUIDUtil.CODEC).orElse(null);
		villageId = input.getIntOr("Village", -1);
		traded = input.getBooleanOr("Traded", false);
		purse = input.getIntOr("Purse", 0);
		lifeTicks = input.getIntOr("Life", 0);
		readInventoryFromTag(input);
	}

	/** To the warehouse, trade, then away from the village and gone. */
	private class TravelGoal extends Goal {
		TravelGoal() {
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Optional<ClaimedVillage> village = village();
			if (!traded) {
				Optional<BlockPos> warehousePos = village.flatMap(ClaimedVillage::warehouse);
				if (warehousePos.isEmpty()) {
					traded = true; // nothing to trade with: just leave
					return;
				}
				if (walker.walkTo(warehousePos.get(), 2.5)
					&& level() instanceof ServerLevel serverLevel
					&& serverLevel.getBlockEntity(warehousePos.get()) instanceof WarehouseBlockEntity warehouse) {
					trade(serverLevel, warehouse);
				}
				return;
			}
			BlockPos center = village.map(ClaimedVillage::center).orElse(blockPosition());
			if (leaveTarget == null) {
				double angle = Math.atan2(getZ() - center.getZ(), getX() - center.getX());
				int x = center.getX() + (int) (Math.cos(angle) * LEAVE_DISTANCE);
				int z = center.getZ() + (int) (Math.sin(angle) * LEAVE_DISTANCE);
				int y = level().isLoaded(new BlockPos(x, 0, z)) ? level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : getBlockY();
				leaveTarget = new BlockPos(x, y, z);
			}
			double dx = getX() - center.getX();
			double dz = getZ() - center.getZ();
			if (dx * dx + dz * dz > (LEAVE_DISTANCE - 8) * (LEAVE_DISTANCE - 8) || walker.walkTo(leaveTarget, 4.0)) {
				discard(); // out of sight of the village
			}
		}
	}
}
