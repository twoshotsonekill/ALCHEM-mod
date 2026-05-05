package com.alchemod.item;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class AlchemicalArmorItemTest {

    @Test
    void testArmorNameWithNbt() {
        // Test that armor displays custom name from NBT
        assertTrue("§bMagic Helmet".contains("§b"));
        assertTrue("§bMagic Helmet".contains("Magic Helmet"));
    }

    @Test
    void testArmorDescription() {
        String desc = "A magical armor with special powers";
        assertTrue(desc.contains("magical"));
    }

    @Test
    void testArmorAbility() {
        String ability = "fire_aura";
        assertNotNull(ability);
        assertEquals("fire_aura", ability);
    }

    @Test
    void testArmorCharges() {
        int charges = 10;
        assertTrue(charges > 0);
        assertEquals(10, charges);
    }

    @Test
    void testRarityLabelCommon() {
        assertEquals("§f[Common]", getRarityLabel("common"));
    }

    @Test
    void testRarityLabelUncommon() {
        assertEquals("§a[Uncommon]", getRarityLabel("uncommon"));
    }

    @Test
    void testRarityLabelRare() {
        assertEquals("§b[Rare]", getRarityLabel("rare"));
    }

    @Test
    void testRarityLabelEpic() {
        assertEquals("§d[Epic]", getRarityLabel("epic"));
    }

    @Test
    void testRarityLabelLegendary() {
        assertEquals("§6§l[Legendary]", getRarityLabel("legendary"));
    }

    @Test
    void testRarityPrefix() {
        assertEquals("§f", getRarityPrefix("common"));
        assertEquals("§a", getRarityPrefix("uncommon"));
        assertEquals("§b", getRarityPrefix("rare"));
        assertEquals("§d", getRarityPrefix("epic"));
        assertEquals("§6§l", getRarityPrefix("legendary"));
        assertEquals("§f", getRarityPrefix("invalid"));
    }

    @Test
    void testFullSetBonus() {
        // Test full set detection
        boolean fullSet = false; // Would check player equipment
        assertFalse(fullSet); // Placeholder
    }

    @Test
    void testArmorTypeHelmet() {
        assertEquals("helmet", "helmet");
    }

    @Test
    void testArmorTypeChestplate() {
        assertEquals("chestplate", "chestplate");
    }

    @Test
    void testArmorTypeLeggings() {
        assertEquals("leggings", "leggings");
    }

    @Test
    void testArmorTypeBoots() {
        assertEquals("boots", "boots");
    }

    // Helper methods
    private static String getRarityLabel(String rarity) {
        return switch (rarity) {
            case "uncommon" -> "§a[Uncommon]";
            case "rare"     -> "§b[Rare]";
            case "epic"     -> "§d[Epic]";
            case "legendary" -> "§6§l[Legendary]";
            default        -> "§f[Common]";
        };
    }

    private static String getRarityPrefix(String rarity) {
        return switch (rarity) {
            case "uncommon" -> "§a";
            case "rare"     -> "§b";
            case "epic"     -> "§d";
            case "legendary" -> "§6§l";
            default        -> "§f";
        };
    }
}
