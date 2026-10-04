package com.architecturoverse.citizen.goal;

import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenMode;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Placement;
import com.architecturoverse.structure.StructureType;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The messenger waits at the post office desk. When somebody is sentenced, he takes the paper,
 * carries it to the condemned, hands it over and walks back to the post office.
 */
public class MessengerGoal extends Goal {
	private static final double SEARCH_RADIUS = 128.0;
	/** Condemned citizen -> messenger delivering their sentence. */
	private static final Map<UUID, UUID> DELIVERIES = new HashMap<>();

	private final CitizenEntity messenger;
	private @Nullable CitizenEntity addressee;

	public MessengerGoal(CitizenEntity messenger) {
		this.messenger = messenger;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	private Optional<Placement> postOffice() {
		return messenger.getVillage().flatMap(ClaimedVillage::postOffice);
	}

	@Override
	public boolean canUse() {
		return messenger.getJob() == CitizenJob.MESSENGER && messenger.getStatus() == CitizenStatus.FREE
			&& messenger.getMode() == CitizenMode.WORK && !messenger.isResting() && postOffice().isPresent();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		release();
		messenger.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	@Override
	public void tick() {
		Optional<Placement> office = postOffice();
		if (office.isEmpty()) {
			return;
		}
		if (addressee == null || !addressee.isAlive() || !addressee.isCondemned() || addressee.hasNotice()) {
			release();
			findAddressee();
		}
		if (addressee == null) {
			// Nothing to deliver: wait at the desk.
			messenger.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			messenger.walkTo(office.get().anchor(StructureType.MESSENGER_DESK), 1.5);
			return;
		}
		if (!messenger.getMainHandItem().is(Items.PAPER)) {
			// The sentence is picked up at the post office first.
			if (!messenger.walkTo(office.get().anchor(StructureType.MESSENGER_DESK), 1.5)) {
				return;
			}
			messenger.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.PAPER));
			messenger.level().playSound(null, messenger.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
		messenger.getLookControl().setLookAt(addressee);
		if (messenger.walkTo(addressee.blockPosition(), 2.2)) {
			messenger.swing(InteractionHand.MAIN_HAND);
			messenger.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			addressee.receiveNotice(new ItemStack(Items.PAPER));
			messenger.level().playSound(null, addressee.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0F, 0.8F);
			release();
		}
	}

	private void findAddressee() {
		ServerLevel level = (ServerLevel) messenger.level();
		for (CitizenEntity candidate : level.getEntitiesOfClass(CitizenEntity.class, new AABB(messenger.blockPosition()).inflate(SEARCH_RADIUS),
			c -> c.isAlive() && c.isCondemned() && !c.hasNotice() && c.getVillageId() == messenger.getVillageId()
				&& c.getRuler() != null && c.getRuler().equals(messenger.getRuler()))) {
			UUID current = DELIVERIES.get(candidate.getUUID());
			if (current == null || current.equals(messenger.getUUID())
				|| !(level.getEntity(current) instanceof CitizenEntity other && other.isAlive())) {
				DELIVERIES.put(candidate.getUUID(), messenger.getUUID());
				addressee = candidate;
				return;
			}
		}
	}

	private void release() {
		if (addressee != null) {
			DELIVERIES.remove(addressee.getUUID());
			addressee = null;
		}
	}
}
