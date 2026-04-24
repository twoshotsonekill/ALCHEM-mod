package com.alchemod.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;

/**
 * Reinforced Obsidian - An ultra-durable, blast-resistant block for protecting
 * valuable alchemical setups. Stronger than regular obsidian with high explosion resistance.
 */
public class ReinforcedObsidianBlock extends Block {

    public ReinforcedObsidianBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
