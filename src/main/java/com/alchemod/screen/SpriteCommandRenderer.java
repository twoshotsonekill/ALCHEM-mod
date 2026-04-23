package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;

/**
 * Interprets the structured {@code creator_sprite_commands} NBT value that was
 * produced by {@link com.alchemod.ai.SpriteToolClient} and paints the result
 * into a 16×16 {@link NativeImage}.
 *
 * <p>This class is client-side only and intentionally contains zero Rhino / script
 * execution.  The only logic here is a simple {@code switch} over four drawing ops:
 * {@code fill}, {@code rect}, {@code pixel}, and {@code circle}.
 *
 * <p>Returns {@code null} if the JSON cannot be parsed or no valid commands are found;
 * callers must fall through to the glyph / Pollinations texture paths in that case.
 */
@Environment(EnvType.CLIENT)
public final class SpriteCommandRenderer {

    private SpriteCommandRenderer() {
    }

    /**
     * @param commandsJson  The raw JSON string stored in {@code creator_sprite_commands}.
     *                      Expected shape: {@code {"commands":[{"op":"fill","r":10,"g":10,"b":20},…]}}
     * @return A freshly allocated 16×16 RGBA {@link NativeImage}, or {@code null} on failure.
     */
    public static NativeImage render(String commandsJson) {
        if (commandsJson == null || commandsJson.isBlank()) {
            return null;
        }

        try {
            JsonObject root = JsonParser.parseString(commandsJson).getAsJsonObject();
            JsonArray commands = root.getAsJsonArray("commands");
            if (commands == null || commands.isEmpty()) {
                return null;
            }

            NativeImage image = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
            clearTransparent(image);

            int executed = 0;
            for (JsonElement el : commands) {
                if (!el.isJsonObject()) continue;
                JsonObject cmd = el.getAsJsonObject();
                String op = getString(cmd, "op");
                int r = clampByte(getInt(cmd, "r"));
                int g = clampByte(getInt(cmd, "g"));
                int b = clampByte(getInt(cmd, "b"));
                // NativeImage stores ABGR on little-endian platforms; use the helper
                // setColorArgb which expects 0xAARRGGBB.
                int argb = 0xFF000000 | (r << 16) | (g << 8) | b;

                switch (op) {
                    case "fill"   -> executeFill(image, argb);
                    case "rect"   -> executeRect(image, cmd, argb);
                    case "pixel"  -> executePixel(image, cmd, argb);
                    case "circle" -> executeCircle(image, cmd, argb);
                    default -> AlchemodInit.LOG.debug("[Sprite] Unknown op '{}', skipping.", op);
                }
                executed++;
            }

            if (executed == 0) {
                image.close();
                return null;
            }
            return image;

        } catch (Exception e) {
            AlchemodInit.LOG.warn("[Sprite] Failed to render sprite commands: {}", e.getMessage());
            return null;
        }
    }

    // ── Operations ────────────────────────────────────────────────────────────

    private static void executeFill(NativeImage image, int argb) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setColorArgb(x, y, argb);
            }
        }
    }

    private static void executeRect(NativeImage image, JsonObject cmd, int argb) {
        int x1 = clampCoord(getInt(cmd, "x1"));
        int y1 = clampCoord(getInt(cmd, "y1"));
        int x2 = clampCoord(getInt(cmd, "x2"));
        int y2 = clampCoord(getInt(cmd, "y2"));
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                image.setColorArgb(x, y, argb);
            }
        }
    }

    private static void executePixel(NativeImage image, JsonObject cmd, int argb) {
        int x = clampCoord(getInt(cmd, "x"));
        int y = clampCoord(getInt(cmd, "y"));
        image.setColorArgb(x, y, argb);
    }

    private static void executeCircle(NativeImage image, JsonObject cmd, int argb) {
        int cx     = clampCoord(getInt(cmd, "cx"));
        int cy     = clampCoord(getInt(cmd, "cy"));
        int radius = Math.max(1, Math.min(8, getInt(cmd, "radius")));
        int r2     = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    int px = cx + dx;
                    int py = cy + dy;
                    if (px >= 0 && px < 16 && py >= 0 && py < 16) {
                        image.setColorArgb(px, py, argb);
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void clearTransparent(NativeImage image) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setColorArgb(x, y, 0x00000000);
            }
        }
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && !el.isJsonNull() ? el.getAsString() : "";
    }

    private static int getInt(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && !el.isJsonNull() && el.isJsonPrimitive() ? el.getAsInt() : 0;
    }

    private static int clampByte(int v)  { return Math.max(0, Math.min(255, v)); }
    private static int clampCoord(int v) { return Math.max(0, Math.min(15,  v)); }
}
