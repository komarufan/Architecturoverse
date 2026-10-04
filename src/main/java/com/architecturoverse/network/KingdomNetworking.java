package com.architecturoverse.network;

import com.architecturoverse.block.WarehouseBlockEntity;
import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenMode;
import com.architecturoverse.kingdom.CitizenRecord;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MineSite;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class KingdomNetworking {
	private static final int STOCK_SHOWN = 6;

	private KingdomNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(KingdomSnapshotPayload.TYPE, KingdomSnapshotPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestKingdomPayload.TYPE, RequestKingdomPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CitizenCommandPayload.TYPE, CitizenCommandPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RequestKingdomPayload.TYPE,
			(payload, context) -> openKingdomScreen(context.player(), null));
		ServerPlayNetworking.registerGlobalReceiver(CitizenCommandPayload.TYPE, (payload, context) -> {
			CitizenJob job = payload.job() >= 0 ? CitizenJob.byId(payload.job()) : null;
			CitizenMode mode = payload.mode() >= 0 ? CitizenMode.byId(payload.mode()) : null;
			if (commandCitizen(context.player(), payload.citizen(), job, mode)) {
				sendSnapshot(context.player(), payload.citizen());
			}
		});
	}

	/** Sends the kingdom snapshot, which makes the client open the kingdom screen. */
	public static void openKingdomScreen(ServerPlayer player, @Nullable UUID focus) {
		if (KingdomManager.get(player.level().getServer()).kingdom(player.getUUID()).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.architecturoverse.no_kingdom").withStyle(ChatFormatting.YELLOW));
			return;
		}
		sendSnapshot(player, focus);
	}

	private static void sendSnapshot(ServerPlayer player, @Nullable UUID focus) {
		Optional<Kingdom> found = KingdomManager.get(player.level().getServer()).kingdom(player.getUUID());
		if (found.isEmpty()) {
			return;
		}
		Kingdom kingdom = found.get();
		List<KingdomSnapshotPayload.VillageInfo> villages = kingdom.villages().stream()
			.map(v -> new KingdomSnapshotPayload.VillageInfo(v.id(), v.dimension().identifier().toString(), v.center(),
				kingdom.population(v.id()), v.warehouse().isPresent(), v.mine().map(MineSite::progress).orElse(-1),
				warehouseStock(player.level().getServer(), v)))
			.toList();
		List<KingdomSnapshotPayload.CitizenInfo> citizens = kingdom.citizens().stream()
			.map(c -> new KingdomSnapshotPayload.CitizenInfo(c.uuid(), c.name(), c.job().ordinal(), c.mode().ordinal(), c.villageId()))
			.toList();
		ServerPlayNetworking.send(player, new KingdomSnapshotPayload(kingdom.ownerName(), villages, citizens, Optional.ofNullable(focus)));
	}

	/** The most plentiful items in the village warehouse, merged by item type. Empty if not loaded. */
	private static List<KingdomSnapshotPayload.StockEntry> warehouseStock(MinecraftServer server, ClaimedVillage village) {
		ServerLevel level = server.getLevel(village.dimension());
		if (level == null || village.warehouse().isEmpty() || !level.isLoaded(village.warehouse().get())
			|| !(level.getBlockEntity(village.warehouse().get()) instanceof WarehouseBlockEntity warehouse)) {
			return List.of();
		}
		Map<Item, Integer> totals = new LinkedHashMap<>();
		for (int i = 0; i < warehouse.getContainerSize(); i++) {
			ItemStack stack = warehouse.getItem(i);
			if (!stack.isEmpty()) {
				totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return totals.entrySet().stream()
			.sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
			.limit(STOCK_SHOWN)
			.map(e -> new KingdomSnapshotPayload.StockEntry(new ItemStack(e.getKey()), e.getValue()))
			.toList();
	}

	/** Updates the kingdom record and, if the citizen is loaded, the entity itself. */
	public static boolean commandCitizen(ServerPlayer ruler, UUID citizenId, @Nullable CitizenJob job, @Nullable CitizenMode mode) {
		KingdomManager manager = KingdomManager.get(ruler.level().getServer());
		Optional<Kingdom> kingdom = manager.kingdom(ruler.getUUID());
		if (kingdom.isEmpty()) {
			return false;
		}
		Optional<CitizenRecord> updated = manager.updateCitizen(kingdom.get(), citizenId, record -> {
			CitizenRecord result = record;
			if (job != null) {
				result = result.withJob(job);
			}
			if (mode != null) {
				result = result.withMode(mode);
			}
			return result;
		});
		if (updated.isEmpty()) {
			return false;
		}
		for (ServerLevel level : ruler.level().getServer().getAllLevels()) {
			if (level.getEntity(citizenId) instanceof CitizenEntity citizen) {
				if (job != null && citizen.getJob() != job) {
					citizen.applyJob(job);
				}
				if (mode != null && citizen.getMode() != mode) {
					citizen.applyMode(mode);
				}
				break;
			}
		}
		return true;
	}
}
