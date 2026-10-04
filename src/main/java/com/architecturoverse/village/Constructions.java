package com.architecturoverse.village;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Construction;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MilitaryBase;
import com.architecturoverse.kingdom.Placement;
import com.architecturoverse.structure.ConstructionPlan;
import com.architecturoverse.structure.Material;
import com.architecturoverse.structure.StructureType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * Building sites: starting one, handing out blocks to workers so they never work on the
 * same block, and finishing the site once every block is in place.
 */
public final class Constructions {
	/** How far ahead of the finished part workers may pick blocks, so several can work in parallel. */
	private static final int CLAIM_WINDOW = 48;
	/** A claim is dropped if the worker did not finish the block in time (e.g. it died). */
	private static final long CLAIM_TICKS = 20 * 60;

	/** Site key -> step index -> claim. */
	private static final Map<String, Map<Integer, Claim>> CLAIMS = new HashMap<>();

	private record Claim(UUID worker, long until) {
	}

	private Constructions() {
	}

	/** Starts building {@code type} in the village; returns false (and tells the ruler why) if that is not possible. */
	public static boolean start(ServerPlayer ruler, int villageId, StructureType type) {
		KingdomManager manager = KingdomManager.get(ruler.level().getServer());
		Optional<Kingdom> kingdom = manager.kingdom(ruler.getUUID());
		Optional<ClaimedVillage> village = kingdom.flatMap(k -> k.village(villageId));
		if (kingdom.isEmpty() || village.isEmpty()) {
			return false;
		}
		if (village.get().construction().isPresent()) {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.already_constructing").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (village.get().has(type)) {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.already_built", type.displayName()).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		ServerLevel level = ruler.level().getServer().getLevel(village.get().dimension());
		Optional<SiteFinder.StructureSite> site = level == null ? Optional.empty()
			: SiteFinder.structure(level, village.get().center(), type.blueprint());
		if (site.isEmpty()) {
			ruler.sendSystemMessage(Component.translatable("message.architecturoverse.no_site", type.displayName(), villageId)
				.withStyle(ChatFormatting.RED));
			return false;
		}
		Construction construction = new Construction(type, site.get().origin(), site.get().rotation(), 0);
		manager.updateVillage(kingdom.get(), villageId, v -> v.withConstruction(Optional.of(construction)));
		ruler.sendSystemMessage(Component.translatable("message.architecturoverse.construction_started", type.displayName(), villageId,
			site.get().origin().getX(), site.get().origin().getY(), site.get().origin().getZ()).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/**
	 * Picks the next block this worker should take care of and reserves it. Returns -1 if
	 * there is nothing free right now; finishes the site when every block is done.
	 */
	public static int claimNext(CitizenEntity worker, ServerLevel level, Predicate<ConstructionPlan.Step> canDo) {
		Optional<ClaimedVillage> village = worker.getVillage();
		if (village.isEmpty() || village.get().construction().isEmpty()) {
			return -1;
		}
		Construction site = village.get().construction().get();
		List<ConstructionPlan.Step> steps = ConstructionPlan.of(site);
		Map<Integer, Claim> claims = claims(site);
		long now = level.getGameTime();
		claims.values().removeIf(c -> c.until() < now);

		int pointer = site.pointer();
		while (pointer < steps.size() && steps.get(pointer).isDone(level) && !claims.containsKey(pointer)) {
			pointer++;
		}
		if (pointer != site.pointer()) {
			int newPointer = pointer;
			updateSite(worker, site, s -> s.withPointer(newPointer));
		}
		if (pointer >= steps.size()) {
			finishOrRewind(worker, level, site, steps, claims);
			return -1;
		}
		for (int i = pointer; i < Math.min(steps.size(), pointer + CLAIM_WINDOW); i++) {
			Claim claim = claims.get(i);
			if (claim != null && !claim.worker().equals(worker.getUUID())) {
				continue;
			}
			if (!steps.get(i).isDone(level) && canDo.test(steps.get(i))) {
				claims.put(i, new Claim(worker.getUUID(), now + CLAIM_TICKS));
				return i;
			}
		}
		return -1;
	}

	/** The material of the next open block nobody works on, i.e. what the site is waiting for. */
	public static Material neededNext(CitizenEntity worker, ServerLevel level) {
		Construction site = siteOf(worker);
		if (site == null) {
			return Material.NONE;
		}
		List<ConstructionPlan.Step> steps = ConstructionPlan.of(site);
		Map<Integer, Claim> claims = claims(site);
		for (int i = site.pointer(); i < Math.min(steps.size(), site.pointer() + CLAIM_WINDOW); i++) {
			if (!claims.containsKey(i) && !steps.get(i).isDone(level)) {
				return steps.get(i).cost();
			}
		}
		return Material.NONE;
	}

	public static void release(Construction site, int step) {
		claims(site).remove(step);
	}

	/** Everything looked done: either finish, or go back to blocks that were broken again meanwhile. */
	private static void finishOrRewind(CitizenEntity worker, ServerLevel level, Construction site, List<ConstructionPlan.Step> steps,
		Map<Integer, Claim> claims) {
		for (int i = 0; i < steps.size(); i++) {
			if (!steps.get(i).isDone(level) && !claims.containsKey(i)) {
				int first = i;
				updateSite(worker, site, s -> s.withPointer(first));
				return;
			}
		}
		if (!claims.isEmpty()) {
			return; // someone is still placing the last blocks
		}
		finish(worker, level, site);
	}

	private static void finish(CitizenEntity worker, ServerLevel level, Construction site) {
		CLAIMS.remove(key(site));
		worker.getKingdomManager().ifPresent(manager -> worker.getKingdom().ifPresent(kingdom ->
			manager.updateVillage(kingdom, worker.getVillageId(), v -> {
				ClaimedVillage done = v.withConstruction(Optional.empty());
				Placement placement = new Placement(site.type(), site.origin(), site.rotation());
				return switch (site.type()) {
					case MILITARY_BASE -> done.withMilitaryBase(Optional.of(new MilitaryBase(site.origin(), site.rotation())));
					case PRISON -> done.withPrison(Optional.of(placement));
					case POST_OFFICE -> done.withPostOffice(Optional.of(placement));
				};
			})));
		level.playSound(null, site.origin(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.0F);
		ServerPlayer ruler = worker.getRulerPlayer();
		if (ruler != null) {
			ruler.sendSystemMessage(Component.translatable("message.architecturoverse.construction_done", site.type().displayName(),
				worker.getVillageId()).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Blocks still to place per material (only blocks that cost something are counted). */
	public static Map<Material, Integer> remaining(ServerLevel level, Construction site) {
		Map<Material, Integer> remaining = new EnumMap<>(Material.class);
		List<ConstructionPlan.Step> steps = ConstructionPlan.of(site);
		for (int i = site.pointer(); i < steps.size(); i++) {
			ConstructionPlan.Step step = steps.get(i);
			if (step.cost() != Material.NONE && !step.isDone(level)) {
				remaining.merge(step.cost(), 1, Integer::sum);
			}
		}
		return remaining;
	}

	/** Share of the plan that is done, 0..100. */
	public static int percentDone(ServerLevel level, Construction site) {
		List<ConstructionPlan.Step> steps = ConstructionPlan.of(site);
		int done = site.pointer();
		for (int i = site.pointer(); i < steps.size(); i++) {
			if (steps.get(i).isDone(level)) {
				done++;
			}
		}
		return steps.isEmpty() ? 100 : done * 100 / steps.size();
	}

	private static void updateSite(CitizenEntity worker, Construction site, java.util.function.UnaryOperator<Construction> change) {
		worker.getKingdomManager().ifPresent(manager -> worker.getKingdom().ifPresent(kingdom ->
			manager.updateVillage(kingdom, worker.getVillageId(), v -> v.construction()
				.filter(c -> key(c).equals(key(site)))
				.map(c -> v.withConstruction(Optional.of(change.apply(c))))
				.orElse(v))));
	}

	private static Map<Integer, Claim> claims(Construction site) {
		return CLAIMS.computeIfAbsent(key(site), k -> new HashMap<>());
	}

	private static String key(Construction site) {
		return site.type() + "@" + site.origin().asLong();
	}

	/** The construction of the worker's village, if any. */
	public static @Nullable Construction siteOf(CitizenEntity worker) {
		return worker.getVillage().flatMap(ClaimedVillage::construction).orElse(null);
	}
}
