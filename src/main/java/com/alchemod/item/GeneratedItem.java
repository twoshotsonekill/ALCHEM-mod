package com.alchemod.item;

import net.minecraft.item.Item;

/**
 * Runtime-created generated item. It intentionally reuses OddityItem behavior:
 * identity, scripts, charges, and rendering data all live in stack NBT, so a
 * failed runtime registration can fall back to the pre-registered Oddity item
 * without changing behavior.
 */
public class GeneratedItem extends OddityItem {

    private final String generatedId;
    private final String itemType;

    public GeneratedItem(Item.Settings settings, String generatedId, String itemType) {
        super(settings);
        this.generatedId = generatedId;
        this.itemType = itemType;
    }

    public String generatedId() {
        return generatedId;
    }

    public String itemType() {
        return itemType;
    }
}
