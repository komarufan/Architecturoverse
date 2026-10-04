package com.architecturoverse.citizen.work;

import com.architecturoverse.block.MineEntranceBlock;
import com.architecturoverse.block.WarehouseBlock;
import com.architecturoverse.citizen.CitizenEntity;
import com.architecturoverse.kingdom.BuildOrder;
import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.MineSite;
import com.architecturoverse.registry.ModBlocks;
import com.architecturoverse.village.SiteFinder;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Works through the village's build orders: picks a site, walks there and puts the building up. */
public class BuilderAI extends WorkerAI {
	private static final int BUILD_TICKS = 100;

	private @Nullable BuildOrder order;
	private SiteFinder.@Nullable Site site;
	private int buildProgress;

	public BuilderAI(CitizenEntity citizen) {
		super(citizen);
	}

	@Override
	public boolean hasWork() {
		return citizen.getVillage().map(v -> !v.orders().isEmpty()).orElse(false);
	}

	@Override
	public void tick() {
		Optional<ClaimedVillage> village = citizen.getVillage();
		if (village.isEmpty() || village.get().orders().isEmpty()) {
			return;
		}
		BuildOrder next = village.get().orders().getFirst();
		if (next != order) {
			order = next;
			site = null;
			buildProgress = 0;
		}
		ServerLevel level = level();
		if (site == null || !SiteFinder.isBuildable(level, site.pos())) {
			buildProgress = 0;
			site = findSite(level, village.get());
			if (site == null) {
				finish(village.get(), Component.translatable("message.architecturoverse.no_site", order.displayName(), village.get().id())
					.withStyle(ChatFormatting.RED));
				return;
			}
		}
		if (!walkTo(site.pos(), 3.0)) {
			return;
		}
		citizen.getLookControl().setLookAt(Vec3.atCenterOf(site.pos()));
		if (buildProgress % 10 == 0) {
			citizen.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, site.pos(), SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 1.0F);
			level.sendParticles(ParticleTypes.CLOUD, site.pos().getX() + 0.5, site.pos().getY() + 0.5, site.pos().getZ() + 0.5,
				4, 0.3, 0.3, 0.3, 0.01);
		}
		if (++buildProgress >= BUILD_TICKS) {
			build(level, village.get(), site);
		}
	}

	private SiteFinder.@Nullable Site findSite(ServerLevel level, ClaimedVillage village) {
		return switch (order) {
			case WAREHOUSE -> SiteFinder.warehouse(level, village.center()).orElse(null);
			case MINE -> SiteFinder.mine(level, village.center()).orElse(null);
			case null -> null;
		};
	}

	private void build(ServerLevel level, ClaimedVillage village, SiteFinder.Site where) {
		BlockPos pos = where.pos();
		BlockState state = switch (order) {
			case WAREHOUSE -> ModBlocks.WAREHOUSE.defaultBlockState().setValue(WarehouseBlock.FACING, where.facing());
			case MINE -> ModBlocks.MINE_ENTRANCE.defaultBlockState().setValue(MineEntranceBlock.FACING, where.facing());
			case null -> null;
		};
		if (state == null) {
			return;
		}
		level.setBlockAndUpdate(pos, state);
		level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.2F);
		BuildOrder done = order;
		citizen.getKingdomManager().ifPresent(manager -> citizen.getKingdom().ifPresent(kingdom ->
			manager.updateVillage(kingdom, village.id(), v -> switch (done) {
				case WAREHOUSE -> v.withWarehouse(Optional.of(pos));
				// The doorway faces the village, the miners dig the other way.
				case MINE -> v.withMine(Optional.of(new MineSite(pos, where.facing().getOpposite(), 0)));
			})));
		finish(village, Component.translatable("message.architecturoverse.built", citizen.getDisplayName(), done.displayName(), village.id())
			.withStyle(ChatFormatting.GREEN));
	}

	/** Removes the current order from the queue and tells the ruler how it went. */
	private void finish(ClaimedVillage village, Component report) {
		BuildOrder done = order;
		order = null;
		site = null;
		buildProgress = 0;
		citizen.getKingdomManager().ifPresent(manager -> citizen.getKingdom().ifPresent(kingdom ->
			manager.updateVillage(kingdom, village.id(), v -> v.withoutOrder(done))));
		ServerPlayer ruler = citizen.getRulerPlayer();
		if (ruler != null) {
			ruler.sendSystemMessage(report);
		}
	}
}
