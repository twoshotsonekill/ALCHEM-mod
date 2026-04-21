package com.alchemod.creator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

public class DynamicItem extends Item {

    private final int slotIndex;
    private DynamicItemRegistry.CreatedItemMeta meta;

    public DynamicItem(Settings settings, int slotIndex) {
        super(settings);
        this.slotIndex = slotIndex;
    }

    public void setMeta(DynamicItemRegistry.CreatedItemMeta meta) {
        this.meta = meta;
    }

    public int getSlotIndex() { return slotIndex; }

    @Override
    public Text getName(ItemStack stack) {
        if (meta != null) return Text.literal(meta.name());
        return Text.literal("Unknown Creation");
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
            List<Text> tooltip, TooltipType type) {
        if (meta != null && !meta.description().isBlank()) {
            // Wrap description at ~40 chars
            String desc = meta.description();
            while (desc.length() > 40) {
                int cut = desc.lastIndexOf(' ', 40);
                if (cut < 0) cut = 40;
                tooltip.add(Text.literal("§7" + desc.substring(0, cut)));
                desc = desc.substring(cut).trim();
            }
            if (!desc.isEmpty()) tooltip.add(Text.literal("§7" + desc));
        }
    }
}
