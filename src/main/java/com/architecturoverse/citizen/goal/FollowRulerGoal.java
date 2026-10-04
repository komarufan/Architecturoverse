package com.architecturoverse.citizen.goal;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenMode;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jspecify.annotations.Nullable;

/** Walks after the ruler while the citizen is ordered to follow; teleports when left far behind. */
public class FollowRulerGoal extends Goal {
	private final CitizenEntity citizen;
	private final double speed;
	private final float startDistance;
	private final float teleportDistance;
	private @Nullable ServerPlayer ruler;
	private int timeToRecalcPath;

	public FollowRulerGoal(CitizenEntity citizen, double speed, float startDistance, float teleportDistance) {
		this.citizen = citizen;
		this.speed = speed;
		this.startDistance = startDistance;
		this.teleportDistance = teleportDistance;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (citizen.getMode() != CitizenMode.FOLLOW || citizen.getTarget() != null) {
			return false;
		}
		ServerPlayer player = citizen.getRulerPlayer();
		if (player == null || player.isSpectator() || player.level() != citizen.level()) {
			return false;
		}
		if (citizen.distanceToSqr(player) < startDistance * startDistance) {
			return false;
		}
		this.ruler = player;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return ruler != null && citizen.getMode() == CitizenMode.FOLLOW && citizen.getTarget() == null
			&& ruler.level() == citizen.level() && citizen.distanceToSqr(ruler) > 4.0;
	}

	@Override
	public void start() {
		this.timeToRecalcPath = 0;
	}

	@Override
	public void stop() {
		this.ruler = null;
		citizen.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (ruler == null) {
			return;
		}
		citizen.getLookControl().setLookAt(ruler, 10.0F, citizen.getMaxHeadXRot());
		if (--timeToRecalcPath > 0) {
			return;
		}
		timeToRecalcPath = 10;
		if (citizen.distanceToSqr(ruler) >= teleportDistance * teleportDistance) {
			teleportNear(ruler.blockPosition());
		} else {
			citizen.getNavigation().moveTo(ruler, speed);
		}
	}

	private void teleportNear(BlockPos target) {
		for (int attempt = 0; attempt < 10; attempt++) {
			int dx = citizen.getRandom().nextIntBetweenInclusive(-3, 3);
			int dz = citizen.getRandom().nextIntBetweenInclusive(-3, 3);
			if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
				continue;
			}
			int dy = citizen.getRandom().nextIntBetweenInclusive(-1, 1);
			BlockPos pos = target.offset(dx, dy, dz);
			if (WalkNodeEvaluator.getPathTypeStatic(citizen, pos) == PathType.WALKABLE
				&& citizen.level().noCollision(citizen, citizen.getBoundingBox().move(pos.subtract(citizen.blockPosition())))) {
				citizen.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, citizen.getYRot(), citizen.getXRot());
				citizen.getNavigation().stop();
				return;
			}
		}
	}
}
