package com.alchemod.resource;

import com.alchemod.AlchemodInit;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;

/**
 * Interprets the structured {@code creator_sprite_commands} NBT value and paints
 * the result into a 16×16 {@link NativeImage}.
 *
 * <h3>Op types</h3>
 * <ul>
 *   <li>{@code fill}   — paint every pixel. r/g/b = 0 together means fully transparent.</li>
 *   <li>{@code rect}   — filled rectangle (x1,y1)→(x2,y2), inclusive.</li>
 *   <li>{@code pixel}  — single pixel at (x,y).</li>
 *   <li>{@code circle} — filled circle centred at (cx,cy) with given radius (1-8).</li>
 * </ul>
 *
 * All ops accept an optional {@code a} field (0-255, default 255) for alpha.
 * When {@code fill} is called with r=g=b=0 and no {@code a} field (or a=0),
 * the canvas is cleared to fully transparent — the intended "transparent background"
 * convention used in the system prompt.
 */
@Environment(EnvType.CLIENT)
public final class SpriteCommandRenderer {

    private SpriteCommandRenderer() {
    }

    public static NativeImage render(String commandsJson) {
        if (commandsJson == null || commandsJson.isBlank()) return null;

        try {
            JsonObject root = JsonParser.parseString(commandsJson).getAsJsonObject();
            JsonArray commands = root.getAsJsonArray("commands");
            if (commands == null || commands.isEmpty()) return null;

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
                // Optional alpha: omitted → 255 (fully opaque).
                int a = cmd.has("a") ? clampByte(getInt(cmd, "a")) : 255;

                // Convention: fill with r=g=b=0 (and default/zero alpha) means
                // "transparent background" — clear the canvas rather than paint black.
                if ("fill".equals(op) && r == 0 && g == 0 && b == 0 && a <= 0) {
                    clearTransparent(image);
                    executed++;
                    continue;
                }

                int argb = (a << 24) | (r << 16) | (g << 8) | b;

                switch (op) {
                    case "fill"   -> executeFill(image, argb);
                    case "rect"   -> executeRect(image, cmd, argb);
                    case "pixel"  -> executePixel(image, cmd, argb);
                    case "circle" -> executeCircle(image, cmd, argb);
                    default -> AlchemodInit.LOG.debug("[Sprite] Unknown op '{}', skipping.", op);
                }
                executed++;
            }

            if (executed == 0) { image.close(); return null; }
            return image;

        } catch (Exception e) {
            AlchemodInit.LOG.warn("[Sprite] Failed to render sprite commands: {}", e.getMessage());
            return null;
        }
    }

    // ── Operations ────────────────────────────────────────────────────────────

    private static void executeFill(NativeImage image, int argb) {
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                image.setColorArgb(x, y, argb);
    }

    private static void executeRect(NativeImage image, JsonObject cmd, int argb) {
        int x1 = clampCoord(getInt(cmd, "x1")), y1 = clampCoord(getInt(cmd, "y1"));
        int x2 = clampCoord(getInt(cmd, "x2")), y2 = clampCoord(getInt(cmd, "y2"));
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                image.setColorArgb(x, y, argb);
    }

    private static void executePixel(NativeImage image, JsonObject cmd, int argb) {
        image.setColorArgb(clampCoord(getInt(cmd, "x")), clampCoord(getInt(cmd, "y")), argb);
    }

    private static void executeCircle(NativeImage image, JsonObject cmd, int argb) {
        int cx = clampCoord(getInt(cmd, "cx"));
        int cy = clampCoord(getInt(cmd, "cy"));
        int radius = Math.max(1, Math.min(8, getInt(cmd, "radius")));
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    int px = cx + dx, py = cy + dy;
                    if (px >= 0 && px < 16 && py >= 0 && py < 16)
                        image.setColorArgb(px, py, argb);
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void clearTransparent(NativeImage image) {
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                image.setColorArgb(x, y, 0x00000000);
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
