package com.architecturoverse.citizen.goal;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.MilitaryBase;
import com.architecturoverse.village.Executions;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** A condemned citizen walks into the prison cell of the military base and waits there. */
public class PrisonerGoal extends Goal {
	private static final double OPEN_GATE_DISTANCE_SQ = 4.0 * 4.0;

	private static final int STEP_IN_AFTER_TICKS = 100;

	private final CitizenEntity prisoner;
	private int nearCellTicks;

	public PrisonerGoal(CitizenEntity prisoner) {
		this.prisoner = prisoner;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	private Optional<MilitaryBase> base() {
		return prisoner.getVillage().flatMap(ClaimedVillage::militaryBase);
	}

	@Override
	public boolean canUse() {
		return prisoner.isCondemned() && base().isPresent();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		Optional<MilitaryBase> base = base();
		if (base.isEmpty()) {
			return;
		}
		ServerLevel level = (ServerLevel) prisoner.level();
		BlockPos cell = base.get().cell();
		if (isInCell(prisoner, base.get())) {
			prisoner.getNavigation().stop();
			prisoner.getLookControl().setLookAt(Vec3.atCenterOf(base.get().gate()));
			return;
		}
		if (prisoner.distanceToSqr(Vec3.atCenterOf(base.get().gate())) < OPEN_GATE_DISTANCE_SQ) {
			Executions.setGate(level, base.get(), true);
			// Right in front of the cell but not getting in (crowded doorway, odd block): step in directly.
			if (++nearCellTicks > STEP_IN_AFTER_TICKS) {
				prisoner.snapTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, prisoner.getYRot(), prisoner.getXRot());
				prisoner.getNavigation().stop();
				return;
			}
		} else {
			nearCellTicks = 0;
		}
		prisoner.walkTo(cell, 0.8);
	}

	/** Standing on the cell floor, behind the bars. */
	public static boolean isInCell(CitizenEntity citizen, MilitaryBase base) {
		BlockPos cell = base.cell();
		return citizen.blockPosition().equals(cell) || citizen.position().distanceToSqr(Vec3.atBottomCenterOf(cell)) < 0.5;
	}
}
