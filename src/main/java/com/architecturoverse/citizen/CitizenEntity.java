package com.architecturoverse.citizen;

import com.architecturoverse.citizen.goal.ExecutionerGoal;
import com.architecturoverse.citizen.goal.FollowRulerGoal;
import com.architecturoverse.citizen.goal.PrisonerGoal;
import com.architecturoverse.citizen.goal.SleepGoal;
import com.architecturoverse.citizen.goal.SoldierGoal;
import com.architecturoverse.citizen.goal.WorkGoal;
import com.architecturoverse.citizen.work.BuilderAI;
import com.architecturoverse.citizen.work.FarmerAI;
import com.architecturoverse.citizen.work.LumberjackAI;
import com.architecturoverse.citizen.work.MineRoute;
import com.architecturoverse.citizen.work.MinerAI;
import com.architecturoverse.citizen.work.Walker;
import com.architecturoverse.citizen.work.WorkerAI;
import com.architecturoverse.kingdom.CitizenRecord;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.RulerNotifier;
import com.architecturoverse.network.KingdomNetworking;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
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
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A villager that swore loyalty to a ruler. Uses plain goal-based AI instead of the
 * villager brain, so jobs can be added as simple goals.
 */
public class CitizenEntity extends PathfinderMob implements InventoryCarrier {
	private static final EntityDataAccessor<VillagerData> DATA_LOOK =
		SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.VILLAGER_DATA);
	private static final EntityDataAccessor<Integer> DATA_JOB =
		SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MODE =
		SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_ENERGY =
		SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_RESTING =
		SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.BOOLEAN);
	public static final float MAX_ENERGY = 100.0F;
	/** A full night's sleep lasts for about four minutes of work. */
	private static final float TIRE_PER_WORK_TICK = MAX_ENERGY / (20 * 60 * 4);
	/** Sleeping in a bed restores full energy in about a minute. */
	private static final float RECOVER_PER_TICK = MAX_ENERGY / (20 * 60);
	public static final int INVENTORY_SIZE = 12;

	private @Nullable UUID ruler;
	private int villageId = -1;
	private boolean syncedWithKingdom;
	private boolean condemned;
	private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
	private final Walker walker = new Walker(this);
	private @Nullable WorkerAI workerAI;
	private @Nullable CitizenJob workerAIJob;

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
		entityData.define(DATA_ENERGY, MAX_ENERGY);
		entityData.define(DATA_RESTING, false);
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
				return !isSoldier() && !condemned && super.canUse();
			}
		});
		this.goalSelector.addGoal(0, new PrisonerGoal(this));
		this.goalSelector.addGoal(1, new SleepGoal(this));
		this.goalSelector.addGoal(1, new ExecutionerGoal(this));
		this.goalSelector.addGoal(2, new FollowRulerGoal(this, 0.7, 5.0F, 24.0F));
		this.goalSelector.addGoal(3, new WorkGoal(this));
		this.goalSelector.addGoal(3, new SoldierGoal(this));
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

	// ---- sentence ----------------------------------------------------------------------------

	/** Sentenced to death: walks into the prison cell of the military base and waits there. */
	public boolean isCondemned() {
		return condemned;
	}

	public void setCondemned(boolean condemned) {
		this.condemned = condemned;
		if (condemned) {
			applyMode(CitizenMode.WORK);
			this.entityData.set(DATA_RESTING, false);
			if (isSleeping()) {
				stopSleeping();
			}
		}
	}

	// ---- fatigue -----------------------------------------------------------------------------

	public float getEnergy() {
		return this.entityData.get(DATA_ENERGY);
	}

	/** Exhausted and on the way to bed (or asleep) until fully rested. */
	public boolean isResting() {
		return this.entityData.get(DATA_RESTING);
	}

	/** Whether the energy bar is shown: for every worker, and for anybody who is not fully rested. */
	public boolean getsTired() {
		return getJob() != CitizenJob.SOLDIER && (getJob() != CitizenJob.UNEMPLOYED || getEnergy() < MAX_ENERGY);
	}

	/** Called for every tick of work. */
	public void tire() {
		float energy = Math.max(0.0F, getEnergy() - TIRE_PER_WORK_TICK);
		this.entityData.set(DATA_ENERGY, energy);
		if (energy <= 0.0F) {
			this.entityData.set(DATA_RESTING, true);
		}
	}

	/** Called while resting; {@code speed} is 1 in a bed and lower without one. */
	public void recover(float speed) {
		float energy = Math.min(MAX_ENERGY, getEnergy() + RECOVER_PER_TICK * speed);
		this.entityData.set(DATA_ENERGY, energy);
		if (energy >= MAX_ENERGY) {
			this.entityData.set(DATA_RESTING, false);
		}
	}

	/** The energy bar shown under the name of working citizens. */
	@Override
	public @Nullable Component belowNameDisplay() {
		if (!getsTired()) {
			return null;
		}
		int filled = Math.round(getEnergy() / MAX_ENERGY * 10);
		ChatFormatting color = filled > 5 ? ChatFormatting.GREEN : filled > 2 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		MutableComponent bar = Component.empty();
		if (isResting()) {
			bar.append(Component.literal("Zzz ").withStyle(ChatFormatting.AQUA));
		}
		return bar.append(Component.literal("25A0".repeat(filled)).withStyle(color))
			.append(Component.literal("25A0".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY));
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
		setItemSlot(EquipmentSlot.MAINHAND, job.tool());
		if (job != CitizenJob.SOLDIER) {
			setTarget(null);
		}
	}

	// ---- work --------------------------------------------------------------------------------

	@Override
	public SimpleContainer getInventory() {
		return inventory;
	}

	public Walker getWalker() {
		return walker;
	}

	public boolean walkTo(BlockPos target, double reach) {
		Optional<BlockPos> waypoint = getVillage().flatMap(ClaimedVillage::mine)
			.flatMap(mine -> MineRoute.waypoint(mine, level().getMinY(), position(), target));
		if (waypoint.isPresent()) {
			walker.walkTo(waypoint.get(), 1.5);
			return false;
		}
		return walker.walkTo(target, reach);
	}

	/**
	 * The brain for what the citizen does right now, or null when there is nothing to work on
	 * (unemployed, soldier). While the village has a building site, the unemployed and the farmers
	 * join the builders; lumberjacks and miners keep supplying the warehouse.
	 */
	public @Nullable WorkerAI getWorkerAI() {
		CitizenJob role = getJob();
		if ((role == CitizenJob.UNEMPLOYED || role == CitizenJob.FARMER) && getVillage().flatMap(ClaimedVillage::construction).isPresent()) {
			role = CitizenJob.BUILDER;
		}
		if (workerAIJob != role) {
			if (workerAI != null) {
				workerAI.stop();
			}
			workerAIJob = role;
			workerAI = switch (role) {
				case LUMBERJACK -> new LumberjackAI(this);
				case MINER -> new MinerAI(this);
				case FARMER -> new FarmerAI(this);
				case BUILDER -> new BuilderAI(this);
				case UNEMPLOYED, SOLDIER -> null;
			};
		}
		return workerAI;
	}

	public Optional<KingdomManager> getKingdomManager() {
		return level() instanceof ServerLevel serverLevel ? Optional.of(KingdomManager.get(serverLevel.getServer())) : Optional.empty();
	}

	public Optional<Kingdom> getKingdom() {
		return ruler == null ? Optional.empty() : getKingdomManager().flatMap(m -> m.kingdom(ruler));
	}

	public Optional<ClaimedVillage> getVillage() {
		return getKingdom().flatMap(k -> k.village(villageId));
	}

	public Optional<BlockPos> getWarehousePos() {
		return getVillage().flatMap(ClaimedVillage::warehouse);
	}

	/** Tells the ruler about a problem in this citizen's village (rate-limited). */
	public void notifyRuler(String translationKey) {
		ServerPlayer player = getRulerPlayer();
		if (player != null) {
			RulerNotifier.notify(player, villageId, translationKey);
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
			condemned = record.get().condemned();
		} else {
			manager.putCitizen(kingdom.get(), toRecord());
		}
		kingdom.get().village(villageId).ifPresent(v -> setHomeTo(v.center(), ClaimedVillage.RADIUS));
	}

	public CitizenRecord toRecord() {
		return new CitizenRecord(getUUID(), getPlainTextName(), getJob(), getMode(), villageId, condemned);
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
				rulerPlayer.sendSystemMessage(condemned
					? Component.translatable("message.architecturoverse.executed", getDisplayName()).withStyle(ChatFormatting.DARK_RED)
					: Component.translatable("message.architecturoverse.citizen_died", getDisplayName()));
			}
		}
		super.die(source);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		// Job tools are handed out for free, so they must not drop; what the citizen carried does.
		for (ItemStack stack : inventory.removeAllItems()) {
			spawnAtLocation(level, stack);
		}
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
		output.putFloat("Energy", getEnergy());
		output.putBoolean("Resting", isResting());
		output.putBoolean("Condemned", condemned);
		if (ruler != null) {
			output.store("Ruler", UUIDUtil.CODEC, ruler);
		}
		if (hasHome()) {
			output.store("Home", BlockPos.CODEC, getHomePosition());
		}
		writeInventoryToTag(output);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.read("Look", VillagerData.CODEC).ifPresent(look -> this.entityData.set(DATA_LOOK, look));
		this.entityData.set(DATA_JOB, input.read("Job", CitizenJob.CODEC).orElse(CitizenJob.UNEMPLOYED).ordinal());
		this.entityData.set(DATA_MODE, input.read("Mode", CitizenMode.CODEC).orElse(CitizenMode.WORK).ordinal());
		this.villageId = input.getIntOr("Village", -1);
		this.entityData.set(DATA_ENERGY, input.getFloatOr("Energy", MAX_ENERGY));
		this.entityData.set(DATA_RESTING, input.getBooleanOr("Resting", false));
		this.condemned = input.getBooleanOr("Condemned", false);
		this.ruler = input.read("Ruler", UUIDUtil.CODEC).orElse(null);
		input.read("Home", BlockPos.CODEC).ifPresent(home -> setHomeTo(home, ClaimedVillage.RADIUS));
		readInventoryFromTag(input);
		this.syncedWithKingdom = false;
	}
}
