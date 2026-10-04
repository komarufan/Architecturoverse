package com.architecturoverse.village;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenNames;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.registry.ModEntities;
import com.architecturoverse.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Ringing a village bell with the scepter seizes the village. Its iron golems defend it:
 * the ruler has to defeat them, or (sneaking) bribe them with emeralds.
 */
public final class CaptureHandler {
	public static final int EMERALDS_PER_GOLEM = 5;
	private static final String GOLEM_TAG_PREFIX = "architecturoverse.ruler.";

	private CaptureHandler() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(ModItems.SCEPTER) || !level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
				tryCapture(serverPlayer, serverLevel, hit.getBlockPos());
			}
			return InteractionResult.SUCCESS;
		});
	}

	public static void tryCapture(ServerPlayer player, ServerLevel level, BlockPos bell) {
		KingdomManager manager = KingdomManager.get(level.getServer());
		Optional<KingdomManager.Claim> existing = manager.claimAt(level.dimension(), bell, KingdomManager.MIN_VILLAGE_DISTANCE);
		if (existing.isPresent()) {
			if (existing.get().kingdom().owner().equals(player.getUUID())) {
				player.sendOverlayMessage(Component.translatable("message.architecturoverse.already_yours"));
			} else {
				player.sendOverlayMessage(Component.translatable("message.architecturoverse.owned_by_other",
					existing.get().kingdom().ownerName()).withStyle(ChatFormatting.RED));
			}
			return;
		}

		AABB area = villageArea(bell);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area, Villager::isAlive);
		if (villagers.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.architecturoverse.no_villagers").withStyle(ChatFormatting.YELLOW));
			return;
		}

		List<IronGolem> defenders = level.getEntitiesOfClass(IronGolem.class, area, g -> g.isAlive() && ownerOf(g).isEmpty());
		if (!defenders.isEmpty()) {
			int price = defenders.size() * EMERALDS_PER_GOLEM;
			if (!player.isShiftKeyDown()) {
				defenders.forEach(golem -> golem.setTarget(player));
				level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.6F);
				player.sendSystemMessage(Component.translatable("message.architecturoverse.golems_defend", defenders.size(), price)
					.withStyle(ChatFormatting.GOLD));
				return;
			}
			if (!payEmeralds(player, price)) {
				player.sendOverlayMessage(Component.translatable("message.architecturoverse.not_enough_emeralds", price)
					.withStyle(ChatFormatting.RED));
				return;
			}
			defenders.forEach(golem -> recruitGolem(golem, player.getUUID()));
		}

		capture(player, level, bell, villagers);
	}

	private static void capture(ServerPlayer player, ServerLevel level, BlockPos bell, List<Villager> villagers) {
		KingdomManager manager = KingdomManager.get(level.getServer());
		Kingdom kingdom = manager.getOrCreate(player);
		boolean firstVillage = kingdom.villages().isEmpty();
		ClaimedVillage village = manager.addVillage(kingdom, level.dimension(), bell);

		int sworn = 0;
		for (Villager villager : villagers) {
			String name = villager.hasCustomName() ? villager.getPlainTextName() : CitizenNames.random(level.getRandom());
			CitizenEntity citizen = villager.convertTo(ModEntities.CITIZEN, ConversionParams.single(villager, false, false),
				c -> c.swearLoyalty(player.getUUID(), village, villager.getVillagerData(), name));
			if (citizen != null) {
				manager.putCitizen(kingdom, citizen.toRecord());
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, citizen.getX(), citizen.getY() + 1.0, citizen.getZ(), 10, 0.4, 0.6, 0.4, 0.0);
				sworn++;
			}
		}

		level.playSound(null, bell, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.connection.send(new ClientboundSetTitleTextPacket(
			Component.translatable("title.architecturoverse.captured").withStyle(ChatFormatting.GOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
			Component.translatable("title.architecturoverse.captured.sub", sworn)));

		if (firstVillage) {
			if (!player.getInventory().contains(new ItemStack(ModItems.RULER_BOOK))) {
				player.addItem(new ItemStack(ModItems.RULER_BOOK));
			}
			player.sendSystemMessage(Component.translatable("message.architecturoverse.first_village").withStyle(ChatFormatting.GREEN));
		}
	}

	private static boolean payEmeralds(ServerPlayer player, int price) {
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		if (player.getInventory().countItem(Items.EMERALD) < price) {
			return false;
		}
		player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.EMERALD), price, player.inventoryMenu.getCraftSlots());
		return true;
	}

	/** Bribed golems stop defending the village and guard it for the ruler instead. */
	public static void recruitGolem(IronGolem golem, UUID ruler) {
		golem.entityTags().removeIf(tag -> tag.startsWith(GOLEM_TAG_PREFIX));
		golem.addTag(GOLEM_TAG_PREFIX + ruler);
		golem.setPlayerCreated(true);
		golem.setTarget(null);
		golem.setPersistenceRequired();
	}

	public static Optional<UUID> ownerOf(IronGolem golem) {
		for (String tag : golem.entityTags()) {
			if (tag.startsWith(GOLEM_TAG_PREFIX)) {
				try {
					return Optional.of(UUID.fromString(tag.substring(GOLEM_TAG_PREFIX.length())));
				} catch (IllegalArgumentException ignored) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	public static AABB villageArea(BlockPos center) {
		int r = ClaimedVillage.RADIUS;
		return new AABB(center).inflate(r, 24, r);
	}
}
