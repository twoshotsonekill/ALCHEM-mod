package com.alchemod.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;

/**
 * Arcane Bricks — a purple magical brick block.
 * Crafted by alchemists to construct ritual chambers, forge rooms,
 * and magical enclosures. Radiates a faint magical luminance.
 */
public class ArcaneBricksBlock extends Block {

    public ArcaneBricksBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
