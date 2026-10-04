package com.architecturoverse.block;

import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/** Placed inside one of the ruler's villages, it becomes that village's warehouse. */
public class WarehouseBlock extends BaseEntityBlock {
	public static final MapCodec<WarehouseBlock> CODEC = simpleCodec(WarehouseBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public WarehouseBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WarehouseBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WarehouseBlockEntity warehouse) {
			player.openMenu(warehouse);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (level instanceof ServerLevel serverLevel && by instanceof ServerPlayer player) {
			register(serverLevel, pos, player);
		}
	}

	/** Makes this block the warehouse of the ruler's village around it. */
	public static boolean register(ServerLevel level, BlockPos pos, ServerPlayer player) {
		KingdomManager manager = KingdomManager.get(level.getServer());
		Optional<ClaimedVillage> village = manager.nearestOwnVillage(player.getUUID(), level.dimension(), pos, ClaimedVillage.RADIUS);
		if (village.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.architecturoverse.warehouse_outside").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Kingdom kingdom = manager.kingdom(player.getUUID()).orElseThrow();
		manager.updateVillage(kingdom, village.get().id(), v -> v.withWarehouse(Optional.of(pos.immutable())));
		player.sendOverlayMessage(Component.translatable("message.architecturoverse.warehouse_set", village.get().id()).withStyle(ChatFormatting.GREEN));
		return true;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		KingdomManager.get(level.getServer()).forgetBlock(level.dimension(), pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
