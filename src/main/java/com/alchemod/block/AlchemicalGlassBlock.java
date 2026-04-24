package com.alchemod.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlideBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

/**
 * Alchemical Glass - A decorative, transparent block with magical properties.
 * Allows light to pass through and has a slight luminance for aesthetic appeal.
 */
public class AlchemicalGlassBlock extends TransparentBlock {

    public AlchemicalGlassBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.TRANSLUCENT;
    }

    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, net.minecraft.util.math.BlockPos pos) {
        return 1.0f;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, net.minecraft.util.math.BlockPos pos) {
        return true;
    }
}
