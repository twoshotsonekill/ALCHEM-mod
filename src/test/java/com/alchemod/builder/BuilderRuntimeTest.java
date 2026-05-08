package com.alchemod.builder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void executesNewPrimitivesAndSimpleLoops() {
        String code = """
                hollowBox(-4, 0, -4, 4, 6, 4, 'stone_bricks');
                cylinder(0, 0, 0, 3, 8, 'cobblestone');
                dome(0, 8, 0, 4, 'glass');
                for (let i = -2; i <= 2; i++) { pillar(i, 0, -6, 5, 'oak_log'); }
                stairs(0, 0, 8, 5, 'north', 'stone');
                """;
        BuilderRuntime.PlacementPreview preview = BuilderRuntime.preview(program(code, 99), 0);

        assertTrue(preview.placementCount() > 320);
        assertTrue(preview.primitiveVariety() >= 5);
    }

    @Test
    void executeDoesNotReplayPartialPreviewWhenValidationFails() {
        CollectingSink sink = new CollectingSink();

        assertThrows(IllegalArgumentException.class, () ->
                BuilderRuntime.execute(program("""
                        block(0, 0, 0, 'stone');
                        block(1, 0, 0, 'water');
                        """, 1), 0, sink));

        assertTrue(sink.placements.isEmpty());
    }

    @Test
    void qualityGateRejectsTinySingleShapeBuilds() {
        BuilderRuntime.PlacementPreview preview = BuilderRuntime.preview(
                program("block(0, 0, 0, 'stone');", 1), 0);

        BuilderRuntime.QualityReport quality = BuilderRuntime.assessQuality(preview);

        assertFalse(quality.accepted());
        assertTrue(quality.reason().contains("placements"));
    }

    @Test
    void deterministicLocalFallbackPassesQualityGate() {
        BuilderProgram fallback = BuilderLocalFallback.create("arcane observatory", 123, "test");
        BuilderRuntime.PlacementPreview preview = BuilderRuntime.preview(fallback, 0);

        assertTrue(BuilderRuntime.assessQuality(preview).accepted());
        assertTrue(preview.placementCount() > 1000);
        assertTrue(preview.primitiveVariety() >= 2);
    }

    private static BuilderProgram program(String code, long seed) {
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
