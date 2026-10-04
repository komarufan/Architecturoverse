package com.architecturoverse.citizen.goal;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenMode;
import com.architecturoverse.citizen.work.Walker;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.MilitaryBase;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/**
 * What soldiers do when not fighting, following the village's army order: patrol (stay around
 * the military base and walk rounds through the village), hold the base, or gather at the
 * rally point. Each soldier takes its own spot so they do not stand inside each other.
 */
public class SoldierGoal extends Goal {
	private static final int PATROL_RADIUS = 32;
	private static final int MIN_WAIT = 20 * 15;
	private static final int MAX_WAIT = 20 * 40;

	private final CitizenEntity soldier;
	private @Nullable BlockPos destination;
	private int waitTicks;
	/** Whether this soldier has already been to the base since it was built; the first patrol stop is the base. */
	private boolean visitedBase;

	public SoldierGoal(CitizenEntity soldier) {
		this.soldier = soldier;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return soldier.isSoldier() && soldier.getMode() == CitizenMode.WORK && soldier.getTarget() == null && soldier.getVillage().isPresent();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		destination = null;
		soldier.getWalker().reset();
	}

	@Override
	public void tick() {
		Optional<ClaimedVillage> village = soldier.getVillage();
		if (village.isEmpty()) {
			return;
		}
		BlockPos wanted = switch (village.get().armyOrder()) {
			case RALLY -> village.get().rallyPoint().map(this::ownSpot).orElse(null);
			case BASE -> village.get().militaryBase().map(base -> ownSpot(base.rally())).orElse(null);
			case PATROL -> null;
		};
		if (wanted != null) {
			destination = wanted;
			soldier.walkTo(wanted, 1.2);
			return;
		}
		patrol(village.get());
	}

	private void patrol(ClaimedVillage village) {
		if (destination == null || --waitTicks <= 0) {
			destination = nextPatrolStop(village);
			waitTicks = MIN_WAIT + soldier.getRandom().nextInt(MAX_WAIT - MIN_WAIT);
		}
		if (destination != null) {
			soldier.walkTo(destination, 2.0);
		}
	}

	/** First the new military base, then half of the time somewhere in the base, otherwise a random spot in the village. */
	private @Nullable BlockPos nextPatrolStop(ClaimedVillage village) {
		Optional<MilitaryBase> base = village.militaryBase();
		if (base.isPresent() && (!visitedBase || soldier.getRandom().nextBoolean())) {
			visitedBase = true;
			BlockPos rally = base.get().rally();
			return rally.offset(soldier.getRandom().nextIntBetweenInclusive(-3, 3), 0, soldier.getRandom().nextIntBetweenInclusive(-2, 2));
		}
		// Several tries, because a random spot may well be in a pond or the river.
		for (int attempt = 0; attempt < 10; attempt++) {
			int x = village.center().getX() + soldier.getRandom().nextIntBetweenInclusive(-PATROL_RADIUS, PATROL_RADIUS);
			int z = village.center().getZ() + soldier.getRandom().nextIntBetweenInclusive(-PATROL_RADIUS, PATROL_RADIUS);
			Optional<BlockPos> ground = Walker.dryGround(soldier.level(), x, z);
			if (ground.isPresent()) {
				return ground.get();
			}
		}
		return null;
	}

	/** Spreads soldiers over a 3x3 grid around the point, two blocks apart. */
	private BlockPos ownSpot(BlockPos point) {
		int index = Math.floorMod(soldier.getUUID().hashCode(), 9);
		return point.offset((index % 3 - 1) * 2, 0, (index / 3 - 1) * 2);
	}
}
