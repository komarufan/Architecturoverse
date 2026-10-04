package com.architecturoverse.village;

import com.architecturoverse.block.WarehouseBlockEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.kingdom.CitizenRecord;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.VillageMood;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.Items;

/**
 * Wages and the village mood. Once per Minecraft day every working citizen is paid from the
 * warehouse emeralds (soldiers twice as much). Pay, beds and harsh justice move the mood; a
 * village whose mood falls too low rises in rebellion.
 */
public final class Economy {
	public static final long PAY_INTERVAL = 24000L;
	public static final int REBELLION_MOOD = 15;
	public static final int UNHAPPY_MOOD = 35;

	private Economy() {
	}

	/** Emeralds a citizen earns per day. Idlers, prisoners and rebels get nothing. */
	public static int wage(CitizenRecord citizen) {
		if (citizen.status() != CitizenStatus.FREE || citizen.job() == CitizenJob.UNEMPLOYED) {
			return 0;
		}
		return citizen.job() == CitizenJob.SOLDIER ? 2 : 1;
	}

	public static int dailyWages(Kingdom kingdom, int villageId) {
		return kingdom.citizens().stream().filter(c -> c.villageId() == villageId).mapToInt(Economy::wage).sum();
	}

	/** Ticks until the next payday of the village. */
	public static long ticksToPayday(ServerLevel level, ClaimedVillage village) {
		long last = village.mood().lastPayday();
		return last < 0 ? PAY_INTERVAL : Math.max(0, last + PAY_INTERVAL - level.getGameTime());
	}

	/** Called every second. */
	public static void tick(MinecraftServer server) {
		KingdomManager manager = KingdomManager.get(server);
		for (Kingdom kingdom : manager.all()) {
			for (ClaimedVillage village : kingdom.villages()) {
				ServerLevel level = server.getLevel(village.dimension());
				if (level == null) {
					continue;
				}
				long now = level.getGameTime();
				if (village.mood().lastPayday() < 0) {
					manager.updateVillage(kingdom, village.id(), v -> v.withMood(v.mood().withLastPayday(now)));
				} else if (now - village.mood().lastPayday() >= PAY_INTERVAL && level.isLoaded(village.center())) {
					payday(level, kingdom, village);
				}
			}
		}
	}

	public static void payday(ServerLevel level, Kingdom kingdom, ClaimedVillage village) {
		KingdomManager manager = KingdomManager.get(level.getServer());
		int wages = dailyWages(kingdom, village.id());
		int paid = 0;
		Optional<WarehouseBlockEntity> warehouse = village.warehouse()
			.filter(level::isLoaded)
			.map(level::getBlockEntity)
			.filter(WarehouseBlockEntity.class::isInstance)
			.map(WarehouseBlockEntity.class::cast);
		if (wages > 0 && warehouse.isPresent()) {
			paid = warehouse.get().take(s -> s.is(Items.EMERALD), wages).stream().mapToInt(s -> s.getCount()).sum();
		}
		int delta;
		if (wages == 0) {
			delta = 2;
		} else if (paid >= wages) {
			delta = 8;
		} else if (paid > 0) {
			delta = -10;
		} else {
			delta = -20;
		}
		long population = kingdom.citizens().stream().filter(c -> c.villageId() == village.id() && !c.status().isDetained()).count();
		long beds = level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), village.center(), ClaimedVillage.RADIUS,
			PoiManager.Occupancy.ANY).count();
		delta += beds >= population ? 3 : -8;
		VillageMood mood = village.mood().changeMood(delta).withLastPayday(level.getGameTime());
		manager.updateVillage(kingdom, village.id(), v -> v.withMood(mood));

		ServerPlayer ruler = level.getServer().getPlayerList().getPlayer(kingdom.owner());
		if (ruler != null) {
			ruler.sendSystemMessage(Component.translatable("message.architecturoverse.payday", village.id(), paid, wages, mood.mood())
				.withStyle(paid >= wages ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			if (beds < population) {
				ruler.sendSystemMessage(Component.translatable("message.architecturoverse.not_enough_beds", village.id(), beds, population)
					.withStyle(ChatFormatting.YELLOW));
			}
			if (mood.mood() < UNHAPPY_MOOD && !mood.rebellion()) {
				ruler.sendSystemMessage(Component.translatable("message.architecturoverse.unhappy", village.id()).withStyle(ChatFormatting.RED));
			}
		}
		if (mood.mood() <= REBELLION_MOOD && !mood.rebellion()) {
			manager.kingdom(kingdom.owner()).flatMap(k -> k.village(village.id()))
				.ifPresent(v -> Rebellions.start(level, kingdom, v));
		}
	}
}
