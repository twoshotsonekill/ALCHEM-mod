package com.alchemod.builder;

public final class BuilderLocalFallback {

    private BuilderLocalFallback() {
    }

    public static BuilderProgram create(String prompt, int seed, String reason) {
        String safePrompt = prompt == null || prompt.isBlank() ? "requested build" : prompt.trim();
        String plan = "Deterministic local fallback for '" + trim(safePrompt, 72)
                + "': an arcane observatory gate with towers, dome, bridges, stairs, crystals, and layered supports. "
                + "Reason: " + trim(reason, 96);

        String accent = Math.floorMod(seed, 2) == 0 ? "alchemod:ether_crystal" : "glowstone";
        String masonry = Math.floorMod(seed, 3) == 0 ? "deepslate_bricks" : "stone_bricks";
        String code = """
                box(-24,-1,-20,24,0,20,'%s');
                hollowBox(-18,1,-14,18,18,14,'alchemod:arcane_bricks');
                hollowBox(-14,2,-10,14,15,10,'%s');
                box(-7,1,-15,7,13,-12,'alchemod:void_stone');
                hollowBox(-5,1,-16,5,11,-13,'%s');
                line(-4,12,-16,4,12,-16,'%s');
                cylinder(-22,0,-18,4,28,'%s');
                cylinder(22,0,-18,4,28,'%s');
                cylinder(-22,0,18,4,28,'%s');
                cylinder(22,0,18,4,28,'%s');
                dome(0,18,0,12,'alchemod:alchemical_glass');
                sphere(0,31,0,5,'%s');
                stairs(0,1,-22,7,13,'south','%s');
                stairs(0,1,22,7,13,'north','%s');
                line(-22,28,-18,22,28,-18,'%s');
                line(-22,28,18,22,28,18,'%s');
                line(-22,28,-18,-22,28,18,'%s');
                line(22,28,-18,22,28,18,'%s');
                for(i=-2;i<=2;i=i+1){pillar(i*4,19,-14,7,'%s');}
                for(i=-2;i<=2;i=i+1){pillar(i*4,19,14,7,'%s');}
                for(i=-3;i<=3;i=i+1){block(i*6,1,-21,'%s'); block(i*6,1,21,'%s');}
                for(i=0;i<4;i=i+1){sphere(-12+i*8,8,-18,2,'%s'); sphere(-12+i*8,8,18,2,'%s');}
                """.formatted(
                masonry,
                masonry,
                masonry,
                accent,
                masonry,
                masonry,
                masonry,
                masonry,
                accent,
                masonry,
                masonry,
                accent,
                accent,
                accent,
                accent,
                accent,
                accent,
                accent,
                accent,
                accent,
                accent);

        return new BuilderProgram(
                BuilderRuntime.PALETTE_NAME,
                (long) seed,
                safeBounds(),
                plan,
                code,
                false);
    }

    public static BuilderProgram.Bounds safeBounds() {
        return new BuilderProgram.Bounds(
                new BuilderProgram.AxisBounds(-BuilderRuntime.MAX_XZ_OFFSET, BuilderRuntime.MAX_XZ_OFFSET),
                new BuilderProgram.AxisBounds(BuilderRuntime.MIN_Y_OFFSET, BuilderRuntime.MAX_Y_OFFSET),
                new BuilderProgram.AxisBounds(-BuilderRuntime.MAX_XZ_OFFSET, BuilderRuntime.MAX_XZ_OFFSET));
    }

    private static String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
