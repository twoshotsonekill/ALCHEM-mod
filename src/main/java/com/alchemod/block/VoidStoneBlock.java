package com.alchemod.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;

/**
 * Void Stone — a near-impervious dark stone quarried from pocket dimensions.
 * Harder than reinforced obsidian, with a faint interior glow from trapped void energy.
 * Used by master alchemists to construct permanent laboratory walls.
 */
public class VoidStoneBlock extends Block {

    public VoidStoneBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
