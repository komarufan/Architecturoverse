package com.architecturoverse.military;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.work.Walker;
import com.architecturoverse.village.Retaliation;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A soldier of a neighbouring village that marches on the ruler's village to avenge a robbed
 * merchant. Fights the ruler and the ruler's citizens; golems and the ruler's soldiers fight back.
 */
public class EnemySoldierEntity extends PathfinderMob implements Enemy {
	private @Nullable UUID victim;
	private int villageId = -1;
	private @Nullable BlockPos target;
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

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.9, true));
		this.goalSelector.addGoal(3, new MarchGoal());
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, EnemySoldierEntity.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(player, level) -> victim != null && victim.equals(player.getUUID())));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CitizenEntity.class, 10, true, false,
			(citizen, level) -> victim != null && victim.equals(((CitizenEntity) citizen).getRuler())));
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel serverLevel && victim != null) {
			Retaliation.onEnemyKilled(serverLevel, this);
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
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		victim = input.read("Victim", UUIDUtil.CODEC).orElse(null);
		villageId = input.getIntOr("Village", -1);
		target = input.read("Target", BlockPos.CODEC).orElse(null);
	}

	/** Marches to the village bell and roams around it looking for a fight. */
	private class MarchGoal extends Goal {
		private @Nullable BlockPos roamTo;

		MarchGoal() {
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return target != null && getTarget() == null;
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
}
