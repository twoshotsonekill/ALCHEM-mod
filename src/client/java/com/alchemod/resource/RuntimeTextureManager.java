package com.alchemod.resource;

import com.alchemod.AlchemodInit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages runtime textures for {@link com.alchemod.creator.DynamicItem} instances.
 *
 * <h3>Resolution order (highest priority first)</h3>
 * <ol>
 *   <li><b>AI sprite commands</b> — {@code creator_sprite_commands} NBT rendered by
 *       {@link SpriteCommandRenderer} synchronously. Zero network calls at render time.</li>
 *   <li><b>Procedural sprite</b> — {@link ProceduralSpriteGenerator} from {@code item_type}
 *       and {@code rarity}. Always succeeds instantly, produces a recognisable silhouette.</li>
 *   <li><b>Pollinations upgrade</b> — optional async download that replaces the procedural
 *       sprite when it arrives. Skipped when AI sprite commands are present.</li>
 * </ol>
 *
 * <p>Textures are registered as standalone {@link NativeImageBackedTexture} objects
 * (not atlas sprites) and referenced by the {@link com.alchemod.mixin.ItemRendererMixin}
 * using {@code RenderLayer.getEntityCutoutNoCull(texId)}.
 */
@Environment(EnvType.CLIENT)
public final class RuntimeTextureManager {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static final java.util.Map<Integer, Identifier> LOADED = new ConcurrentHashMap<>();
    private static final Set<Integer> PENDING_DOWNLOADS = ConcurrentHashMap.newKeySet();
    /** Slots where the highest-quality texture is already in place; skip Pollinations. */
    private static final Set<Integer> FINAL_QUALITY = ConcurrentHashMap.newKeySet();

    private RuntimeTextureManager() {}

    // ── Main entry point ──────────────────────────────────────────────────────

    /**
     * Ensures a texture exists for the item in {@code stack} and returns its
     * {@link Identifier}.  Called every frame by {@link com.alchemod.mixin.ItemRendererMixin}
     * so it must be fast after first call — subsequent calls are a single map lookup.
     */
    public static Identifier ensureForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        NbtCompound tag = getCustomData(stack);
        if (tag == null) return null;

        int slot = tag.getInt("creator_slot");
        if (slot < 0) return null;

        // Fast path — already loaded this session.
        Identifier existing = LOADED.get(slot);
        if (existing != null) {
            if (!FINAL_QUALITY.contains(slot)) {
                maybeQueuePollinationsUpgrade(slot, tag.getString("creator_sprite"));
            }
            return existing;
        }

        // ── Priority 1: AI sprite commands (draw_sprite tool call) ────────────
        String spriteCommands = tag.getString("creator_sprite_commands");
        if (spriteCommands != null && !spriteCommands.isBlank()) {
            NativeImage image = SpriteCommandRenderer.render(spriteCommands);
            if (image != null) {
                Identifier id = register(slot, image);
                LOADED.put(slot, id);
                FINAL_QUALITY.add(slot);
                AlchemodInit.LOG.info("[Creator] Rendered AI sprite-command texture for slot {}.", slot);
                return id;
            }
            AlchemodInit.LOG.warn("[Creator] Sprite commands present but render failed for slot {} — using procedural.", slot);
        }

        // ── Priority 2: Procedural sprite (always works, zero network) ────────
        String itemType  = tag.getString("creator_item_type");
        String rarity    = tag.getString("creator_rarity");
        String name      = tag.getString("creator_name");
        String effects   = tag.getString("creator_effects");
        int effectHash   = effects == null ? 0 : effects.hashCode();

        NativeImage procedural = ProceduralSpriteGenerator.generate(itemType, rarity, effectHash, name);
        Identifier id = register(slot, procedural);
        LOADED.put(slot, id);
        AlchemodInit.LOG.info("[Creator] Generated procedural sprite for slot {} (type={}, rarity={}).", slot, itemType, rarity);

        // ── Priority 3: async Pollinations upgrade ────────────────────────────
        maybeQueuePollinationsUpgrade(slot, tag.getString("creator_sprite"));
        return id;
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    public static boolean isLoaded(int slot)      { return LOADED.containsKey(slot); }
    public static Identifier getLoaded(int slot)  { return LOADED.get(slot); }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static Identifier register(int slot, NativeImage image) {
        Identifier id = Identifier.of(AlchemodInit.MOD_ID, "dynamic/item_" + slot);
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new NativeImageBackedTexture(image));
        return id;
    }

    private static void maybeQueuePollinationsUpgrade(int slot, String prompt) {
        if (prompt == null || prompt.isBlank()
                || FINAL_QUALITY.contains(slot)
                || PENDING_DOWNLOADS.contains(slot)) return;

        if (!PENDING_DOWNLOADS.add(slot)) return;

        CompletableFuture.supplyAsync(() -> fetchImageBytes(prompt, slot))
                .thenAccept(bytes -> MinecraftClient.getInstance().execute(() -> {
                    try {
                        if (bytes != null) {
                            NativeImage raw = NativeImage.read(NativeImage.Format.RGBA,
                                    new ByteArrayInputStream(bytes));
                            Identifier upgraded = register(slot, scale(raw, 16, 16));
                            LOADED.put(slot, upgraded);
                            FINAL_QUALITY.add(slot);
                            AlchemodInit.LOG.info("[Creator] Pollinations upgrade applied for slot {}.", slot);
                        }
                    } catch (Exception e) {
                        AlchemodInit.LOG.warn("[Creator] Failed to decode Pollinations image: {}", e.getMessage());
                    } finally {
                        PENDING_DOWNLOADS.remove(slot);
                    }
                }));
    }

    private static byte[] fetchImageBytes(String prompt, int slot) {
        try {
            String encoded = URLEncoder.encode(
                    "16x16 pixel art minecraft item icon, " + prompt
                    + ", transparent background, centered, no text, simple flat style",
                    StandardCharsets.UTF_8).replace("+", "%20");
            String url = "https://image.pollinations.ai/prompt/" + encoded
                    + "?width=16&height=16&nologo=true&seed=" + slot;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET().build();
            HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                AlchemodInit.LOG.warn("[Creator] Pollinations HTTP {}", res.statusCode());
                return null;
            }
            return res.body().readAllBytes();
        } catch (Exception e) {
            AlchemodInit.LOG.warn("[Creator] Pollinations download failed: {}", e.getMessage());
            return null;
        }
    }

    private static NativeImage scale(NativeImage src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setColorArgb(x, y, src.getColorArgb(
                        x * src.getWidth() / w, y * src.getHeight() / h));
        return out;
    }

    private static NbtCompound getCustomData(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        return c != null ? c.copyNbt() : null;
    }
}
