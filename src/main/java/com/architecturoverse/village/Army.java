package com.architecturoverse.village;

import com.architecturoverse.kingdom.ArmyOrder;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Orders for the soldiers of one village or of the whole kingdom. */
public final class Army {
	public static final int ALL_VILLAGES = -1;

	private Army() {
	}

	/**
	 * Gives the order to the soldiers of the village (or of every village). A rally order
	 * gathers them where the ruler stands right now.
	 */
	public static boolean order(ServerPlayer ruler, int villageId, ArmyOrder order) {
		KingdomManager manager = KingdomManager.get(ruler.level().getServer());
		Optional<Kingdom> kingdom = manager.kingdom(ruler.getUUID());
		if (kingdom.isEmpty()) {
			return false;
		}
		List<ClaimedVillage> villages = kingdom.get().villages().stream()
			.filter(v -> villageId == ALL_VILLAGES || v.id() == villageId)
			.toList();
		if (order == ArmyOrder.BASE && villages.stream().noneMatch(v -> v.militaryBase().isPresent())) {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.no_military_base").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Optional<BlockPos> rally = order == ArmyOrder.RALLY ? Optional.of(ruler.blockPosition()) : Optional.empty();
		for (ClaimedVillage village : villages) {
			if (order == ArmyOrder.BASE && village.militaryBase().isEmpty()) {
				continue;
			}
			manager.updateVillage(kingdom.get(), village.id(), v -> v.withArmyOrder(order, rally));
		}
		ruler.sendSystemMessage(Component.translatable("message.architecturoverse.army_order", order.displayName()).withStyle(ChatFormatting.GREEN));
		return true;
	}
}
