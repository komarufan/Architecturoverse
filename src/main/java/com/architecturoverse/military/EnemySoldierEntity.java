package com.architecturoverse.military;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.work.Walker;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MilitaryBase;
import com.architecturoverse.kingdom.Placement;
import com.architecturoverse.village.Retaliation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A soldier of a neighbouring village that marches on the ruler's village to avenge a robbed
 * merchant. Fights the ruler and the ruler's citizens. Once no defender is left the squad
 * plunders: sets the houses on fire, blows up the military base and leaves. Badly hurt
 * soldiers may give themselves up.
 */
public class EnemySoldierEntity extends PathfinderMob implements Enemy {
	public enum Phase { FIGHT, PILLAGE, RETREAT }

	private static final float SURRENDER_HEALTH = 0.3F;
	private static final int FIRES_PER_SOLDIER = 3;
	private static final int RETREAT_DISTANCE = 64;
	private static final int MAX_RETREAT_TICKS = 20 * 60;

	private @Nullable UUID victim;
	private int villageId = -1;
	private @Nullable BlockPos target;
	private Phase phase = Phase.FIGHT;
	private boolean sapper;
	private boolean triedToSurrender;
	private final Walker walker = new Walker(this);

	public EnemySoldierEntity(EntityType<? extends EnemySoldierEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.getNavigation().setCanOpenDoors(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 26.0)
			.add(Attributes.MOVEMENT_SPEED, 0.5)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.ARMOR, 4.0)
			.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	/** Called right after spawning: who is attacked and where to march. */
	public void sendOff(UUID victim, int villageId, BlockPos villageCenter) {
		this.victim = victim;
		this.villageId = villageId;
		this.target = villageCenter;
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		this.setCustomName(Component.translatable("entity.architecturoverse.enemy_soldier").withStyle(ChatFormatting.RED));
		this.setCustomNameVisible(true);
		this.setGlowingTag(true);
	}

	public @Nullable UUID getVictim() {
		return victim;
	}

	public int getVillageId() {
		return villageId;
	}

	public Phase getPhase() {
		return phase;
	}

