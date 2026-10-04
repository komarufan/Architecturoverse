package com.architecturoverse.citizen;

import com.architecturoverse.citizen.goal.FollowRulerGoal;
import com.architecturoverse.kingdom.CitizenRecord;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.network.KingdomNetworking;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A villager that swore loyalty to a ruler. Uses plain goal-based AI instead of the
 * villager brain, so jobs can be added as simple goals.
 */
public class CitizenEntity extends PathfinderMob {
	private static final EntityDataAccessor<VillagerData> DATA_LOOK =
		SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.VILLAGER_DATA);
	private static final EntityDataAccessor<Integer> DATA_JOB =
		SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MODE =
		SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);

	private @Nullable UUID ruler;
	private int villageId = -1;
	private boolean syncedWithKingdom;

	public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) {
		super(type, level);
		this.getNavigation().setCanOpenDoors(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 24.0)
			.add(Attributes.MOVEMENT_SPEED, 0.5)
			.add(Attributes.ATTACK_DAMAGE, 2.0)
			.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_LOOK, Villager.createDefaultVillagerData());
		entityData.define(DATA_JOB, CitizenJob.UNEMPLOYED.ordinal());
		entityData.define(DATA_MODE, CitizenMode.WORK.ordinal());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.8, true) {
			@Override
			public boolean canUse() {
				return isSoldier() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return isSoldier() && super.canContinueToUse();
			}
		});
		this.goalSelector.addGoal(1, new PanicGoal(this, 0.75) {
			@Override
			public boolean canUse() {
				return !isSoldier() && super.canUse();
			}
		});
		this.goalSelector.addGoal(2, new FollowRulerGoal(this, 0.7, 5.0F, 24.0F));
		this.goalSelector.addGoal(3, new OpenDoorGoal(this, true));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.6) {
			@Override
			public boolean canUse() {
				return getMode() == CitizenMode.WORK && super.canUse();
			}
		});
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5) {
			@Override
			public boolean canUse() {
				return getMode() == CitizenMode.WORK && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, CitizenEntity.class) {
			@Override
			public boolean canUse() {
				return isSoldier() && super.canUse();
			}
		});
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false,
			(target, level) -> isSoldier() && target instanceof Enemy) {
			@Override
			public boolean canUse() {
				return isSoldier() && super.canUse();
			}
		});
	}

	// ---- state -------------------------------------------------------------------------------

	public CitizenJob getJob() {
		return CitizenJob.byId(this.entityData.get(DATA_JOB));
	}

	public CitizenMode getMode() {
		return CitizenMode.byId(this.entityData.get(DATA_MODE));
	}

	public boolean isSoldier() {
		return getJob() == CitizenJob.SOLDIER;
	}

	public VillagerData getLook() {
		return this.entityData.get(DATA_LOOK);
	}

	public @Nullable UUID getRuler() {
		return ruler;
	}

	public int getVillageId() {
		return villageId;
	}

	public @Nullable ServerPlayer getRulerPlayer() {
		if (ruler == null || !(level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		return serverLevel.getServer().getPlayerList().getPlayer(ruler);
	}

	/** Called once when a villager is converted into a citizen. */
	public void swearLoyalty(UUID ruler, ClaimedVillage village, VillagerData look, String name) {
		this.ruler = ruler;
		this.villageId = village.id();
		this.setHomeTo(village.center(), ClaimedVillage.RADIUS);
		this.entityData.set(DATA_LOOK, look);
		this.setCustomName(Component.literal(name));
		this.applyJob(CitizenJob.UNEMPLOYED);
		this.applyMode(CitizenMode.WORK);
		this.syncedWithKingdom = true;
	}

	public void applyJob(CitizenJob job) {
		this.entityData.set(DATA_JOB, job.ordinal());
		this.entityData.set(DATA_LOOK, getLook().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(job.look())));
		if (job == CitizenJob.SOLDIER) {
			if (getMainHandItem().isEmpty()) {
				setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			}
		} else {
			setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			setTarget(null);
		}
	}

	public void applyMode(CitizenMode mode) {
		this.entityData.set(DATA_MODE, mode.ordinal());
		this.getNavigation().stop();
	}

	// ---- behaviour ---------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!syncedWithKingdom && ruler != null) {
			syncedWithKingdom = true;
			syncFromKingdom(level);
		}
	}

	/** Copies orders that were given while this citizen was unloaded. */
	private void syncFromKingdom(ServerLevel level) {
		KingdomManager manager = KingdomManager.get(level.getServer());
		Optional<Kingdom> kingdom = manager.kingdom(ruler);
		if (kingdom.isEmpty()) {
			return;
		}
		Optional<CitizenRecord> record = kingdom.get().citizen(getUUID());
		if (record.isPresent()) {
			if (record.get().job() != getJob()) {
				applyJob(record.get().job());
			}
			if (record.get().mode() != getMode()) {
				applyMode(record.get().mode());
			}
		} else {
			manager.putCitizen(kingdom.get(), toRecord());
		}
		kingdom.get().village(villageId).ifPresent(v -> setHomeTo(v.center(), ClaimedVillage.RADIUS));
	}

	public CitizenRecord toRecord() {
		return new CitizenRecord(getUUID(), getPlainTextName(), getJob(), getMode(), villageId);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (ruler == null || !ruler.equals(player.getUUID())) {
			return super.mobInteract(player, hand);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			if (player.isShiftKeyDown()) {
				CitizenMode mode = getMode() == CitizenMode.FOLLOW ? CitizenMode.STAY : CitizenMode.FOLLOW;
				KingdomNetworking.commandCitizen(serverPlayer, getUUID(), null, mode);
				serverPlayer.sendOverlayMessage(Component.translatable("message.architecturoverse.citizen_mode",
					getDisplayName(), mode.displayName()));
			} else {
				KingdomNetworking.openKingdomScreen(serverPlayer, getUUID());
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		// Never fight the ruler or fellow citizens of the same ruler.
		if (ruler != null && ruler.equals(target.getUUID())) {
			return false;
		}
		if (target instanceof CitizenEntity other && ruler != null && ruler.equals(other.ruler)) {
			return false;
		}
		return super.canAttack(target);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel serverLevel && ruler != null) {
			KingdomManager.get(serverLevel.getServer()).removeCitizen(ruler, getUUID());
			ServerPlayer rulerPlayer = getRulerPlayer();
			if (rulerPlayer != null) {
				rulerPlayer.sendSystemMessage(Component.translatable("message.architecturoverse.citizen_died", getDisplayName()));
			}
		}
		super.die(source);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		// Job tools are handed out for free, so they must not drop.
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	// ---- saving ------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("Look", VillagerData.CODEC, getLook());
		output.store("Job", CitizenJob.CODEC, getJob());
		output.store("Mode", CitizenMode.CODEC, getMode());
		output.putInt("Village", villageId);
		if (ruler != null) {
			output.store("Ruler", UUIDUtil.CODEC, ruler);
		}
		if (hasHome()) {
			output.store("Home", BlockPos.CODEC, getHomePosition());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.read("Look", VillagerData.CODEC).ifPresent(look -> this.entityData.set(DATA_LOOK, look));
		this.entityData.set(DATA_JOB, input.read("Job", CitizenJob.CODEC).orElse(CitizenJob.UNEMPLOYED).ordinal());
		this.entityData.set(DATA_MODE, input.read("Mode", CitizenMode.CODEC).orElse(CitizenMode.WORK).ordinal());
		this.villageId = input.getIntOr("Village", -1);
		this.ruler = input.read("Ruler", UUIDUtil.CODEC).orElse(null);
		input.read("Home", BlockPos.CODEC).ifPresent(home -> setHomeTo(home, ClaimedVillage.RADIUS));
		this.syncedWithKingdom = false;
	}
}
