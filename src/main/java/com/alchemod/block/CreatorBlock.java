package com.alchemod.block;

import com.alchemod.AlchemodInit;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class CreatorBlock extends Block implements BlockEntityProvider {

    public CreatorBlock(AbstractBlock.Settings settings) { super(settings); }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CreatorBlockEntity(pos, state);
    }

    // Block base class returns MODEL already; kept for clarity.
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    /**
     * BUG FIX: Block.createScreenHandlerFactory() returns null by default.
     * We must override it to return the block entity (which implements
     * NamedScreenHandlerFactory). Without this, state.createScreenHandlerFactory()
     * always returns null and the GUI never opens.
     */
    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        return be instanceof NamedScreenHandlerFactory nsf ? nsf : null;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
            PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            // Directly grab the block entity — avoids relying on Block.createScreenHandlerFactory
            // which returns null in the base class and would silently swallow the open call.
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof NamedScreenHandlerFactory factory) {
                player.openHandledScreen(factory);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;
        if (type == AlchemodInit.CREATOR_BE_TYPE) {
            return (BlockEntityTicker<T>) (w, pos, s, be) ->
                    ((CreatorBlockEntity) be).serverTick(w, pos);
        }
        return null;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
            BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof CreatorBlockEntity creator) {
                ItemScatterer.spawn(world, pos, creator);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