	/** The defenders are gone: plunder the village. The sapper also blows up the military base. */
	public void startPillage(boolean sapper) {
		this.phase = Phase.PILLAGE;
		this.sapper = sapper;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.9, true));
		this.goalSelector.addGoal(2, new PillageGoal());
		this.goalSelector.addGoal(2, new RetreatGoal());
		this.goalSelector.addGoal(3, new MarchGoal());
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, EnemySoldierEntity.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(player, level) -> phase == Phase.FIGHT && victim != null && victim.equals(player.getUUID())));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, 10, true, false,
			(citizen, level) -> phase == Phase.FIGHT && victim != null && victim.equals(((CitizenEntity) citizen).getRuler())));
	}

	private Optional<ClaimedVillage> village() {
		if (victim == null || !(level() instanceof ServerLevel serverLevel)) {
			return Optional.empty();
		}
		return KingdomManager.get(serverLevel.getServer()).kingdom(victim).flatMap(k -> k.village(villageId));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive() && !triedToSurrender && getHealth() < getMaxHealth() * SURRENDER_HEALTH) {
			triedToSurrender = true;
			if (random.nextBoolean()) {
				Retaliation.surrender(level, this);
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel serverLevel && victim != null) {
			Retaliation.onEnemyGone(serverLevel, this, phase == Phase.FIGHT);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		// The sword is not dropped; a few emeralds of war loot are.
		spawnAtLocation(level, new ItemStack(Items.EMERALD, 1 + random.nextInt(3)));
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (victim != null) {
			output.store("Victim", UUIDUtil.CODEC, victim);
		}
		output.putInt("Village", villageId);
		if (target != null) {
			output.store("Target", BlockPos.CODEC, target);
		}
		output.putString("Phase", phase.name());
		output.putBoolean("Sapper", sapper);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		victim = input.read("Victim", UUIDUtil.CODEC).orElse(null);
		villageId = input.getIntOr("Village", -1);
		target = input.read("Target", BlockPos.CODEC).orElse(null);
		try {
			phase = Phase.valueOf(input.getStringOr("Phase", Phase.FIGHT.name()));
		} catch (IllegalArgumentException e) {
			phase = Phase.FIGHT;
		}
		sapper = input.getBooleanOr("Sapper", false);
	}

	/** Marches to the village bell and roams around it looking for a fight. */
	private class MarchGoal extends Goal {
		private @Nullable BlockPos roamTo;

		MarchGoal() {
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return phase == Phase.FIGHT && target != null && getTarget() == null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (target == null) {
				return;
			}
			if (roamTo == null) {
				if (walker.walkTo(target, 6.0)) {
					roamTo = nextRoamSpot();
				}
			} else if (walker.walkTo(roamTo, 3.0) || random.nextInt(400) == 0) {
				roamTo = nextRoamSpot();
			}
		}

		private BlockPos nextRoamSpot() {
			for (int attempt = 0; attempt < 10; attempt++) {
				Optional<BlockPos> ground = Walker.dryGround(level(), target.getX() + random.nextIntBetweenInclusive(-16, 16),
					target.getZ() + random.nextIntBetweenInclusive(-16, 16));
				if (ground.isPresent()) {
					return ground.get();
				}
			}
			return target;
		}
	}

	/** Sets houses and buildings on fire; the sapper blows up the military base first. */
	private class PillageGoal extends Goal {
		private final List<BlockPos> targets = new ArrayList<>();
		private int fires;

		PillageGoal() {
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return phase == Phase.PILLAGE;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			ServerLevel level = (ServerLevel) level();
			Optional<ClaimedVillage> village = village();
			if (village.isEmpty()) {
				phase = Phase.RETREAT;
				return;
			}
			if (sapper && village.get().militaryBase().isPresent()) {
				MilitaryBase base = village.get().militaryBase().get();
				if (walker.walkTo(base.rally(), 2.5)) {
					blowUp(level, base);
				}
				return;
			}
			if (targets.isEmpty()) {
				pickTargets(level, village.get());
				if (targets.isEmpty()) {
					phase = Phase.RETREAT;
					return;
				}
			}
			BlockPos next = targets.getFirst();
			if (walker.walkTo(next, 3.0)) {
				targets.removeFirst();
				if (setFire(level, next) && ++fires >= FIRES_PER_SOLDIER) {
					phase = Phase.RETREAT;
				}
			}
		}

		private void blowUp(ServerLevel level, MilitaryBase base) {
			sapper = false;
			for (BlockPos spot : List.of(base.rally(), base.workbench(), base.cell())) {
				level.explode(EnemySoldierEntity.this, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 4.0F, true,
					Level.ExplosionInteraction.TNT);
			}
			if (victim != null) {
				Retaliation.baseDestroyed(level, victim, villageId);
			}
		}

		/** Houses (beds) and the village buildings, in random order. */
		private void pickTargets(ServerLevel level, ClaimedVillage village) {
			level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), village.center(), ClaimedVillage.RADIUS, PoiManager.Occupancy.ANY)
				.forEach(poi -> targets.add(poi.getPos()));
			village.warehouse().ifPresent(targets::add);
			village.prison().map(Placement::door).ifPresent(targets::add);
			village.postOffice().map(Placement::door).ifPresent(targets::add);
			java.util.Collections.shuffle(targets, new java.util.Random(random.nextLong()));
			while (targets.size() > FIRES_PER_SOLDIER * 2) {
				targets.removeLast();
			}
		}

		/** Lights a fire in a free spot next to something that burns. */
		private boolean setFire(ServerLevel level, BlockPos around) {
			swing(InteractionHand.MAIN_HAND);
			for (BlockPos pos : BlockPos.betweenClosed(around.offset(-2, -1, -2), around.offset(2, 2, 2))) {
				if (!level.getBlockState(pos).isAir()) {
					continue;
				}
				for (Direction direction : Direction.values()) {
					if (level.getBlockState(pos.relative(direction)).ignitedByLava()) {
						level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
						return true;
					}
				}
			}
			return false;
		}
	}

	/** Walks away from the village and disappears. */
	private class RetreatGoal extends Goal {
		private @Nullable BlockPos away;
		private int ticks;

		RetreatGoal() {
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return phase == Phase.RETREAT;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			BlockPos center = target != null ? target : blockPosition();
			if (away == null) {
				double angle = Math.atan2(getZ() - center.getZ(), getX() - center.getX());
				int x = center.getX() + (int) (Math.cos(angle) * RETREAT_DISTANCE);
				int z = center.getZ() + (int) (Math.sin(angle) * RETREAT_DISTANCE);
				away = Walker.dryGround(level(), x, z).orElse(new BlockPos(x, getBlockY(), z));
			}
			double dx = getX() - center.getX();
			double dz = getZ() - center.getZ();
			if (++ticks > MAX_RETREAT_TICKS || dx * dx + dz * dz > (RETREAT_DISTANCE - 8) * (RETREAT_DISTANCE - 8) || walker.walkTo(away, 4.0)) {
				discard();
			}
		}
	}
}
