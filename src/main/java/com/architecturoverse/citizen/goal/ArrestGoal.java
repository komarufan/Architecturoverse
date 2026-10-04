package com.architecturoverse.citizen.goal;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Placement;
import com.architecturoverse.village.Arrests;
import com.architecturoverse.village.Executions;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * An arrested citizen waits until a soldier comes, then walks to the prison with the soldier
 * right behind and steps into a cell. Imprisoned citizens stay in their cell.
 */
public class ArrestGoal extends Goal {
	private static final double OPEN_GATE_DISTANCE_SQ = 3.0 * 3.0;
	private static final int STEP_IN_AFTER_TICKS = 100;

	private final CitizenEntity prisoner;
	private int nearCellTicks;

	public ArrestGoal(CitizenEntity prisoner) {
		this.prisoner = prisoner;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	private Optional<Placement> prison() {
		return prisoner.getVillage().flatMap(ClaimedVillage::prison);
	}

	@Override
	public boolean canUse() {
		CitizenStatus status = prisoner.getStatus();
		return status == CitizenStatus.ARRESTED || status == CitizenStatus.IMPRISONED;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ServerLevel level = (ServerLevel) prisoner.level();
		Optional<Placement> prison = prison();
		if (prison.isEmpty()) {
			prisoner.getNavigation().stop(); // waits under guard until there is a prison
			return;
		}
		if (prisoner.getPrisonCell() < 0) {
			prisoner.setPrisonCell(Arrests.freeCell(level, prison.get(), prisoner));
			if (prisoner.getPrisonCell() < 0) {
				prisoner.getNavigation().stop();
				return;
			}
		}
		BlockPos cell = prison.get().prisonCell(prisoner.getPrisonCell());
		BlockPos gate = prison.get().prisonGate(prisoner.getPrisonCell());
		if (isInCell(cell)) {
			prisoner.getNavigation().stop();
			prisoner.getLookControl().setLookAt(Vec3.atCenterOf(gate));
			if (prisoner.getStatus() == CitizenStatus.ARRESTED) {
				lockUp(level, gate);
			}
			return;
		}
		if (prisoner.getStatus() == CitizenStatus.ARRESTED && Arrests.escortNearby(level, prisoner) == null) {
			// No soldier at hand yet: wait where you are.
			prisoner.getNavigation().stop();
			return;
		}
		if (prisoner.distanceToSqr(Vec3.atCenterOf(gate)) < OPEN_GATE_DISTANCE_SQ) {
			Executions.setGate(level, gate, true);
			if (++nearCellTicks > STEP_IN_AFTER_TICKS) {
				prisoner.snapTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, prisoner.getYRot(), prisoner.getXRot());
				return;
			}
		} else {
			nearCellTicks = 0;
		}
		prisoner.walkTo(cell, 0.8);
	}

	private void lockUp(ServerLevel level, BlockPos gate) {
		prisoner.setStatus(CitizenStatus.IMPRISONED);
		prisoner.getKingdomManager().ifPresent(manager -> prisoner.getKingdom().ifPresent(kingdom ->
			manager.updateCitizen(kingdom, prisoner.getUUID(), r -> r.withStatus(CitizenStatus.IMPRISONED))));
		Arrests.release(prisoner.getUUID());
		Executions.setGate(level, gate, false);
	}

	private boolean isInCell(BlockPos cell) {
		return prisoner.blockPosition().equals(cell) || prisoner.position().distanceToSqr(Vec3.atBottomCenterOf(cell)) < 0.5;
	}
}
