package com.architecturoverse.village;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.kingdom.CitizenRecord;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MilitaryBase;
import com.architecturoverse.kingdom.Placement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ruler's justice: death sentences (carried out at the military base), prison terms (a soldier
 * leads the prisoner to a prison cell) and pardons.
 */
public final class Executions {
	/** What the ruler decided about a citizen. */
	public enum Sentence {
		/** Back to a free life, out of any cell. */
		PARDON,
		DEATH,
		PRISON;

		public static Sentence byId(int id) {
			Sentence[] all = values();
			return id >= 0 && id < all.length ? all[id] : PARDON;
		}
	}

	private static final int EXECUTION_MOOD_PENALTY = 5;
	private static final int PRISON_MOOD_PENALTY = 2;

	/** Prisoner -> the soldier who carries out the sentence. */
	private static final Map<UUID, UUID> EXECUTIONERS = new HashMap<>();

	private Executions() {
	}

	/** Passes the sentence; returns false and tells the ruler why if it cannot be done. */
	public static boolean sentence(ServerPlayer ruler, UUID citizenId, Sentence sentence) {
		KingdomManager manager = KingdomManager.get(ruler.level().getServer());
		Optional<Kingdom> kingdom = manager.kingdom(ruler.getUUID());
		Optional<CitizenRecord> record = kingdom.flatMap(k -> k.citizen(citizenId));
		if (kingdom.isEmpty() || record.isEmpty()) {
			return false;
		}
		Optional<ClaimedVillage> village = kingdom.get().village(record.get().villageId());
		if (village.isEmpty()) {
			return false;
		}
		CitizenStatus status;
		Component message;
		switch (sentence) {
			case DEATH -> {
				if (village.get().militaryBase().isEmpty()) {
					ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.execution_needs_base").withStyle(ChatFormatting.YELLOW));
					return false;
				}
				status = CitizenStatus.CONDEMNED;
				message = Component.translatable(hasMessenger(kingdom.get(), village.get())
					? "message.architecturoverse.condemned_messenger" : "message.architecturoverse.condemned", record.get().name());
			}
			case PRISON -> {
				if (village.get().prison().isEmpty()) {
					ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.prison_needed").withStyle(ChatFormatting.YELLOW));
					return false;
				}
				long prisoners = kingdom.get().citizens().stream()
					.filter(c -> c.villageId() == village.get().id() && (c.status() == CitizenStatus.ARRESTED || c.status() == CitizenStatus.IMPRISONED))
					.count();
				if (prisoners >= Placement.prisonCells()) {
					ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.prison_full").withStyle(ChatFormatting.YELLOW));
					return false;
				}
				status = CitizenStatus.ARRESTED;
				message = Component.translatable("message.architecturoverse.arrested", record.get().name());
			}
			default -> {
				status = CitizenStatus.FREE;
				message = Component.translatable("message.architecturoverse.pardoned", record.get().name());
			}
		}
		manager.updateCitizen(kingdom.get(), citizenId, r -> r.withStatus(status));
		boolean messenger = sentence == Sentence.DEATH && hasMessenger(kingdom.get(), village.get());
		for (ServerLevel level : ruler.level().getServer().getAllLevels()) {
			if (level.getEntity(citizenId) instanceof CitizenEntity citizen) {
				citizen.setStatus(status);
				if (sentence == Sentence.DEATH && !messenger) {
					citizen.receiveNotice(net.minecraft.world.item.ItemStack.EMPTY);
				}
				if (sentence == Sentence.PARDON) {
					citizen.applyJob(citizen.getJob()); // hands back the job tool instead of the paper
					village.get().militaryBase().ifPresent(base -> setGate(level, base.gate(), true));
					village.get().prison().ifPresent(prison -> {
						for (int i = 0; i < Placement.prisonCells(); i++) {
							setGate(level, prison.prisonGate(i), true);
						}
					});
				}
			}
		}
		if (sentence != Sentence.PARDON) {
			int penalty = sentence == Sentence.DEATH ? EXECUTION_MOOD_PENALTY : PRISON_MOOD_PENALTY;
			manager.updateVillage(kingdom.get(), village.get().id(), v -> v.withMood(v.mood().changeMood(-penalty)));
		} else {
			EXECUTIONERS.remove(citizenId);
			Arrests.release(citizenId);
		}
		ruler.sendSystemMessage(message.copy().withStyle(sentence == Sentence.PARDON ? ChatFormatting.GREEN : ChatFormatting.RED));
		return true;
	}

	/** A post office and a free messenger are needed for the sentence to be delivered in person. */
	private static boolean hasMessenger(Kingdom kingdom, ClaimedVillage village) {
		return village.postOffice().isPresent() && kingdom.citizens().stream()
			.anyMatch(c -> c.villageId() == village.id() && c.job() == CitizenJob.MESSENGER && c.status() == CitizenStatus.FREE);
	}

	/**
	 * Whether this soldier is (or now becomes) the executioner of this prisoner. The first
	 * soldier to ask gets the job; it is handed on if that soldier is gone.
	 */
	public static boolean claimExecution(ServerLevel level, CitizenEntity prisoner, CitizenEntity soldier) {
		UUID current = EXECUTIONERS.get(prisoner.getUUID());
		if (current != null && !current.equals(soldier.getUUID())
			&& level.getEntity(current) instanceof CitizenEntity other && other.isAlive() && other.isSoldier()) {
			return false;
		}
		EXECUTIONERS.put(prisoner.getUUID(), soldier.getUUID());
		return true;
	}

	public static void finished(UUID prisoner) {
		EXECUTIONERS.remove(prisoner);
	}

	/** Opens or closes the prison cell gate of the military base. */
	public static void setGate(ServerLevel level, MilitaryBase base, boolean open) {
		setGate(level, base.gate(), open);
	}

	/** Opens or closes a cell gate. */
	public static void setGate(ServerLevel level, BlockPos gate, boolean open) {
		// Bases built by older versions had bars right above the gate, leaving a doorway too low to walk through.
		if (open && level.getBlockState(gate.above()).is(Blocks.IRON_BARS)) {
			level.setBlockAndUpdate(gate.above(), Blocks.AIR.defaultBlockState());
		}
		BlockState state = level.getBlockState(gate);
		if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN) != open) {
			level.setBlockAndUpdate(gate, state.setValue(FenceGateBlock.OPEN, open));
		}
	}
}
