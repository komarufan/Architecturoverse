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

	private final CitizenEntity prisoner;

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
		}
		prisoner.walkTo(cell, 0.8);
	}

	/** Standing on the cell floor, behind the bars. */
	public static boolean isInCell(CitizenEntity citizen, MilitaryBase base) {
		BlockPos cell = base.cell();
		return citizen.blockPosition().equals(cell) || citizen.position().distanceToSqr(Vec3.atBottomCenterOf(cell)) < 0.5;
	}
}
