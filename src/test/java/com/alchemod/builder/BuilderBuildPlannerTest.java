package com.alchemod.builder;

import com.alchemod.ai.OpenRouterClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderBuildPlannerTest {

    @Test
    void repairRetryIsUsedForTinyPrimaryOutput() {
        String tiny = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":1,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Too small.","code":"block(0,0,0,'stone');"}}
                """;
        String repaired = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":2,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Repaired tower with shell and dome.","code":"hollowBox(-8,0,-8,8,18,8,'stone_bricks'); dome(0,18,0,8,'glass'); pillar(-10,0,-10,20,'cobblestone');"}}
                """;

        BuilderBuildPlanner.BuildResult result = BuilderBuildPlanner.plan(
                "tower",
                tiny,
                null,
                99,
                (system, user) -> new OpenRouterClient.ChatResult(repaired, repaired, null));

        assertTrue(result.ok());
        assertEquals(BuilderDiagnostics.STATUS_REPAIRED, result.diagnostics().parseStatus());
        assertTrue(result.diagnostics().repairAttempted());
        assertFalse(result.program().legacyFallback());
    }

    @Test
    void fallsBackLocallyWhenPrimaryAndRepairAreInvalid() {
        BuilderBuildPlanner.BuildResult result = BuilderBuildPlanner.plan(
                "castle",
                "not json",
                null,
                42,
                (system, user) -> new OpenRouterClient.ChatResult("still invalid", "still invalid", null));

        assertTrue(result.ok());
        assertEquals(BuilderDiagnostics.STATUS_FALLBACK, result.diagnostics().parseStatus());
        assertTrue(result.diagnostics().repairAttempted());
        assertNotNull(result.program());
        assertTrue(result.preview().placementCount() > 1000);
    }

    @Test
    void apiFailureFallsBackWithoutRepair() {
        BuilderBuildPlanner.BuildResult result = BuilderBuildPlanner.plan(
                "bridge",
                "",
                "OPENROUTER_API_KEY not set",
                7,
                (system, user) -> {
                    throw new AssertionError("repair should not be called after a primary request error");
                });

        assertTrue(result.ok());
        assertEquals(BuilderDiagnostics.STATUS_FALLBACK, result.diagnostics().parseStatus());
        assertFalse(result.diagnostics().repairAttempted());
    }
}
