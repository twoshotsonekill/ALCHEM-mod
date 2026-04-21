package com.alchemod.resource;

import com.alchemod.AlchemodInit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Downloads a 16×16 pixel-art sprite from Pollinations.ai (free, no key required)
 * and registers it as a NativeImageBackedTexture on the client's TextureManager.
 *
 * The texture ID returned is used directly in the item model JSON override we
 * inject via DynamicResourcePack.
 */
@Environment(EnvType.CLIENT)
public class RuntimeTextureManager {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
            .build();

    // Cache so we don't re-download the same slot
    private static final Map<Integer, Identifier> LOADED = new ConcurrentHashMap<>();

    /**
     * Kick off an async sprite download for the given slot index.
     * When done, calls onReady with the texture Identifier on the render thread.
     *
     * prompt  — the Pollinations image prompt (item name + description)
     * slot    — the dynamic item slot index (used to form the texture ID)
     */
    public static void downloadSprite(String prompt, int slot, Consumer<Identifier> onReady) {
        if (LOADED.containsKey(slot)) {
            onReady.accept(LOADED.get(slot));
            return;
        }

        // Pollinations.ai — free AI image generation, no API key required
        // We ask for a 16×16 pixel art Minecraft item icon
        String encodedPrompt = encodeUrl(
                "16x16 pixel art minecraft item icon, " + prompt
                + ", transparent background, centered, no text, simple flat style");

        String url = "https://image.pollinations.ai/prompt/" + encodedPrompt
                + "?width=16&height=16&nologo=true&seed=" + slot;

        AlchemodInit.LOG.info("[Creator] Fetching sprite: {}", url);

        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();

                HttpResponse<InputStream> resp = HTTP.send(req,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (resp.statusCode() != 200) {
                    AlchemodInit.LOG.warn("[Creator] Sprite HTTP {}", resp.statusCode());
                    return (byte[]) null;
                }

                return resp.body().readAllBytes();

            } catch (Exception e) {
                AlchemodInit.LOG.error("[Creator] Sprite download failed", e);
                return (byte[]) null;
            }
        }).thenAccept(bytes -> {
            // Must register the texture on the render / main client thread
            MinecraftClient.getInstance().execute(() -> {
                Identifier texId = Identifier.of(AlchemodInit.MOD_ID,
                        "textures/item/dynamic_item_" + slot + ".png");
                try {
                    if (bytes != null) {
                        NativeImage img = NativeImage.read(
                                NativeImage.Format.RGBA,
                                new java.io.ByteArrayInputStream(bytes));
                        // Scale to 16×16 if needed
                        NativeImage scaled = scaleToPixelArt(img, 16, 16);
                        NativeImageBackedTexture tex = new NativeImageBackedTexture(scaled);
                        MinecraftClient.getInstance().getTextureManager()
                                .registerTexture(texId, tex);
                        LOADED.put(slot, texId);
                        AlchemodInit.LOG.info("[Creator] Sprite registered for slot {}", slot);
                    } else {
                        // Fall back to a generated procedural texture
                        Identifier fallback = generateFallbackTexture(slot);
                        LOADED.put(slot, fallback);
                        texId = fallback;
                    }
                    onReady.accept(texId);
                } catch (Exception e) {
                    AlchemodInit.LOG.error("[Creator] Failed to load image bytes", e);
                    Identifier fallback = generateFallbackTexture(slot);
                    LOADED.put(slot, fallback);
                    onReady.accept(fallback);
                }
            });
        });
    }

    /** Scale a NativeImage to exactly w×h using nearest-neighbour (pixel art safe). */
    private static NativeImage scaleToPixelArt(NativeImage src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        NativeImage dst = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sx = x * src.getWidth()  / w;
                int sy = y * src.getHeight() / h;
                dst.setColorArgb(x, y, src.getColorArgb(sx, sy));
            }
        }
        return dst;
    }

    /**
     * Generates a deterministic procedural 16×16 texture so the item is never
     * invisible even when the download fails.
     */
    private static Identifier generateFallbackTexture(int slot) {
        Identifier id = Identifier.of(AlchemodInit.MOD_ID,
                "textures/item/dynamic_fallback_" + slot + ".png");

        // Seed colours from slot so each item looks different
        java.util.Random rng = new java.util.Random(slot * 0x9E3779B97F4A7C15L);
        int r1 = rng.nextInt(180) + 60;
        int g1 = rng.nextInt(180) + 60;
        int b1 = rng.nextInt(180) + 60;
        int r2 = (r1 + 80) % 256;
        int g2 = (g1 + 80) % 256;
        int b2 = (b1 + 120) % 256;

        NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean border = (x == 0 || x == 15 || y == 0 || y == 15);
                boolean cross  = (x == 7 || x == 8 || y == 7 || y == 8);
                boolean corner = (x < 2 || x > 13) && (y < 2 || y > 13);
                if (corner) {
                    img.setColorArgb(x, y, 0x00000000);
                } else if (border || cross) {
                    img.setColorArgb(x, y, 0xFF000000 | (r2 << 16) | (g2 << 8) | b2);
                } else {
                    img.setColorArgb(x, y, 0xFF000000 | (r1 << 16) | (g1 << 8) | b1);
                }
            }
        }

        NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        return id;
    }

    private static String encodeUrl(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s.replace(" ", "%20");
        }
    }

    public static boolean isLoaded(int slot) { return LOADED.containsKey(slot); }
    public static Identifier getLoaded(int slot) { return LOADED.get(slot); }
}
