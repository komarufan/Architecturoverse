package com.architecturoverse.village;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.kingdom.CitizenRecord;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * An unhappy village rises up: a third of the civilians turn against the ruler and fight the
 * ruler and the soldiers until they are killed or give up. Then the village calms down.
 */
public final class Rebellions {
	/** Mood a village returns to once the rebellion is put down. */
	public static final int MOOD_AFTER_REBELLION = 45;

	private Rebellions() {
	}

	public static void start(ServerLevel level, Kingdom kingdom, ClaimedVillage village) {
		KingdomManager manager = KingdomManager.get(level.getServer());
		List<CitizenRecord> civilians = new ArrayList<>(kingdom.citizens().stream()
			.filter(c -> c.villageId() == village.id() && c.status() == CitizenStatus.FREE && c.job() != CitizenJob.SOLDIER)
			.toList());
		if (civilians.isEmpty()) {
			return;
		}
		java.util.Collections.shuffle(civilians, new java.util.Random(level.getRandom().nextLong()));
		int count = Math.max(1, (civilians.size() + 2) / 3);
		for (CitizenRecord rebel : civilians.subList(0, count)) {
			manager.updateCitizen(kingdom, rebel.uuid(), r -> r.withStatus(CitizenStatus.REBEL));
			if (level.getEntity(rebel.uuid()) instanceof CitizenEntity citizen) {
				citizen.setStatus(CitizenStatus.REBEL);
			}
		}
		manager.updateVillage(kingdom, village.id(), v -> v.withMood(v.mood().withRebellion(true)));
		ServerPlayer ruler = level.getServer().getPlayerList().getPlayer(kingdom.owner());
		if (ruler != null) {
			ruler.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.architecturoverse.rebellion").withStyle(ChatFormatting.DARK_RED)));
			ruler.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.architecturoverse.rebellion.sub", count)));
			ruler.sendSystemMessage(Component.translatable("message.architecturoverse.rebellion_started", village.id(), count)
				.withStyle(ChatFormatting.RED));
		}
	}

	/** A rebel gives up: goes to prison if there is one, otherwise back to an idle life. */
	public static void surrender(ServerLevel level, CitizenEntity rebel) {
		boolean prison = rebel.getVillage().flatMap(ClaimedVillage::prison).isPresent();
		CitizenStatus status = prison ? CitizenStatus.ARRESTED : CitizenStatus.FREE;
		rebel.setStatus(status);
		rebel.applyJob(CitizenJob.UNEMPLOYED);
		rebel.getKingdomManager().ifPresent(manager -> rebel.getKingdom().ifPresent(kingdom ->
			manager.updateCitizen(kingdom, rebel.getUUID(), r -> r.withStatus(status).withJob(CitizenJob.UNEMPLOYED))));
		ServerPlayer ruler = rebel.getRulerPlayer();
		if (ruler != null) {
			ruler.sendSystemMessage(Component.translatable(prison ? "message.architecturoverse.rebel_jailed" : "message.architecturoverse.rebel_gave_up",
				rebel.getDisplayName()).withStyle(ChatFormatting.GOLD));
		}
		if (rebel.getRuler() != null) {
			checkEnd(level, rebel.getRuler(), rebel.getVillageId(), null);
		}
	}

	/** Ends the rebellion once no rebel of the village is left. */
	public static void checkEnd(ServerLevel level, UUID rulerId, int villageId, @Nullable CitizenEntity leaving) {
		KingdomManager manager = KingdomManager.get(level.getServer());
		Optional<Kingdom> kingdom = manager.kingdom(rulerId);
		Optional<ClaimedVillage> village = kingdom.flatMap(k -> k.village(villageId));
		if (village.isEmpty() || !village.get().mood().rebellion()) {
			return;
		}
		boolean rebelsLeft = kingdom.get().citizens().stream().anyMatch(c -> c.villageId() == villageId && c.status() == CitizenStatus.REBEL
			&& (leaving == null || !c.uuid().equals(leaving.getUUID())));
		if (rebelsLeft) {
			return;
		}
		manager.updateVillage(kingdom.get(), villageId, v -> v.withMood(v.mood().withRebellion(false).withMood(MOOD_AFTER_REBELLION)));
		ServerPlayer ruler = level.getServer().getPlayerList().getPlayer(rulerId);
		if (ruler != null) {
			ruler.sendSystemMessage(Component.translatable("message.architecturoverse.rebellion_over", villageId).withStyle(ChatFormatting.GREEN));
		}
	}
}
