package com.alchemod.item;

import com.alchemod.creator.DynamicItemRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedItemSchemaTest {

    @Test
    void labelsExpandedGeneratedItemTypes() {
        assertTrue(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel("tool").contains("Tool"));
        assertTrue(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel("potion").contains("Potion"));
        assertTrue(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel("weapon").contains("Weapon"));
        assertTrue(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel("wand").contains("Wand"));
        assertTrue(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel("charm").contains("Charm"));
        assertTrue(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel("scroll").contains("Scroll"));
        assertTrue(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel("throwable").contains("Throwable"));
        assertTrue(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel("spawn_item").contains("Spawn"));
        assertTrue(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel("artifact").contains("Artifact"));
    }

    @Test
    void generatedMetadataCarriesScriptsAndCharges() {
        DynamicItemRegistry.CreatedItemMeta meta = new DynamicItemRegistry.CreatedItemMeta(
                "Test Wand",
                "A test generated wand.",
                123,
                "legendary",
                "wand",
                List.of("speed", "luck"),
                "lightning",
                null,
                "function onUse(player, world) { player.sendMessage('zap'); }");

        assertTrue(meta.hasScript());
        assertEquals(10, meta.startingCharges());
        assertEquals("§7Generated Wand", DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel(meta.itemType()));
    }
}
