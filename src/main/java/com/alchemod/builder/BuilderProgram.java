package com.alchemod.builder;

public record BuilderProgram(
        String palette,
        Integer seed,
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
