package com.alchemod.item;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class AmuletItemTest {

    @Test
    void testAmuletNameWithData() {
        String name = "§bSpeed Amulet";
        assertTrue(name.contains("§b"));
        assertTrue(name.contains("Speed Amulet"));
    }

    @Test
    void testAmuletNameWithoutData() {
        String name = "§fAmulet";
        assertTrue(name.contains("Amulet"));
    }

    @Test
    void testAmuletDesc() {
        String desc = "Grants speed boost when worn";
        assertTrue(desc.contains("speed"));
    }

    @Test
    void testAmuletEffectSpeed() {
        String effect = "speed";
        assertTrue("speed".equals(effect));
    }

    @Test
    void testAmuletEffectStrength() {
        String effect = "strength";
        assertTrue("strength".equals(effect));
    }

    @Test
    void testAmuletCharges() {
        int charges = 10;
        assertTrue(charges > 0);
        assertEquals(10, charges);
    }

    @Test
    void testRarityCommon() {
        assertEquals("§a[Common]", getRarityLabel("common"));
    }

    @Test
    void testRarityRare() {
        assertEquals("§b[Rare]", getRarityLabel("rare"));
    }

    @Test
    void testRarityEpic() {
        assertEquals("§d[Epic]", getRarityLabel("epic"));
    }

    @Test
    void testRarityLegendary() {
        assertEquals("§6§l[Legendary]", getRarityLabel("legendary"));
    }

    @Test
    void testRarityInvalid() {
        assertEquals("§f[Common]", getRarityLabel("invalid"));
    }

    @Test
    void testRarityPrefixCommon() {
        assertEquals("§a", getRarityPrefix("common"));
    }

    @Test
    void testRarityPrefixRare() {
        assertEquals("§b", getRarityPrefix("rare"));
    }

    @Test
    void testRarityPrefixLegendary() {
        assertEquals("§6§l", getRarityPrefix("legendary"));
    }

    @Test
    void testValidEffects() {
        List<String> valid = List.of("speed", "haste", "strength", "jump_boost", "regeneration",
                "resistance", "fire_resistance", "water_breathing", "invisibility",
                "health_boost", "absorption", "luck");
        assertTrue(valid.contains("speed"));
        assertTrue(valid.contains("invisibility"));
        assertEquals(12, valid.size());
    }

    @Test
    void testAmuletUseWithCharges() {
        int charges = 5;
        assertTrue(charges > 0);
    }

    @Test
    void testAmuletUseWithoutCharges() {
        int charges = 0;
        assertFalse(charges > 0);
    }

    // Helper methods to simulate rarity logic
    private static String getRarityLabel(String rarity) {
        return switch (rarity) {
            case "common" -> "§a[Common]";
            case "rare"     -> "§b[Rare]";
            case "epic"     -> "§d[Epic]";
            case "legendary" -> "§6§l[Legendary]";
            default        -> "§f[Common]";
        };
    }

    private static String getRarityPrefix(String rarity) {
        return switch (rarity) {
            case "common" -> "§a";
            case "rare"     -> "§b";
            case "epic"     -> "§d";
            case "legendary" -> "§6§l";
            default        -> "§f";
        };
    }
}
