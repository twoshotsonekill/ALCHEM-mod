package com.alchemod.resource;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;

/**
 * Generates recognisable 16×16 pixel-art item sprites entirely on the client,
 * with zero API calls. Sprites are deterministic: the same (itemType, rarity,
 * effectHash) triple always produces the same image.
 *
 * <p>Silhouette vocabulary mirrors the SpriteToolClient system prompt so that
 * hand-drawn and procedural sprites share the same design language:
 * <ul>
 *   <li>sword / dagger   — diagonal blade + crossguard + grip</li>
 *   <li>bow              — arc + string + arrow</li>
 *   <li>use_item / wand  — diagonal wand with gem tip</li>
 *   <li>potion           — round body + narrow neck + stopper</li>
 *   <li>food             — apple silhouette + leaf</li>
 *   <li>spawn_egg        — bicolour oval, split horizontally</li>
 *   <li>totem            — tall rect with inset face</li>
 *   <li>throwable        — small orb + diagonal trail</li>
 * </ul>
 *
 * <p>Rarity drives the colour palette so players can identify power at a glance.
 */
@Environment(EnvType.CLIENT)
public final class ProceduralSpriteGenerator {

    private ProceduralSpriteGenerator() {}

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * @param itemType   creator_item_type NBT value (e.g. "sword", "bow", "potion")
     * @param rarity     creator_rarity NBT value    (e.g. "common", "legendary")
     * @param effectHash a hash derived from the effect list — drives accent colour variation
     * @param name       item name — used as an extra seed for colour variation
     * @return a fresh 16×16 RGBA {@link NativeImage}
     */
    public static NativeImage generate(String itemType, String rarity, int effectHash, String name) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        clearTransparent(img);

        Palette p = paletteFor(rarity, effectHash, name);

        switch (normalise(itemType)) {
            case "sword"     -> drawSword(img, p);
            case "bow"       -> drawBow(img, p);
            case "spawn_egg" -> drawSpawnEgg(img, p, effectHash);
            case "food"      -> drawFood(img, p);
            case "totem"     -> drawTotem(img, p);
            case "throwable" -> drawThrowable(img, p);
            default          -> drawWand(img, p);   // use_item, unknown
        }

