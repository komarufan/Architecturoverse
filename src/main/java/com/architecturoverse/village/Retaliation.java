package com.architecturoverse.village;

import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.military.EnemySoldierEntity;
import com.architecturoverse.registry.ModEntities;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Revenge for a robbed merchant: two to three minutes later a squad of soldiers from a
 * neighbouring village marches on the ruler's village. Killing all of them wins the fight.
 */
public final class Retaliation {
	private static final int MIN_DELAY = 20 * 120;
	private static final int MAX_DELAY = 20 * 180;
	private static final int SPAWN_DISTANCE = 36;
	private static final int MIN_SQUAD = 3;
	private static final int MAX_SQUAD = 8;

	private Retaliation() {
	}

	/** Schedules the revenge raid on the village, unless one is already on its way. */
	public static void schedule(ServerLevel level, UUID ruler, int villageId) {
		KingdomManager manager = KingdomManager.get(level.getServer());
		Optional<Kingdom> kingdom = manager.kingdom(ruler);
		Optional<ClaimedVillage> village = kingdom.flatMap(k -> k.village(villageId));
		if (village.isEmpty() || village.get().raidAt() > 0) {
			return;
		}
		long at = level.getGameTime() + Mth.nextInt(level.getRandom(), MIN_DELAY, MAX_DELAY);
		manager.updateVillage(kingdom.get(), villageId, v -> v.withRaidAt(at));
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(ruler);
		if (player != null) {
			player.sendSystemMessage(Component.translatable("message.architecturoverse.revenge_warning", villageId).withStyle(ChatFormatting.RED));
		}
	}

	/** Called every second: launches raids whose time has come (only while the ruler is online). */
	public static void tick(MinecraftServer server) {
		KingdomManager manager = KingdomManager.get(server);
		for (Kingdom kingdom : manager.all()) {
			ServerPlayer ruler = server.getPlayerList().getPlayer(kingdom.owner());
			if (ruler == null) {
				continue;
			}
			for (ClaimedVillage village : kingdom.villages()) {
				ServerLevel level = server.getLevel(village.dimension());
				if (village.raidAt() > 0 && level != null && level.getGameTime() >= village.raidAt() && level.isLoaded(village.center())) {
					manager.updateVillage(kingdom, village.id(), v -> v.withRaidAt(0));
					launch(level, kingdom, village, ruler);
				}
			}
		}
	}

	/** Spawns the enemy squad at the edge of the village and sends it marching on the bell. */
	public static List<EnemySoldierEntity> launch(ServerLevel level, Kingdom kingdom, ClaimedVillage village, ServerPlayer ruler) {
		long defenders = kingdom.citizens().stream().filter(c -> c.villageId() == village.id() && c.job() == CitizenJob.SOLDIER).count();
		int size = Mth.clamp((int) defenders + 2, MIN_SQUAD, MAX_SQUAD);
		double angle = level.getRandom().nextDouble() * Math.PI * 2;
		int x = village.center().getX() + (int) (Math.cos(angle) * SPAWN_DISTANCE);
		int z = village.center().getZ() + (int) (Math.sin(angle) * SPAWN_DISTANCE);
		BlockPos rally = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		List<EnemySoldierEntity> squad = spawnSquad(level, kingdom.owner(), village, rally, size);
		ruler.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.architecturoverse.raid").withStyle(ChatFormatting.DARK_RED)));
		ruler.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.architecturoverse.raid.sub", squad.size())));
		ruler.sendSystemMessage(Component.translatable("message.architecturoverse.raid_started", squad.size(), village.id(),
			rally.getX(), rally.getY(), rally.getZ()).withStyle(ChatFormatting.RED));
		level.playSound(null, village.center(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64.0F, 1.0F);
		return squad;
	}

	public static List<EnemySoldierEntity> spawnSquad(ServerLevel level, UUID victim, ClaimedVillage village, BlockPos rally, int size) {
		List<EnemySoldierEntity> squad = new java.util.ArrayList<>();
		for (int i = 0; i < size; i++) {
			int dx = level.getRandom().nextIntBetweenInclusive(-3, 3);
			int dz = level.getRandom().nextIntBetweenInclusive(-3, 3);
			int sx = rally.getX() + dx;
			int sz = rally.getZ() + dz;
			BlockPos pos = new BlockPos(sx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz), sz);
			EnemySoldierEntity soldier = ModEntities.ENEMY_SOLDIER.create(level, EntitySpawnReason.EVENT);
			if (soldier == null) {
				continue;
			}
			soldier.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			soldier.sendOff(victim, village.id(), village.center());
			level.addFreshEntity(soldier);
			squad.add(soldier);
		}
		return squad;
	}

	/** Tells the ruler when the last enemy of the raid falls. */
	public static void onEnemyKilled(ServerLevel level, EnemySoldierEntity fallen) {
		List<EnemySoldierEntity> left = level.getEntitiesOfClass(EnemySoldierEntity.class, new AABB(fallen.blockPosition()).inflate(160.0),
			e -> e != fallen && e.isAlive() && fallen.getVictim() != null && fallen.getVictim().equals(e.getVictim())
				&& e.getVillageId() == fallen.getVillageId());
		ServerPlayer ruler = level.getServer().getPlayerList().getPlayer(fallen.getVictim());
		if (ruler == null) {
			return;
		}
		if (left.isEmpty()) {
			ruler.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.architecturoverse.victory").withStyle(ChatFormatting.GOLD)));
			ruler.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.architecturoverse.victory.sub")));
			level.playSound(null, ruler.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		} else {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.enemies_left", left.size()).withStyle(ChatFormatting.RED));
		}
	}
}
