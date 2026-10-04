package com.architecturoverse.citizen.goal;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.village.Retaliation;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import org.jspecify.annotations.Nullable;

/** While enemies are in the village, the unemployed run home (to the nearest bed) and stay there. */
public class HideGoal extends Goal {
	private static final int SHELTER_RADIUS = 48;

	private final CitizenEntity citizen;
	private @Nullable BlockPos shelter;

	public HideGoal(CitizenEntity citizen) {
		this.citizen = citizen;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return citizen.getJob() == CitizenJob.UNEMPLOYED && citizen.getStatus() == CitizenStatus.FREE
			&& Retaliation.isUnderAttack(citizen.getRuler(), citizen.getVillageId());
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		ServerLevel level = (ServerLevel) citizen.level();
		BlockPos home = citizen.hasHome() ? citizen.getHomePosition() : citizen.blockPosition();
		shelter = level.getPoiManager().findClosest(type -> type.is(PoiTypes.HOME), citizen.blockPosition(), SHELTER_RADIUS,
			PoiManager.Occupancy.ANY).orElse(home);
	}

	@Override
	public void stop() {
		shelter = null;
	}

	@Override
	public void tick() {
		if (shelter != null) {
			citizen.walkTo(shelter, 1.5);
		}
	}
}
