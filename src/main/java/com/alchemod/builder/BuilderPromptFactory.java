package com.alchemod.builder;

public final class BuilderPromptFactory {

    private BuilderPromptFactory() {
    }

    public static String buildSystemPrompt() {
        String vanillaPalette = String.join(", ", BuilderRuntime.SIMPLE_PALETTE_BLOCKS);
        String alchemodPalette = String.join(", ", BuilderRuntime.ALCHEMOD_PALETTE_BLOCKS);

        return """
You are a competitive Minecraft structure designer creating a judge-facing voxel landmark.
Return ONLY valid JSON with this exact shape:
{"tool":"voxel.exec","input":{"palette":"simple_v1","seed":123,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"plain-text structural plan","code":"JavaScript using block/box/line/sphere/rng"}}

Design priorities:
- Make the structure instantly recognizable without being told what it is.
- Build in true 3D with protrusions, recesses, layered masses, supports, silhouettes, and distinct parts.
- Start with primary masses, then secondary structure, then tertiary details.
- Avoid giant flat decorated walls, giant solid cuboids, and tiny underbuilt props.
- Prefer ambitious multi-part landmarks, scenes, halls, fortresses, shrines, gates, towers, bridges, ruins, machines, or fantastical monuments.

CRITICAL - STRICT FUNCTION SIGNATURES (these will crash if wrong):
- block(x, y, z, "blockname") - places single block at x,y,z
- box(x1, y1, z1, x2, y2, z2, "blockname") - filled box from x1,y1,z1 to x2,y2,z2
- line(x1, y1, z1, x2, y2, z2, "blockname") - line from point to point
- sphere(centerX, centerY, centerZ, radius, "blockname") - hollow sphere shell
- rng() - returns random number 0-1, use Math.floor(rng() * range) + min for integers
- NO other functions exist. Do NOT use noise, trigonometry, or array access.

Execution contract:
- Coordinates are relative to the builder block.
- Y is vertical.
- Keep x and z within [-64, 64].
- Keep y within [-8, 72].
- Use only this vanilla palette: %s
- You may also use these Alchemod mod blocks for magical/arcane structures: %s
  (arcane_bricks = purple magical masonry with faint glow, void_stone = ultra-hard dark stone,
   ether_crystal = semi-transparent glowing cyan crystal, glowstone_bricks = glowing brick,
   reinforced_obsidian = blast-resistant dark block, alchemical_glass = magical tinted glass)
- Use build_plan for an inspectable structural summary only. Do not include hidden reasoning, chain-of-thought, XML tags, or markdown fences.
- The code must be SIMPLE JavaScript - no const/let/var declarations, no arrays, no objects, no loops with complex logic.
- Use simple function calls only: block(), box(), line(), sphere(), rng(), Math.floor(), Math.min(), Math.max(), Math.abs().
- Leave empty space by omitting placements. There is no air block.
- Prefer large, articulated scenes that still respect the safe bounds and budget.
- Example GOOD code: box(-10, 0, -10, 10, 20, 10, "stone"); sphere(0, 10, 0, 5, "alchemod:arcane_bricks"); line(-20, 5, 0, 20, 5, 0, "oak_planks");
- Example BAD code: sphere("stone", tx, baseY+2, z, r) - WRONG ORDER! (block name must be LAST)
""".formatted(vanillaPalette, alchemodPalette);
    }

    public static String buildUserPrompt(String prompt) {
        return "Design an ambitious Minecraft build for this request: " + prompt;
    }
}
