package com.architecturoverse.citizen.goal;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.village.Arrests;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A soldier fetches an arrested citizen and leads them to prison, walking right behind them
 * all the way to the cell.
 */
public class EscortGoal extends Goal {
	private static final double SEARCH_RADIUS = 96.0;
	private static final double BEHIND = 1.5;

	private final CitizenEntity soldier;
	private @Nullable CitizenEntity prisoner;

	public EscortGoal(CitizenEntity soldier) {
		this.soldier = soldier;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!soldier.isSoldier() || (soldier.tickCount + soldier.getId()) % 20 != 0
			|| soldier.getVillage().flatMap(ClaimedVillage::prison).isEmpty()) {
			return false;
		}
		ServerLevel level = (ServerLevel) soldier.level();
		for (CitizenEntity candidate : level.getEntitiesOfClass(CitizenEntity.class, soldier.getBoundingBox().inflate(SEARCH_RADIUS),
			c -> c.isAlive() && c.getStatus() == CitizenStatus.ARRESTED && c.getVillageId() == soldier.getVillageId()
				&& c.getRuler() != null && c.getRuler().equals(soldier.getRuler()))) {
			if (Arrests.claimEscort(level, candidate, soldier)) {
				prisoner = candidate;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return prisoner != null && prisoner.isAlive() && prisoner.getStatus() == CitizenStatus.ARRESTED && soldier.isSoldier();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		soldier.setTarget(null);
	}

	@Override
	public void stop() {
		prisoner = null;
		soldier.getWalker().reset();
	}

	@Override
	public void tick() {
		if (prisoner == null) {
			return;
		}
		soldier.getLookControl().setLookAt(prisoner);
		if (soldier.distanceToSqr(prisoner) > 3.0 * 3.0) {
			soldier.walkTo(prisoner.blockPosition(), 2.0);
			return;
		}
		// Keep a step and a half behind the prisoner, on the side away from where they are going.
		Vec3 heading = prisoner.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4
			? prisoner.getDeltaMovement().normalize()
			: Vec3.directionFromRotation(0.0F, prisoner.getYRot());
		Vec3 spot = prisoner.position().subtract(heading.x * BEHIND, 0.0, heading.z * BEHIND);
		soldier.walkTo(BlockPos.containing(spot), 1.0);
	}
}
