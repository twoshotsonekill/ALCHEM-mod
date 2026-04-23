package com.alchemod.builder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderRuntimeTest {

    @Test
    void rejectsOutOfBoundsPlacements() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                BuilderRuntime.execute(program("block(65, 0, 0, 'stone');", 1), 0, new CollectingSink()));

        assertTrue(error.getMessage().contains("safe build bounds"));
    }

    @Test
    void rejectsDisallowedPaletteBlocks() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                BuilderRuntime.execute(program("block(0, 0, 0, 'water');", 1), 0, new CollectingSink()));

        assertTrue(error.getMessage().contains("palette"));
    }

    @Test
    void enforcesPlacementBudget() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                BuilderRuntime.execute(program("for (let i = 0; i <= 24576; i++) { block(0, 0, 0, 'stone'); }", 1), 0, new CollectingSink()));

        assertTrue(error.getMessage().contains("budget"));
    }

    @Test
    void enforcesSphereRadiusLimit() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                BuilderRuntime.execute(program("sphere(0, 0, 0, 17, 'stone');", 1), 0, new CollectingSink()));

        assertTrue(error.getMessage().contains("radius"));
    }

    @Test
    void rngIsDeterministicForTheSameSeed() {
        CollectingSink first = new CollectingSink();
        CollectingSink second = new CollectingSink();
        String code = """
                var x1 = Math.floor(rng() * 10);
                var z1 = Math.floor(rng() * 10);
                var x2 = Math.floor(rng() * 10);
                var z2 = Math.floor(rng() * 10);
                block(x1, 0, z1, 'stone');
                block(x2, 1, z2, 'glass');
                """;

        BuilderRuntime.ExecutionResult firstResult = BuilderRuntime.execute(program(code, 1234), 0, first);
        BuilderRuntime.ExecutionResult secondResult = BuilderRuntime.execute(program(code, 1234), 999, second);

        assertEquals(1234, firstResult.seedUsed());
        assertEquals(first.placements, second.placements);
    }

    private static BuilderProgram program(String code, Integer seed) {
        return new BuilderProgram(
                BuilderRuntime.PALETTE_NAME,
                seed,
                new BuilderProgram.Bounds(
                        new BuilderProgram.AxisBounds(-BuilderRuntime.MAX_XZ_OFFSET, BuilderRuntime.MAX_XZ_OFFSET),
                        new BuilderProgram.AxisBounds(BuilderRuntime.MIN_Y_OFFSET, BuilderRuntime.MAX_Y_OFFSET),
                        new BuilderProgram.AxisBounds(-BuilderRuntime.MAX_XZ_OFFSET, BuilderRuntime.MAX_XZ_OFFSET)),
                "Test plan",
                code,
                false);
    }

    private static final class CollectingSink implements BuilderRuntime.PlacementSink {
        private final List<String> placements = new ArrayList<>();

        @Override
        public void place(int x, int y, int z, String blockId) {
            placements.add(x + "," + y + "," + z + ":" + blockId);
        }
    }
}
