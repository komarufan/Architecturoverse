package com.architecturoverse.trade;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.work.Walker;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.registry.ModEntities;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/** Calling the travelling merchant to a village, and setting the village soldiers on him. */
public final class Traders {
	private static final int[] ARRIVAL_DISTANCES = {64, 48};
	private static final double SEARCH_RADIUS = 160.0;

	private Traders() {
	}

	/** The merchant currently visiting this village, if he is around. */
	public static Optional<TraderEntity> find(ServerLevel level, UUID ruler, ClaimedVillage village) {
		return level.getEntitiesOfClass(TraderEntity.class, new AABB(village.center()).inflate(SEARCH_RADIUS),
				t -> t.isAlive() && ruler.equals(t.getRuler()) && t.getVillageId() == village.id())
			.stream().findFirst();
	}

	/** Spawns a merchant at the edge of the village who walks to the warehouse. */
	public static boolean call(ServerPlayer ruler, int villageId) {
		Optional<ClaimedVillage> village = KingdomManager.get(ruler.level().getServer()).kingdom(ruler.getUUID()).flatMap(k -> k.village(villageId));
		ServerLevel level = village.map(v -> ruler.level().getServer().getLevel(v.dimension())).orElse(null);
		if (village.isEmpty() || level == null) {
			return false;
		}
		if (village.get().warehouse().isEmpty()) {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.trader_needs_warehouse").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (find(level, ruler.getUUID(), village.get()).isPresent()) {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.trader_on_way").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Optional<BlockPos> spot = arrivalSpot(level, village.get().center());
		if (spot.isEmpty()) {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.trader_no_road").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		spawn(level, ruler.getUUID(), village.get(), spot.get());
		ruler.sendSystemMessage(Component.translatable("message.architecturoverse.trader_called", villageId).withStyle(ChatFormatting.GREEN));
		return true;
	}

	public static TraderEntity spawn(ServerLevel level, UUID ruler, ClaimedVillage village, BlockPos pos) {
		TraderEntity trader = ModEntities.TRADER.create(level, EntitySpawnReason.EVENT);
		if (trader == null) {
			throw new IllegalStateException("Could not create trader");
		}
		trader.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
		trader.hire(ruler, village.id(), 10 + level.getRandom().nextInt(16));
		level.addFreshEntity(trader);
		return trader;
	}

	/** Orders the village soldiers to attack the visiting merchant. */
	public static boolean rob(ServerPlayer ruler, int villageId) {
		Optional<ClaimedVillage> village = KingdomManager.get(ruler.level().getServer()).kingdom(ruler.getUUID()).flatMap(k -> k.village(villageId));
		ServerLevel level = village.map(v -> ruler.level().getServer().getLevel(v.dimension())).orElse(null);
		if (village.isEmpty() || level == null) {
			return false;
		}
		Optional<TraderEntity> trader = find(level, ruler.getUUID(), village.get());
		if (trader.isEmpty()) {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.no_trader").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		List<CitizenEntity> soldiers = level.getEntitiesOfClass(CitizenEntity.class, new AABB(village.get().center()).inflate(SEARCH_RADIUS),
			c -> c.isAlive() && c.isSoldier() && c.getVillageId() == villageId && ruler.getUUID().equals(c.getRuler()));
		if (soldiers.isEmpty()) {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.no_soldiers_here").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		soldiers.forEach(soldier -> soldier.setTarget(trader.get()));
		ruler.sendSystemMessage(Component.translatable("message.architecturoverse.rob_ordered", soldiers.size()).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** A dry spot on the ground well out of the village (closer only if the far ground is not loaded). */
	private static Optional<BlockPos> arrivalSpot(ServerLevel level, BlockPos center) {
		double start = level.getRandom().nextDouble() * Math.PI * 2;
		for (int distance : ARRIVAL_DISTANCES) {
			for (int i = 0; i < 12; i++) {
				double angle = start + Math.PI * 2 * i / 12;
				Optional<BlockPos> ground = Walker.dryGround(level, center.getX() + (int) (Math.cos(angle) * distance),
					center.getZ() + (int) (Math.sin(angle) * distance));
				if (ground.isPresent()) {
					return ground;
				}
			}
		}
		return Optional.empty();
	}
}
