package com.architecturoverse.village;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.kingdom.Placement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Who escorts which prisoner, and which prison cells are taken. */
public final class Arrests {
	/** Prisoner -> escorting soldier. */
	private static final Map<UUID, UUID> ESCORTS = new HashMap<>();
	private static final double ESCORT_CLOSE_SQ = 4.0 * 4.0;

	private Arrests() {
	}

	/** Whether this soldier is (or now becomes) the escort of this prisoner. */
	public static boolean claimEscort(ServerLevel level, CitizenEntity prisoner, CitizenEntity soldier) {
		UUID current = ESCORTS.get(prisoner.getUUID());
		if (current != null && !current.equals(soldier.getUUID())
			&& level.getEntity(current) instanceof CitizenEntity other && other.isAlive() && other.isSoldier()) {
			return false;
		}
		ESCORTS.put(prisoner.getUUID(), soldier.getUUID());
		return true;
	}

	/** The prisoner's escort, if it is right next to the prisoner. */
	public static @Nullable CitizenEntity escortNearby(ServerLevel level, CitizenEntity prisoner) {
		UUID escort = ESCORTS.get(prisoner.getUUID());
		if (escort != null && level.getEntity(escort) instanceof CitizenEntity soldier && soldier.isAlive()
			&& soldier.distanceToSqr(prisoner) < ESCORT_CLOSE_SQ) {
			return soldier;
		}
		return null;
	}

	public static void release(UUID prisoner) {
		ESCORTS.remove(prisoner);
	}

	/** The first cell of the prison nobody is assigned to, or -1 when all are taken. */
	public static int freeCell(ServerLevel level, Placement prison, CitizenEntity prisoner) {
		Set<Integer> taken = new HashSet<>();
		for (CitizenEntity other : level.getEntitiesOfClass(CitizenEntity.class, new AABB(prison.origin()).inflate(64.0),
			c -> c != prisoner && c.isAlive() && c.getPrisonCell() >= 0
				&& (c.getStatus() == CitizenStatus.ARRESTED || c.getStatus() == CitizenStatus.IMPRISONED))) {
			taken.add(other.getPrisonCell());
		}
		for (int i = 0; i < Placement.prisonCells(); i++) {
			if (!taken.contains(i)) {
				return i;
			}
		}
		return -1;
	}
}
