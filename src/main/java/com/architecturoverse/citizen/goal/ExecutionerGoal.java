package com.architecturoverse.citizen.goal;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.MilitaryBase;
import com.architecturoverse.village.Executions;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A soldier carries out a death sentence: walks to the workbench of the military base, takes
 * the axe lying there, goes to the prison cell, opens the gate and strikes until it is over.
 */
public class ExecutionerGoal extends Goal {
	private static final int TAKE_AXE_TICKS = 30;
	private static final int STRIKE_INTERVAL = 20;
	private static final float STRIKE_DAMAGE = 10.0F;
	private static final double MAX_DISTANCE_TO_BASE_SQ = 96.0 * 96.0;

	private enum Phase { TO_WORKBENCH, TAKE_AXE, TO_CELL, STRIKE }

	private final CitizenEntity soldier;
	private @Nullable CitizenEntity prisoner;
	private Phase phase = Phase.TO_WORKBENCH;
	private int timer;

	public ExecutionerGoal(CitizenEntity soldier) {
		this.soldier = soldier;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	private Optional<MilitaryBase> base() {
		return soldier.getVillage().flatMap(ClaimedVillage::militaryBase);
	}

	@Override
	public boolean canUse() {
		if (!soldier.isSoldier() || soldier.isCondemned() || (soldier.tickCount + soldier.getId()) % 20 != 0) {
			return false;
		}
		Optional<MilitaryBase> base = base();
		if (base.isEmpty() || soldier.distanceToSqr(Vec3.atCenterOf(base.get().cell())) > MAX_DISTANCE_TO_BASE_SQ) {
			return false;
		}
		ServerLevel level = (ServerLevel) soldier.level();
		for (CitizenEntity candidate : level.getEntitiesOfClass(CitizenEntity.class, new AABB(base.get().cell()).inflate(2.0),
			c -> c.isAlive() && c.isCondemned() && c.getRuler() != null && c.getRuler().equals(soldier.getRuler())
				&& PrisonerGoal.isInCell(c, base.get()))) {
			if (Executions.claimExecution(level, candidate, soldier)) {
				prisoner = candidate;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return prisoner != null && prisoner.isAlive() && prisoner.isCondemned() && soldier.isSoldier() && base().isPresent();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		phase = Phase.TO_WORKBENCH;
		timer = 0;
		soldier.setTarget(null);
	}

	@Override
	public void stop() {
		if (prisoner != null && !prisoner.isAlive()) {
			Executions.finished(prisoner.getUUID());
		}
		prisoner = null;
		// The axe goes back on the workbench; the soldier takes up the sword again.
		soldier.setItemSlot(EquipmentSlot.MAINHAND, CitizenJob.SOLDIER.tool());
	}

	@Override
	public void tick() {
		Optional<MilitaryBase> base = base();
		if (base.isEmpty() || prisoner == null) {
			return;
		}
		ServerLevel level = (ServerLevel) soldier.level();
		switch (phase) {
			case TO_WORKBENCH -> {
				if (soldier.walkTo(base.get().workbench(), 2.2)) {
					phase = Phase.TAKE_AXE;
					timer = TAKE_AXE_TICKS;
				}
			}
			case TAKE_AXE -> {
				soldier.getLookControl().setLookAt(Vec3.atCenterOf(base.get().workbench()));
				if (timer == TAKE_AXE_TICKS / 2) {
					soldier.swing(InteractionHand.MAIN_HAND);
				}
				if (--timer <= 0) {
					soldier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
					level.playSound(null, soldier.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 1.0F, 0.8F);
					phase = Phase.TO_CELL;
				}
			}
			case TO_CELL -> {
				Executions.setGate(level, base.get(), true);
				if (soldier.walkTo(base.get().gate(), 1.6)) {
					phase = Phase.STRIKE;
					timer = 10;
				}
			}
			case STRIKE -> {
				soldier.getLookControl().setLookAt(prisoner);
				if (soldier.distanceToSqr(prisoner) > 3.5 * 3.5) {
					soldier.walkTo(prisoner.blockPosition(), 2.0);
					return;
				}
				if (--timer <= 0) {
					timer = STRIKE_INTERVAL;
					soldier.swing(InteractionHand.MAIN_HAND);
					prisoner.hurtServer(level, soldier.damageSources().mobAttack(soldier), STRIKE_DAMAGE);
				}
			}
		}
	}
}
