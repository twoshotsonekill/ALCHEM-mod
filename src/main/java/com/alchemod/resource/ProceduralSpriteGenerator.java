package com.alchemod.resource;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public final class ProceduralSpriteGenerator {

    private ProceduralSpriteGenerator() {
    }

    public static NativeImage generate(
            String itemType,
            String rarity,
            String name,
            String effects,
            String inputA,
            String inputB,
            int uid
    ) {
        String type = normalise(itemType);
        int hash = stableHash(type + "|" + rarity + "|" + name + "|" + effects + "|" + inputA + "|" + inputB + "|" + uid);
        int primary = colorFromText(inputA + "|" + name, 0xFF7C5CC4);
        int secondary = colorFromText(inputB + "|" + effects, 0xFF39A5A7);
        int accent = rarityColor(rarity);
        int dark = mix(primary, 0xFF101018, 0.55f);
        int light = mix(accent, 0xFFFFFFFF, 0.35f);

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        clear(image);

        switch (type) {
            case "weapon", "sword" -> drawSword(image, primary, secondary, accent, light, hash);
            case "bow" -> drawBow(image, primary, secondary, accent, light, hash);
            case "tool" -> drawTool(image, primary, secondary, accent, light, hash);
            case "potion" -> drawPotion(image, primary, secondary, accent, light, hash);
            case "wand" -> drawWand(image, primary, secondary, accent, light, hash);
            case "charm" -> drawCharm(image, primary, secondary, accent, light, hash);
            case "scroll" -> drawScroll(image, primary, secondary, accent, light, hash);
            case "throwable" -> drawThrowable(image, primary, secondary, accent, light, hash);
            case "spawn_item", "spawn_egg" -> drawSpawnItem(image, primary, secondary, accent, light, hash);
            case "food" -> drawFood(image, primary, secondary, accent, light, hash);
            default -> drawArtifact(image, primary, secondary, accent, dark, light, hash);
        }

        drawHashRunes(image, hash, accent, light);
        return image;
    }

    private static void drawSword(NativeImage image, int primary, int secondary, int accent, int light, int hash) {
        rect(image, 7, 1, 9, 10, mix(primary, 0xFFFFFFFF, 0.55f));
        rect(image, 8, 0, 8, 1, light);
        rect(image, 4, 9, 12, 10, accent);
        rect(image, 7, 11, 9, 14, secondary);
        pixel(image, 7, 4, light);
        pixel(image, 9, 7, mix(accent, 0xFFFFFFFF, 0.4f));
    }

    private static void drawBow(NativeImage image, int primary, int secondary, int accent, int light, int hash) {
        rect(image, 5, 2, 6, 4, secondary);
        rect(image, 4, 5, 5, 10, secondary);
        rect(image, 5, 11, 6, 13, secondary);
        line(image, 10, 2, 9, 13, light);
        rect(image, 7, 7, 11, 7, accent);
        pixel(image, 8, 6, light);
        pixel(image, 8, 8, light);
    }

    private static void drawTool(NativeImage image, int primary, int secondary, int accent, int light, int hash) {
        rect(image, 7, 6, 9, 14, secondary);
        rect(image, 5, 2, 11, 5, primary);
        rect(image, 4, 3, 5, 6, primary);
        rect(image, 11, 3, 12, 6, primary);
        pixel(image, 6, 2, light);
        pixel(image, 10, 4, accent);
    }

    private static void drawPotion(NativeImage image, int primary, int secondary, int accent, int light, int hash) {
        circle(image, 8, 10, 4, mix(primary, accent, 0.45f));
        rect(image, 7, 5, 9, 7, mix(secondary, 0xFFFFFFFF, 0.35f));
        rect(image, 6, 4, 10, 5, secondary);
        pixel(image, 6, 8, light);
        pixel(image, 10, 12, mix(accent, 0xFFFFFFFF, 0.5f));
    }

    private static void drawWand(NativeImage image, int primary, int secondary, int accent, int light, int hash) {
        line(image, 4, 13, 11, 3, secondary);
        line(image, 5, 13, 12, 3, mix(secondary, 0xFFFFFFFF, 0.25f));
        circle(image, 12, 3, 2, accent);
        pixel(image, 12, 3, light);
        pixel(image, 3, 14, primary);
    }

    private static void drawCharm(NativeImage image, int primary, int secondary, int accent, int light, int hash) {
        line(image, 5, 2, 11, 2, light);
        line(image, 5, 2, 8, 6, light);
        line(image, 11, 2, 8, 6, light);
        circle(image, 8, 9, 4, primary);
        circle(image, 8, 9, 2, secondary);
        pixel(image, 6, 7, accent);
        pixel(image, 9, 11, light);
    }

    private static void drawScroll(NativeImage image, int primary, int secondary, int accent, int light, int hash) {
        rect(image, 4, 3, 12, 12, 0xFFE8D8A8);
        rect(image, 3, 2, 13, 4, mix(secondary, 0xFFFFFFFF, 0.25f));
        rect(image, 3, 11, 13, 13, mix(secondary, 0xFFFFFFFF, 0.25f));
        line(image, 5, 6, 10, 6, primary);
        line(image, 5, 8, 11, 8, accent);
        pixel(image, 11, 4, light);
    }

    private static void drawThrowable(NativeImage image, int primary, int secondary, int accent, int light, int hash) {
        circle(image, 8, 9, 5, mix(primary, 0xFF202028, 0.45f));
        rect(image, 8, 3, 9, 5, secondary);
        pixel(image, 8, 3, light);
        pixel(image, 6, 7, mix(0xFFFFFFFF, primary, 0.3f));
        pixel(image, 10, 11, accent);
    }

    private static void drawSpawnItem(NativeImage image, int primary, int secondary, int accent, int light, int hash) {
        circle(image, 8, 8, 6, primary);
        rect(image, 3, 8, 13, 13, secondary);
        circle(image, 7, 6, 2, accent);
        pixel(image, 10, 10, light);
        pixel(image, 5, 11, mix(light, secondary, 0.4f));
    }

    private static void drawFood(NativeImage image, int primary, int secondary, int accent, int light, int hash) {
        circle(image, 8, 9, 5, primary);
        rect(image, 8, 3, 9, 5, secondary);
        rect(image, 5, 4, 7, 5, accent);
        pixel(image, 6, 7, light);
        pixel(image, 10, 11, mix(primary, 0xFF000000, 0.25f));
    }

    private static void drawArtifact(NativeImage image, int primary, int secondary, int accent, int dark, int light, int hash) {
        if ((hash & 1) == 0) {
            circle(image, 8, 8, 6, dark);
            circle(image, 8, 8, 4, primary);
            rect(image, 6, 5, 10, 7, secondary);
        } else {
            rect(image, 5, 2, 11, 13, dark);
            rect(image, 6, 3, 10, 12, primary);
            rect(image, 7, 5, 9, 9, secondary);
        }
        pixel(image, 6, 6, light);
        pixel(image, 10, 10, accent);
        pixel(image, 8, 4, mix(light, accent, 0.35f));
    }

    private static void drawHashRunes(NativeImage image, int hash, int accent, int light) {
        for (int i = 0; i < 4; i++) {
            int x = 2 + Math.floorMod(hash >> (i * 5), 12);
            int y = 2 + Math.floorMod(hash >> (i * 7), 12);
            int color = (i & 1) == 0 ? accent : light;
            if (((hash >> i) & 1) == 0) {
                pixel(image, x, y, color);
            } else {
                rect(image, x, y, Math.min(15, x + 1), y, color);
            }
        }
    }

    private static void clear(NativeImage image) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setColorArgb(x, y, 0x00000000);
            }
        }
    }

    private static void rect(NativeImage image, int x1, int y1, int x2, int y2, int color) {
        int minX = clampCoord(Math.min(x1, x2));
        int maxX = clampCoord(Math.max(x1, x2));
        int minY = clampCoord(Math.min(y1, y2));
        int maxY = clampCoord(Math.max(y1, y2));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                image.setColorArgb(x, y, color);
            }
        }
    }

    private static void pixel(NativeImage image, int x, int y, int color) {
        image.setColorArgb(clampCoord(x), clampCoord(y), color);
    }

    private static void circle(NativeImage image, int cx, int cy, int radius, int color) {
        int r2 = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + y * y <= r2) {
                    int px = cx + x;
                    int py = cy + y;
                    if (px >= 0 && px < 16 && py >= 0 && py < 16) {
                        image.setColorArgb(px, py, color);
                    }
                }
            }
        }
    }

    private static void line(NativeImage image, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0.0f : i / (float) steps;
            int x = Math.round(x1 + (x2 - x1) * t);
            int y = Math.round(y1 + (y2 - y1) * t);
            pixel(image, x, y, color);
        }
    }

    private static int rarityColor(String rarity) {
        return switch (normalise(rarity)) {
            case "uncommon" -> 0xFF50BE5A;
            case "rare" -> 0xFF3CACE0;
            case "epic" -> 0xFFBE50DC;
            case "legendary" -> 0xFFF0B41E;
            default -> 0xFFA0A0A0;
        };
    }

    private static int colorFromText(String text, int fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        int hash = stableHash(text);
        int r = 72 + Math.floorMod(hash, 144);
        int g = 72 + Math.floorMod(hash >> 8, 144);
        int b = 72 + Math.floorMod(hash >> 16, 144);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int stableHash(String text) {
        int hash = 0x811C9DC5;
        String value = text == null ? "" : text;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    private static int mix(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int outA = (int) (aa + (ba - aa) * t);
        int outR = (int) (ar + (br - ar) * t);
        int outG = (int) (ag + (bg - ag) * t);
        int outB = (int) (ab + (bb - ab) * t);
        return (clampByte(outA) << 24) | (clampByte(outR) << 16) | (clampByte(outG) << 8) | clampByte(outB);
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int clampCoord(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private static String normalise(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("minecraft:", "").trim();
    }
}
