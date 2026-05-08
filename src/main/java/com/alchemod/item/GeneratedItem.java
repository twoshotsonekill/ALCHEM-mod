package com.alchemod.item;

import net.minecraft.item.Item;

/**
 * Runtime-created generated item. It intentionally reuses OddityItem behavior:
 * identity, scripts, charges, and rendering data all live in stack NBT. The
 * creator now requires this runtime item to register successfully; failures
 * abort creation instead of emitting a template fallback.
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
