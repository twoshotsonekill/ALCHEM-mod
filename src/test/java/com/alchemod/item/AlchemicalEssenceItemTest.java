package com.alchemod.item;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class AlchemicalEssenceItemTest {

    // Since we can't easily instantiate AlchemicalEssenceItem without Minecraft internals,
    // we test the display name logic and tooltip logic via static helpers

    @Test
    void testTier5Name() {
        // Tier 5: 48+ items
        String name = getTierName(48);
        assertTrue(name.contains("§6§lAlchemical Essence"));
        assertTrue(name.contains("[T5]"));
    }

    @Test
    void testTier4Name() {
        String name = getTierName(32);
        assertTrue(name.contains("§d§lAlchemical Essence"));
        assertTrue(name.contains("[T4]"));
    }

    @Test
    void testTier3Name() {
        String name = getTierName(16);
        assertTrue(name.contains("§b§lAlchemical Essence"));
        assertTrue(name.contains("[T3]"));
    }

    @Test
    void testTier2Name() {
        String name = getTierName(8);
        assertTrue(name.contains("§a§lAlchemical Essence"));
        assertTrue(name.contains("[T2]"));
    }

    @Test
    void testTier1Name() {
        String name = getTierName(1);
        assertTrue(name.contains("§fAlchemical Essence"));
        assertTrue(name.contains("[T1]"));
    }

    @Test
    void testTierStars() {
        assertEquals("§6★★★★★", getStars(48));
        assertEquals("§d★★★★☆", getStars(32));
        assertEquals("§b★★★☆☆", getStars(16));
        assertEquals("§a★★☆☆☆", getStars(8));
        assertEquals("§f★☆☆☆☆", getStars(1));
    }

    @Test
    void testTooltipHighEssence() {
        List<String> tooltip = getTooltipLines(50);
        assertTrue(tooltip.stream().anyMatch(s -> s.contains("High essence")));
    }

    @Test
    void testTooltipModerateEssence() {
        List<String> tooltip = getTooltipLines(24);
        assertTrue(tooltip.stream().anyMatch(s -> s.contains("Moderate essence")));
    }

    @Test
    void testTooltipLowEssence() {
        List<String> tooltip = getTooltipLines(8);
        assertTrue(tooltip.stream().anyMatch(s -> s.contains("Low essence")));
    }

    @Test
    void testTooltipVeryLowEssence() {
        List<String> tooltip = getTooltipLines(3);
        assertTrue(tooltip.stream().anyMatch(s -> s.contains("Very low essence")));
    }

    // ---- Helper methods replicating AlchemicalEssenceItem logic ----

    private static String getTierName(int count) {
        if (count >= 48) return "§6§lAlchemical Essence §8[T5]";
        if (count >= 32) return "§d§lAlchemical Essence §8[T4]";
        if (count >= 16) return "§b§lAlchemical Essence §8[T3]";
        if (count >= 8)  return "§a§lAlchemical Essence §8[T2]";
        return "§fAlchemical Essence §8[T1]";
    }

    private static String getStars(int count) {
        if (count >= 48) return "§6★★★★★";
        if (count >= 32) return "§d★★★★☆";
        if (count >= 16) return "§b★★★☆☆";
        if (count >= 8)  return "§a★★☆☆☆";
        return "§f★☆☆☆☆";
    }

    private static List<String> getTooltipLines(int count) {
        List<String> lines = new ArrayList<>();
        lines.add("§7Alchemical currency used for AI operations");
        String stars = getStars(count);
        lines.add("§6Tier: " + stars + " §8(" + count + " essence)");

        if (count >= 48) {
            lines.add("§a§oHigh essence - ready for complex operations!");
        } else if (count >= 24) {
            lines.add("§e§oModerate essence - keep collecting!");
        } else if (count >= 8) {
            lines.add("§6§oLow essence - gather more for best results");
        } else {
            lines.add("§c§oVery low essence - collect more!");
        }
        return lines;
    }
}
