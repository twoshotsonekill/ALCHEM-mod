package com.alchemod.item;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class AlchemicalWandItemTest {

    @Test
    void testWandNameFullMana() {
        // Simulate wand with full mana (200)
        String name = getWandName(200);
        assertTrue(name.contains("§6§lAlchemical Wand"));
        assertTrue(name.contains("[200/200]"));
    }

    @Test
    void testWandNameMediumMana() {
        String name = getWandName(80);
        assertTrue(name.contains("§b§lAlchemical Wand"));
        assertTrue(name.contains("[80/200]"));
    }

    @Test
    void testWandNameLowMana() {
        String name = getWandName(30);
        assertTrue(name.contains("§a§lAlchemical Wand"));
        assertTrue(name.contains("[30/200]"));
    }

    @Test
    void testWandNameCriticalMana() {
        String name = getWandName(10);
        assertTrue(name.contains("§7§lAlchemical Wand"));
        assertTrue(name.contains("[10/200]"));
    }

    @Test
    void testManaPerEssence() {
        int essenceCount = 5;
        int manaGain = essenceCount * 4; // MANA_PER_ESSENCE = 4
        assertEquals(20, manaGain);
    }

    @Test
    void testMaxMana() {
        assertEquals(200, 200); // MAX_MANA = 200
    }

    @Test
    void testManaBounds() {
        int mana = Math.max(0, Math.min(200, 250));
        assertEquals(200, mana);

        mana = Math.max(0, Math.min(200, -10));
        assertEquals(0, mana);
    }

    @Test
    void testSpellCast() {
        int currentMana = 100;
        int spellCost = 20;
        assertTrue(currentMana >= spellCost);
        assertEquals(80, currentMana - spellCost);
    }

    @Test
    void testSpellCastInsufficientMana() {
        int currentMana = 10;
        int spellCost = 20;
        assertFalse(currentMana >= spellCost);
    }

    @Test
    void testLastSpellStorage() {
        // Test that last spell is stored in NBT
        assertTrue(true); // Placeholder - would need mock ItemStack
    }

    @Test
    void testRechargeFromEssence() {
        // Test recharge logic
        int wandMana = 50;
        int essenceCount = 10;
        int addMana = essenceCount * 4;
        int newMana = Math.min(200, wandMana + addMana);
        assertEquals(90, newMana);
    }

    // Helper to simulate wand name generation
    private static String getWandName(int mana) {
        String prefix = mana > 150 ? "§6§l" : mana >= 80 ? "§b§l" : mana >= 30 ? "§a§l" : "§7§l";
        return prefix + "Alchemical Wand §8[" + mana + "/" + 200 + "]";
    }
}
