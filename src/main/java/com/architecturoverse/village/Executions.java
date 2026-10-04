package com.architecturoverse.village;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.kingdom.CitizenRecord;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MilitaryBase;
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
 * Sentencing citizens. The condemned walks into the prison cell of the village military base;
 * one soldier fetches an axe from the base workbench and carries out the sentence.
 */
public final class Executions {
	/** Prisoner -> the soldier who carries out the sentence. */
	private static final Map<UUID, UUID> EXECUTIONERS = new HashMap<>();

	private Executions() {
	}

	/** Sentences ({@code condemn = true}) or pardons a citizen. Returns false and tells the ruler why if impossible. */
	public static boolean sentence(ServerPlayer ruler, UUID citizenId, boolean condemn) {
		KingdomManager manager = KingdomManager.get(ruler.level().getServer());
		Optional<Kingdom> kingdom = manager.kingdom(ruler.getUUID());
		Optional<CitizenRecord> record = kingdom.flatMap(k -> k.citizen(citizenId));
		if (kingdom.isEmpty() || record.isEmpty()) {
			return false;
		}
		Optional<ClaimedVillage> village = kingdom.get().village(record.get().villageId());
		if (condemn && village.flatMap(ClaimedVillage::militaryBase).isEmpty()) {
			ruler.sendOverlayMessage(Component.translatable("message.architecturoverse.execution_needs_base").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		manager.updateCitizen(kingdom.get(), citizenId, r -> r.withCondemned(condemn));
		for (ServerLevel level : ruler.level().getServer().getAllLevels()) {
			if (level.getEntity(citizenId) instanceof CitizenEntity citizen) {
				citizen.setCondemned(condemn);
				if (!condemn) {
					village.flatMap(ClaimedVillage::militaryBase).ifPresent(base -> setGate(level, base, true));
				}
			}
		}
		if (!condemn) {
			EXECUTIONERS.remove(citizenId);
		}
		ruler.sendSystemMessage(Component.translatable(condemn ? "message.architecturoverse.condemned" : "message.architecturoverse.pardoned",
			record.get().name()).withStyle(condemn ? ChatFormatting.RED : ChatFormatting.GREEN));
		return true;
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

	/** Opens or closes the prison cell gate. */
	public static void setGate(ServerLevel level, MilitaryBase base, boolean open) {
		BlockPos gate = base.gate();
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