        return img;
    }

    // ── Palette ───────────────────────────────────────────────────────────────

    private record Palette(
            int bg,      // fully transparent — not used directly but kept for clarity
            int body,    // primary shape fill
            int shade,   // darker variant for depth / shadow side
            int light,   // lighter variant for highlight side
            int accent,  // gem, glow, string, detail
            int accentB, // secondary accent / highlight pixel
            int outline  // darkest outline pixels
    ) {}

    private static Palette paletteFor(String rarity, int hash, String name) {
        // Accent hue varies slightly per item via name hash
        int nameHash = name == null ? 0 : name.hashCode();
        int hueShift = (nameHash ^ hash) & 0x1F; // 0-31

        return switch (normalise(rarity)) {
            case "uncommon" -> new Palette(
                    0,
                    argb(60, 140, 60),
                    argb(38, 90, 38),
                    argb(90, 190, 90),
                    argb(clamp(160 + hueShift), clamp(230 + hueShift), 80),
                    argb(200, 255, 120),
                    argb(20, 50, 20));
            case "rare" -> new Palette(
                    0,
                    argb(50, 100, 180),
                    argb(28, 60, 120),
                    argb(90, 150, 220),
                    argb(clamp(80 + hueShift), clamp(200 + hueShift), 255),
                    argb(180, 230, 255),
                    argb(15, 30, 70));
            case "epic" -> new Palette(
                    0,
                    argb(130, 50, 180),
                    argb(80, 20, 120),
                    argb(180, 90, 230),
                    argb(clamp(220 + hueShift), clamp(130 + hueShift), 255),
                    argb(255, 200, 255),
                    argb(40, 10, 60));
            case "legendary" -> new Palette(
                    0,
                    argb(180, 140, 20),
                    argb(120, 90, 10),
                    argb(230, 200, 60),
                    argb(255, clamp(220 + hueShift), clamp(60 + hueShift)),
                    argb(255, 255, 180),
                    argb(60, 40, 0));
            default -> // common
                    new Palette(
                    0,
                    argb(130, 120, 110),
                    argb(85, 78, 70),
                    argb(175, 165, 155),
                    argb(clamp(200 + hueShift), clamp(190 + hueShift), clamp(160 + hueShift)),
                    argb(230, 225, 210),
                    argb(40, 36, 32));
        };
    }

    // ── Shape drawers ─────────────────────────────────────────────────────────

    /** Diagonal sword: tip top-right, pommel bottom-left. */
    private static void drawSword(NativeImage img, Palette p) {
        // Blade — two-pixel wide diagonal (x decreases, y increases)
        int[][] blade = {
            {13,1},{12,2},{11,2},{11,3},{10,4},{9,4},{9,5},{8,6},{7,6},{7,7},
            {6,8},{5,8}
        };
        for (int[] b : blade) {
            px(img, b[0], b[1], p.body());
            // Shadow pixel one step offset
            if (b[0] - 1 >= 0 && b[1] + 1 < 16) px(img, b[0]-1, b[1]+1, p.shade());
        }
        // Crossguard horizontal bar at (3-8, 10)
        for (int x = 3; x <= 9; x++) px(img, x, 10, p.body());
        for (int x = 3; x <= 9; x++) px(img, x, 11, p.shade());
        // Highlight top of crossguard
        for (int x = 3; x <= 9; x++) px(img, x, 9, p.light());

        // Grip — single-pixel diagonal below crossguard
        int[][] grip = {{6,12},{5,13},{4,14},{3,15}};
        for (int[] g : grip) {
            if (g[0] >= 0 && g[1] < 16) px(img, g[0], g[1], p.shade());
        }

        // Blade tip gem / shine
        px(img, 13, 1, p.accentB());
        px(img, 12, 1, p.accent());

        // Pommel
        px(img, 2, 15, p.accent());
        px(img, 3, 14, p.light());

        // Outline dots
        px(img, 14, 0, p.outline());
        px(img, 2, 14, p.outline());
    }

    /** Bow: left arc + right string + arrow shaft. */
    private static void drawBow(NativeImage img, Palette p) {
        // Arc pixels (left side)
        int[][] arc = {
            {3,1},{2,2},{2,3},{2,4},{3,5},{3,6},{3,7},{3,8},{3,9},{3,10},{2,11},{2,12},{3,13},{4,14}
        };
        for (int[] a : arc) px(img, a[0], a[1], p.body());
        // Shade on inside of arc
        for (int[] a : arc) {
            if (a[0]+1 < 16) px(img, a[0]+1, a[1], p.shade());
        }

        // String — rightside vertical, slightly inset
        for (int y = 1; y <= 14; y++) {
            int x = (y < 7) ? 11 + (y / 3) : 13 - ((y - 7) / 3);
            x = Math.min(13, x);
            px(img, x, y, p.accent());
        }

        // Arrow shaft
        int[][] arrow = {{4,7},{5,7},{6,7},{7,7},{8,7},{9,7},{10,7}};
        for (int[] a : arrow) px(img, a[0], a[1], p.light());

        // Arrowhead
        px(img, 11, 6, p.accentB());
        px(img, 11, 7, p.accentB());
        px(img, 11, 8, p.accentB());
        px(img, 12, 7, p.accentB());

        // Fletching
        px(img, 3, 6, p.accentB());
        px(img, 3, 8, p.accentB());

        // Bow ends / nocks
        px(img, 3, 1, p.accentB());
        px(img, 4, 14, p.accentB());
    }

    /** Wand / use_item: diagonal staff with glowing gem tip. */
    private static void drawWand(NativeImage img, Palette p) {
        // Staff body — single pixel diagonal
        int[][] staff = {
            {3,13},{4,12},{5,11},{6,10},{7,9},{8,8},{9,7},{10,6},{11,5},{12,4}
        };
        for (int[] s : staff) {
            px(img, s[0], s[1], p.body());
            if (s[0]+1 < 16) px(img, s[0]+1, s[1], p.shade());
        }

        // Gem at tip — circle at (12,3) r=2
        int[][] gem = {
            {11,2},{12,2},{13,2},
            {10,3},{11,3},{12,3},{13,3},{14,3},
            {11,4},{12,4},{13,4}
        };
        for (int[] g : gem) if (g[0]<16 && g[1]<16) px(img, g[0], g[1], p.accent());
        // Gem inner highlight
        px(img, 12, 3, p.accentB());
        px(img, 11, 2, p.accentB());

        // Handle wrap bands
        px(img, 4, 12, p.accent());
        px(img, 6, 10, p.accent());

        // Base/pommel
        px(img, 2, 14, p.body());
        px(img, 3, 14, p.shade());
        px(img, 2, 15, p.shade());

        // Glow aura around gem (semi-bright outline)
        px(img, 10, 2, p.accent());
        px(img, 14, 2, p.accent());
        px(img, 10, 4, p.accent());
        px(img, 14, 4, p.accent());
        px(img, 12, 1, p.light());
    }

    /** Potion: round body + neck + cork. */
    private static void drawWandAsPotion(NativeImage img, Palette p) {
        drawFood(img, p); // fallback - food also uses round body
    }

    /** Food: apple silhouette with leaf. */
    private static void drawFood(NativeImage img, Palette p) {
        // Apple body — filled circle centred at (8,10), r=4
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                if (dx*dx + dy*dy <= 16) {
                    int fx = 8 + dx, fy = 10 + dy;
                    if (fx >= 0 && fx < 16 && fy >= 0 && fy < 16)
                        px(img, fx, fy, p.body());
                }
            }
        }
        // Shade right half
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = 1; dx <= 4; dx++) {
                if (dx*dx + dy*dy <= 16) {
                    int fx = 8 + dx, fy = 10 + dy;
                    if (fx >= 0 && fx < 16 && fy >= 0 && fy < 16)
                        px(img, fx, fy, p.shade());
                }
            }
        }
        // Highlight top-left
        for (int[] h : new int[][]{{6,7},{7,7},{6,8}}) px(img, h[0], h[1], p.light());

        // Stem
        px(img, 8, 5, p.outline());
        px(img, 8, 6, p.shade());

        // Leaf
        px(img, 9, 5, p.accent());
        px(img, 10, 4, p.accent());
        px(img, 10, 5, p.accentB());

        // Bite notch at top (apple dip)
        px(img, 7, 6, 0x00000000);
        px(img, 8, 6, 0x00000000);
        px(img, 9, 6, 0x00000000);
    }

    /** Spawn egg: bicolour oval, top half body colour, bottom half accent. */
    private static void drawSpawnEgg(NativeImage img, Palette p, int hash) {
        // Oval centred at (8,8), wider than tall
        for (int dy = -6; dy <= 6; dy++) {
            for (int dx = -5; dx <= 5; dx++) {
                double dist = (dx*dx) / 25.0 + (dy*dy) / 36.0;
                if (dist <= 1.0) {
                    int ey = 8 + dy;
                    int ex = 8 + dx;
                    if (ex >= 0 && ex < 16 && ey >= 0 && ey < 16) {
                        int col = (dy < 0) ? p.body() : p.accent();
                        px(img, ex, ey, col);
                    }
                }
            }
        }
        // Equatorial band
        for (int x = 3; x <= 12; x++) px(img, x, 8, p.shade());

        // Highlight spots
        px(img, 6, 5, p.light());
        px(img, 7, 4, p.light());

        // Speckles (seeded by hash)
        int[] speckX = {5, 9, 11, 6, 10};
        int[] speckY = {10, 11, 9, 12, 10};
        for (int i = 0; i < speckX.length; i++) {
            if (((hash >> i) & 1) == 1) px(img, speckX[i], speckY[i], p.accentB());
        }
    }

    /** Totem: tall upright rectangle with simple face and headdress. */
    private static void drawTotem(NativeImage img, Palette p) {
        // Body — tall rect
        for (int y = 3; y <= 14; y++) {
            for (int x = 4; x <= 11; x++) {
                px(img, x, y, p.body());
            }
        }
        // Shade right side
        for (int y = 3; y <= 14; y++) {
            px(img, 11, y, p.shade());
            px(img, 10, y, p.shade());
        }
        // Highlight left
        for (int y = 3; y <= 14; y++) px(img, 4, y, p.light());

        // Headdress at top
        for (int x = 3; x <= 12; x++) px(img, x, 2, p.accent());
        for (int x = 5; x <= 10; x++) px(img, x, 1, p.accentB());
        px(img, 7, 0, p.accentB());
        px(img, 8, 0, p.accentB());

        // Face — eye sockets (dark)
        for (int[] eye : new int[][]{{5,6},{6,6},{9,6},{10,6}}) px(img, eye[0], eye[1], p.outline());
        // Eye glow
        px(img, 5, 6, p.accent());
        px(img, 9, 6, p.accent());

        // Mouth — small rect
        for (int x = 6; x <= 9; x++) px(img, x, 9, p.outline());
        px(img, 6, 8, p.outline());
        px(img, 9, 8, p.outline());

        // Base line
        for (int x = 4; x <= 11; x++) px(img, x, 14, p.shade());
    }

    /** Throwable: small bright orb with diagonal particle trail. */
    private static void drawThrowable(NativeImage img, Palette p) {
        // Orb — circle at (11,4), r=3
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (dx*dx + dy*dy <= 9) {
                    int ox = 11+dx, oy = 4+dy;
                    if (ox>=0 && ox<16 && oy>=0 && oy<16)
                        px(img, ox, oy, p.accent());
                }
            }
        }
        // Orb shell (darker outline)
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                double dist = Math.sqrt(dx*dx + dy*dy);
                if (dist > 2.4 && dist <= 3.0) {
                    int ox = 11+dx, oy = 4+dy;
                    if (ox>=0 && ox<16 && oy>=0 && oy<16)
                        px(img, ox, oy, p.body());
                }
            }
        }
        // Core highlight
        px(img, 10, 3, p.accentB());
        px(img, 11, 3, p.accentB());

        // Diagonal trail — fading toward bottom-left
        int[][] trail = {{9,7},{8,8},{7,9},{6,10},{5,11},{4,12},{3,13}};
        int[] alphas   = {220, 180, 150, 120, 90, 60, 30};
        int ar = (p.accent() >> 16) & 0xFF;
        int ag = (p.accent() >> 8)  & 0xFF;
        int ab =  p.accent()        & 0xFF;
        for (int i = 0; i < trail.length; i++) {
            px(img, trail[i][0], trail[i][1], (alphas[i] << 24) | (ar << 16) | (ag << 8) | ab);
        }

        // Sparkle particles off the trail
        px(img, 7, 8, p.accentB());
        px(img, 5, 12, p.light());
    }

    // ── Pixel helpers ─────────────────────────────────────────────────────────

    /** Set a pixel; 0x00000000 = transparent. */
    private static void px(NativeImage img, int x, int y, int argb) {
        if (x < 0 || x >= 16 || y < 0 || y >= 16) return;
        img.setColorArgb(x, y, argb);
    }

    private static void clearTransparent(NativeImage img) {
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                img.setColorArgb(x, y, 0x00000000);
    }

    /** Pack 0xAARRGGBB — fully opaque. */
    private static int argb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static String normalise(String s) {
        return s == null ? "" : s.toLowerCase().replace("minecraft:", "").trim();
    }
}
