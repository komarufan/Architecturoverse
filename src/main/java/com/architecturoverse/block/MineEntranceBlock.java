package com.architecturoverse.block;

import com.architecturoverse.kingdom.ClaimedVillage;
import com.architecturoverse.kingdom.Kingdom;
import com.architecturoverse.kingdom.KingdomManager;
import com.architecturoverse.kingdom.MineSite;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.Nullable;

/**
 * Marks where the village miners start digging. The doorway faces the player who placed
 * it, and the staircase goes down the other way, i.e. the way the player was looking.
 */
public class MineEntranceBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<MineEntranceBlock> CODEC = simpleCodec(MineEntranceBlock::new);

	public MineEntranceBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (level instanceof ServerLevel serverLevel && by instanceof ServerPlayer player) {
			register(serverLevel, pos, state.getValue(FACING).getOpposite(), player);
		}
	}

	/** Makes this the mine of the ruler's nearest village. */
	public static boolean register(ServerLevel level, BlockPos pos, Direction facing, ServerPlayer player) {
		KingdomManager manager = KingdomManager.get(level.getServer());
		Optional<ClaimedVillage> village = manager.nearestOwnVillage(player.getUUID(), level.dimension(), pos, ClaimedVillage.MINE_RANGE);
		if (village.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.architecturoverse.mine_outside", ClaimedVillage.MINE_RANGE)
				.withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Kingdom kingdom = manager.kingdom(player.getUUID()).orElseThrow();
		manager.updateVillage(kingdom, village.get().id(), v -> v.withMine(Optional.of(new MineSite(pos.immutable(), facing, 0))));
		player.sendOverlayMessage(Component.translatable("message.architecturoverse.mine_set", village.get().id()).withStyle(ChatFormatting.GREEN));
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
