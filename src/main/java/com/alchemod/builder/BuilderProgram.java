package com.alchemod.builder;

public record BuilderProgram(
        String palette,
        Long seed,
        Bounds bounds,
        String buildPlan,
        String code,
        boolean legacyFallback
) {
    public record Bounds(
            AxisBounds x,
            AxisBounds y,
            AxisBounds z
    ) {
    }

    public record AxisBounds(int min, int max) {
    }
}
