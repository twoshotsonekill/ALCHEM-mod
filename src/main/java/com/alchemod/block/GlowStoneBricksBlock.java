package com.alchemod.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;

/**
 * Glowstone Bricks - A decorative, light-emitting stone variant.
 * Combines the aesthetic of stone bricks with the luminance of glowstone,
 * making it ideal for magical lighting in alchemical structures.
 */
public class GlowStoneBricksBlock extends Block {

    public GlowStoneBricksBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
