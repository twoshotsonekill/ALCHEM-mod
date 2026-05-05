package com.alchemod.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Alchemical Essence - the primary currency for Alchemical operations.
 * Obtained from breaking Alchemical blocks, completing AI tasks.
 * Used as fuel for Transmuter, Wand, and other blocks.
 * Stackable up to 64, with visual tier indicator (1-5 stars based on stack size).
 */
public class AlchemicalEssenceItem extends Item {

    public AlchemicalEssenceItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        int count = stack.getCount();
        if (count >= 48) return Text.literal("§6§lAlchemical Essence §8[T5]");
        if (count >= 32) return Text.literal("§d§lAlchemical Essence §8[T4]");
        if (count >= 16) return Text.literal("§b§lAlchemical Essence §8[T3]");
        if (count >= 8)  return Text.literal("§a§lAlchemical Essence §8[T2]");
        return Text.literal("§fAlchemical Essence §8[T1]");
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        int count = stack.getCount();
        String stars = getTierStars(count);
        tooltip.add(Text.literal("§7Alchemical currency used for AI operations"));
        tooltip.add(Text.literal("§6Tier: " + stars + " §8(" + count + " essence)"));

        if (count >= 48) {
            tooltip.add(Text.literal("§a§oHigh essence - ready for complex operations!"));
        } else if (count >= 24) {
            tooltip.add(Text.literal("§e§oModerate essence - keep collecting!"));
        } else if (count >= 8) {
            tooltip.add(Text.literal("§6§oLow essence - gather more for best results"));
        } else {
            tooltip.add(Text.literal("§c§oVery low essence - collect more!"));
        }
    }

    private String getTierStars(int count) {
        if (count >= 48) return "§6★★★★★";
        if (count >= 32) return "§d★★★★☆";
        if (count >= 16) return "§b★★★☆☆";
        if (count >= 8)  return "§a★★☆☆☆";
        return "§f★☆☆☆☆";
    }
}
