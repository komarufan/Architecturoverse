package com.architecturoverse.village;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenNames;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.military.EnemySoldierEntity;
import com.architecturoverse.registry.ModEntities;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

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
	private static final double FIGHT_RADIUS = 128.0;
	/** Give the defenders a moment to arrive before deciding nobody defends the village. */
	private static final int MIN_FIGHT_TICKS = 20 * 20;
	private static final Set<String> UNDER_ATTACK = new HashSet<>();

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
		UNDER_ATTACK.clear();
		for (Kingdom kingdom : manager.all()) {
			for (ClaimedVillage village : kingdom.villages()) {
				ServerLevel level = server.getLevel(village.dimension());
				if (level != null && level.isLoaded(village.center())) {
					watchFight(level, kingdom, village);
				}
			}
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
	/** Whether enemy soldiers are in this village right now (updated every second). */
	public static boolean isUnderAttack(@Nullable UUID ruler, int villageId) {
		return ruler != null && UNDER_ATTACK.contains(ruler + "|" + villageId);
	}

	/**
	 * Notes villages with enemies in them; once no defending soldier is left standing, the
	 * enemies stop fighting and start plundering.
	 */
	private static void watchFight(ServerLevel level, Kingdom kingdom, ClaimedVillage village) {
		List<EnemySoldierEntity> enemies = level.getEntitiesOfClass(EnemySoldierEntity.class, new AABB(village.center()).inflate(FIGHT_RADIUS),
			e -> e.isAlive() && kingdom.owner().equals(e.getVictim()) && e.getVillageId() == village.id());
		if (enemies.isEmpty()) {
			return;
		}
		UNDER_ATTACK.add(kingdom.owner() + "|" + village.id());
		boolean fighting = enemies.stream().anyMatch(e -> e.getPhase() == EnemySoldierEntity.Phase.FIGHT && e.tickCount > MIN_FIGHT_TICKS);
		if (!fighting) {
			return;
		}
		boolean defended = !level.getEntitiesOfClass(CitizenEntity.class, new AABB(village.center()).inflate(FIGHT_RADIUS),
			c -> c.isAlive() && c.isSoldier() && c.getVillageId() == village.id() && kingdom.owner().equals(c.getRuler())).isEmpty();
		if (defended) {
			return;
		}
		boolean sapperChosen = false;
		for (EnemySoldierEntity enemy : enemies) {
			boolean sapper = !sapperChosen && village.militaryBase().isPresent();
			sapperChosen |= sapper;
			enemy.startPillage(sapper);
		}
		ServerPlayer ruler = level.getServer().getPlayerList().getPlayer(kingdom.owner());
		if (ruler != null) {
			ruler.sendSystemMessage(Component.translatable("message.architecturoverse.defenders_fell", village.id()).withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** An enemy soldier gives up: becomes a prisoner of the ruler and is led to prison. */
	public static void surrender(ServerLevel level, EnemySoldierEntity enemy) {
		UUID victim = enemy.getVictim();
		Optional<Kingdom> kingdom = victim == null ? Optional.empty() : KingdomManager.get(level.getServer()).kingdom(victim);
		Optional<ClaimedVillage> village = kingdom.flatMap(k -> k.village(enemy.getVillageId()));
		if (village.isEmpty()) {
			return;
		}
		boolean wasFighting = enemy.getPhase() == EnemySoldierEntity.Phase.FIGHT;
		String name = CitizenNames.random(level.getRandom());
		VillagerData look = new VillagerData(BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.TAIGA),
			BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE), 1);
		CitizenEntity captive = enemy.convertTo(ModEntities.CITIZEN, ConversionParams.single(enemy, false, false), c -> {
			c.swearLoyalty(victim, village.get(), look, name);
			c.setGlowingTag(false);
			c.setCustomNameVisible(false);
			c.setStatus(CitizenStatus.ARRESTED);
		});
		if (captive == null) {
			return;
		}
		KingdomManager.get(level.getServer()).putCitizen(kingdom.get(), captive.toRecord());
		ServerPlayer ruler = level.getServer().getPlayerList().getPlayer(victim);
		if (ruler != null) {
			ruler.sendSystemMessage(Component.translatable(village.get().prison().isPresent()
				? "message.architecturoverse.enemy_surrendered" : "message.architecturoverse.enemy_surrendered_no_prison", name)
				.withStyle(ChatFormatting.GOLD));
		}
		onEnemyGone(level, enemy, wasFighting);
	}

	/** The sapper blew up the military base: it has to be built again. */
	public static void baseDestroyed(ServerLevel level, UUID ruler, int villageId) {
		KingdomManager manager = KingdomManager.get(level.getServer());
		manager.kingdom(ruler).ifPresent(k -> manager.updateVillage(k, villageId, v -> v.withMilitaryBase(Optional.empty())));
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(ruler);
		if (player != null) {
			player.sendSystemMessage(Component.translatable("message.architecturoverse.base_destroyed", villageId).withStyle(ChatFormatting.DARK_RED));
		}
	}

	public static void onEnemyGone(ServerLevel level, EnemySoldierEntity fallen, boolean wasFighting) {
		List<EnemySoldierEntity> left = level.getEntitiesOfClass(EnemySoldierEntity.class, new AABB(fallen.blockPosition()).inflate(160.0),
			e -> e != fallen && e.isAlive() && fallen.getVictim() != null && fallen.getVictim().equals(e.getVictim())
				&& e.getVillageId() == fallen.getVillageId());
		ServerPlayer ruler = level.getServer().getPlayerList().getPlayer(fallen.getVictim());
		if (ruler == null) {
			return;
		}
		if (left.isEmpty() && wasFighting) {
			ruler.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.architecturoverse.victory").withStyle(ChatFormatting.GOLD)));
			ruler.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.architecturoverse.victory.sub")));
			level.playSound(null, ruler.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		} else if (!left.isEmpty()) {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.enemies_left", left.size()).withStyle(ChatFormatting.RED));
		}
	}
}
