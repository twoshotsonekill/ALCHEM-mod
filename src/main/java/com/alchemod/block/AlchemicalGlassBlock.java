package com.alchemod.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

public class AlchemicalGlassBlock extends Block {

    public AlchemicalGlassBlock(AbstractBlock.Settings settings) {
        super(settings.nonOpaque());
    }

    public boolean isTransparent(BlockState state, BlockView world, net.minecraft.util.math.BlockPos pos) {
        return true;
    }
}
