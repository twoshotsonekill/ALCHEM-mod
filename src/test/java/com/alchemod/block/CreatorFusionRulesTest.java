package com.alchemod.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatorFusionRulesTest {

    @Test
    void tntAndBowMakeExplosiveBowWithScriptedAbility() {
        CreatorFusionRules.Fusion fusion = CreatorFusionRules.match(
                "TNT (minecraft:tnt)",
                "Bow (minecraft:bow)");

        assertNotNull(fusion);
        assertEquals("Explosive Bow", fusion.name());
        assertEquals("bow", fusion.itemType());
        assertEquals("ignite", fusion.special());
        assertTrue(fusion.behaviorScript().contains("world.createExplosion"));
        assertTrue(fusion.behaviorScript().contains("player.sendMessage"));
    }

    @Test
    void dirtAndIronMakeLandmineBlockWithScriptedAbility() {
        CreatorFusionRules.Fusion fusion = CreatorFusionRules.match(
                "Dirt (minecraft:dirt)",
                "Iron Ingot (minecraft:iron_ingot)");

        assertNotNull(fusion);
        assertEquals("Landmine Block", fusion.name());
        assertEquals("block", fusion.itemType());
        assertEquals("ignite", fusion.special());
        assertTrue(fusion.behaviorScript().contains("world.setBlock"));
        assertTrue(fusion.behaviorScript().contains("minecraft:tnt"));
        assertTrue(fusion.behaviorScript().contains("minecraft:stone_pressure_plate"));
    }
}
