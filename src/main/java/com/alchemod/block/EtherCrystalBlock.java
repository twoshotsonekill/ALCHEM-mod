package com.alchemod.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public class EtherCrystalBlock extends Block {

    public EtherCrystalBlock(AbstractBlock.Settings settings) {
        super(settings.nonOpaque());
    }

    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }
}
