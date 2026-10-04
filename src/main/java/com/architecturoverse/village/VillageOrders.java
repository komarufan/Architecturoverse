package com.architecturoverse.village;

import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.work.MinePlan;
import com.architecturoverse.kingdom.BuildOrder;
import com.architecturoverse.kingdom.CitizenRecord;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MineSite;
import com.architecturoverse.network.KingdomNetworking;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** Build orders the ruler gives from the kingdom screen. */
public final class VillageOrders {
	private VillageOrders() {
	}

	/**
	 * Queues the order for the village's builders. If the village has no builder, the first
	 * unemployed citizen becomes one. Returns false (and tells the ruler why) if the order makes no sense.
	 */
	public static boolean order(ServerPlayer ruler, int villageId, BuildOrder order) {
		KingdomManager manager = KingdomManager.get(ruler.level().getServer());
		Optional<Kingdom> kingdom = manager.kingdom(ruler.getUUID());
		Optional<ClaimedVillage> village = kingdom.flatMap(k -> k.village(villageId));
		if (kingdom.isEmpty() || village.isEmpty()) {
			return false;
		}
		Component refusal = refusal(ruler, village.get(), order);
		if (refusal != null) {
			ruler.sendOverlayMessage(refusal.copy().withStyle(ChatFormatting.YELLOW));
			return false;
		}
		manager.updateVillage(kingdom.get(), villageId, v -> v.withOrder(order));
		ruler.sendSystemMessage(Component.translatable("message.architecturoverse.order_accepted", order.displayName(), villageId)
			.withStyle(ChatFormatting.GREEN));
		ensureBuilder(ruler, kingdom.get(), villageId);
		return true;
	}

	private static @Nullable Component refusal(ServerPlayer ruler, ClaimedVillage village, BuildOrder order) {
		if (village.orders().contains(order)) {
			return Component.translatable("message.architecturoverse.order_queued", order.displayName());
		}
		return switch (order) {
			case WAREHOUSE -> village.warehouse().isPresent()
				? Component.translatable("message.architecturoverse.has_warehouse") : null;
			case MINE -> village.mine().isPresent() && !exhausted(ruler, village, village.mine().get())
				? Component.translatable("message.architecturoverse.has_mine") : null;
		};
	}

	private static boolean exhausted(ServerPlayer ruler, ClaimedVillage village, MineSite mine) {
		ServerLevel level = ruler.level().getServer().getLevel(village.dimension());
		return level != null && mine.progress() >= MinePlan.of(mine, level.getMinY()).size();
	}

	private static void ensureBuilder(ServerPlayer ruler, Kingdom kingdom, int villageId) {
		boolean hasBuilder = kingdom.citizens().stream().anyMatch(c -> c.villageId() == villageId && c.job() == CitizenJob.BUILDER);
		if (hasBuilder) {
			return;
		}
		Optional<CitizenRecord> idle = kingdom.citizens().stream()
			.filter(c -> c.villageId() == villageId && c.job() == CitizenJob.UNEMPLOYED)
			.findFirst();
		if (idle.isPresent()) {
			KingdomNetworking.commandCitizen(ruler, idle.get().uuid(), CitizenJob.BUILDER, null);
			ruler.sendSystemMessage(Component.translatable("message.architecturoverse.builder_assigned", idle.get().name()));
		} else {
			ruler.sendSystemMessage(Component.translatable("message.architecturoverse.need_builder", villageId).withStyle(ChatFormatting.YELLOW));
		}
	}
}
