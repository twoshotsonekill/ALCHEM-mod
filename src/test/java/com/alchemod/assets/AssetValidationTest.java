package com.alchemod.assets;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetValidationTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final List<String> REGISTERED_BLOCKS = List.of(
            "alchemical_forge",
            "item_creator",
            "build_creator",
            "alchemical_glass",
            "reinforced_obsidian",
            "glowstone_bricks",
            "arcane_bricks",
            "void_stone",
            "ether_crystal");

    @Test
    void everyRegisteredBlockHasRequiredAssets() {
        for (String block : REGISTERED_BLOCKS) {
            assertExists("blockstate", "assets/alchemod/blockstates/" + block + ".json");
            assertExists("block model", "assets/alchemod/models/block/" + block + ".json");
            assertExists("item definition", "assets/alchemod/items/" + block + ".json");
            assertExists("item model", "assets/alchemod/models/item/" + block + ".json");
            assertExists("block texture", "assets/alchemod/textures/block/" + block + ".png");
            assertExists("loot table", "data/alchemod/loot_table/blocks/" + block + ".json");
        }
    }

    private static void assertExists(String kind, String relativePath) {
        Path path = RESOURCES.resolve(relativePath);
        assertTrue(Files.exists(path), () -> "Missing " + kind + ": " + relativePath);
    }
}
