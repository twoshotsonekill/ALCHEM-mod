package com.alchemod.resource;

import com.alchemod.AlchemodInit;
import com.alchemod.screen.SpriteCommandRenderer;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages runtime textures for dynamic items created by the Oddity Printer.
 *
 * <h3>Texture resolution order (highest priority first)</h3>
 * <ol>
 *   <li><b>Sprite commands</b> — if {@code creator_sprite_commands} NBT is present, the
 *       structured drawing commands produced by {@link com.alchemod.ai.SpriteToolClient}
 *       are interpreted synchronously by {@link SpriteCommandRenderer}.  No network call,
 *       no Rhino, deterministically reproducible from NBT alone.</li>
 *   <li><b>Glyph / input-colour texture</b> — generated instantly from the two input
 *       item identifiers if their colours are known, giving a visually distinct but
 *       abstract placeholder.</li>
 *   <li><b>Prompt hash texture</b> — a seeded procedural diamond glyph derived from
 *       the item name + sprite prompt strings.</li>
 *   <li><b>Pollinations download</b> (async upgrade) — queued after any of the above
 *       placeholder paths; replaces the placeholder once the download completes.
 *       Skipped entirely when sprite commands are present ({@code AI_READY} flag).</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class RuntimeTextureManager {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static final Map<Integer, Identifier> LOADED = new ConcurrentHashMap<>();
    private static final Set<Integer> PENDING_AI_DOWNLOADS = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> AI_READY = ConcurrentHashMap.newKeySet();

    private static final Map<String, Integer> ITEM_COLORS = Map.ofEntries(
            Map.entry("minecraft:diamond",             0xFF00FFFF),
            Map.entry("minecraft:emerald",             0xFF00FF00),
            Map.entry("minecraft:redstone",            0xFFFF0000),
            Map.entry("minecraft:gold_ingot",          0xFFFFD700),
            Map.entry("minecraft:iron_ingot",          0xFFCCCCCC),
            Map.entry("minecraft:lapis_lazuli",        0xFF0000AA),
            Map.entry("minecraft:quartz",              0xFFEEEEEE),
            Map.entry("minecraft:blaze_rod",           0xFFFF6600),
            Map.entry("minecraft:ghast_tear",          0xFFFFFFFF),
            Map.entry("minecraft:ender_pearl",         0xFF666688),
            Map.entry("minecraft:slime_ball",          0xFF00FF00),
            Map.entry("minecraft:spider_eye",          0xFF660000),
            Map.entry("minecraft:bone",                0xFFEEEEEE),
            Map.entry("minecraft:feather",             0xFFFFFFFF),
            Map.entry("minecraft:flint",               0xFF333333),
            Map.entry("minecraft:glass",               0xFFAAFFFF),
            Map.entry("minecraft:obsidian",            0xFF000066),
            Map.entry("minecraft:nether_star",         0xFFFFFFAA),
            Map.entry("minecraft:shulker_shell",       0xFFAA6699),
            Map.entry("minecraft:prismarine_shard",    0xFF00AAAA),
            Map.entry("minecraft:prismarine_crystals", 0xFF55FFFF),
            Map.entry("minecraft:heart_of_the_sea",    0xFF0000FF),
            Map.entry("minecraft:totem_of_undying",    0xFFFFAA00),
            Map.entry("minecraft:netherite_ingot",     0xFF333333),
            Map.entry("minecraft:copper_ingot",        0xFFFF8844),
            Map.entry("minecraft:amethyst_shard",      0xFFAA00FF),
            Map.entry("minecraft:music_disc_11",       0xFF000000),
            Map.entry("minecraft:fire_charge",         0xFFFF4400),
            Map.entry("minecraft:experience_bottle",   0xFF00AAFF)
    );

    private RuntimeTextureManager() {
    }

    // ── Primary entry point ───────────────────────────────────────────────────

    /**
     * Returns (and if necessary generates) the texture {@link Identifier} for the
     * dynamic item in {@code stack}.  Always returns immediately; async work is
     * dispatched in the background when needed.
     */
    public static Identifier ensureForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        NbtCompound tag = getCustomData(stack);
        if (tag == null) return null;

        int slot = tag.getInt("creator_slot");
        if (slot < 0) return null;

        // Fast path: already loaded this session.
        Identifier loaded = LOADED.get(slot);
        if (loaded != null) {
            // If we have a tool-generated sprite we don't need Pollinations.
            if (!AI_READY.contains(slot)) {
                maybeQueueAiUpgrade(slot, tag.getString("creator_sprite"));
            }
            return loaded;
        }

        // ── Priority 1: sprite commands from the draw_sprite tool call ────────
        //
        // If the item was created with the new tool-calling flow the NBT will
        // contain a "creator_sprite_commands" JSON string.  We render it
        // synchronously right here — no async, no Pollinations upgrade queued.
        String spriteCommands = tag.getString("creator_sprite_commands");
        if (spriteCommands != null && !spriteCommands.isBlank()) {
            NativeImage image = SpriteCommandRenderer.render(spriteCommands);
            if (image != null) {
                Identifier texId = registerGeneratedTexture(slot, image);
                LOADED.put(slot, texId);
                AI_READY.add(slot); // suppress Pollinations upgrade for this slot
                AlchemodInit.LOG.info("[Creator] Rendered sprite-command texture for slot {}.", slot);
                return texId;
            }
            // If render returned null (malformed JSON etc.) fall through.
            AlchemodInit.LOG.warn("[Creator] Sprite commands present but render failed for slot {} — falling back.", slot);
        }

        // ── Priority 2 & 3: procedural glyph texture ─────────────────────────
        Identifier textureId;
        Map<String, Integer> inputColors = extractInputColors(
                tag.getString("creator_input_a"), tag.getString("creator_input_b"));
        if (!inputColors.isEmpty()) {
            textureId = registerGeneratedTexture(slot, buildGlyphTexture(
                    firstColor(inputColors, 0xFF8844AA),
                    secondColor(inputColors, 0xFF4422AA),
                    getRarityColor(normalise(tag.getString("creator_rarity")))));
        } else {
            textureId = registerGeneratedTexture(slot, buildPromptTexture(
                    slot,
                    tag.getString("creator_name"),
                    tag.getString("creator_sprite"),
                    normalise(tag.getString("creator_rarity"))));
        }

        LOADED.put(slot, textureId);

        // ── Priority 4: async Pollinations upgrade (only if no tool sprite) ───
        maybeQueueAiUpgrade(slot, tag.getString("creator_sprite"));
        return textureId;
    }

    // ── Manual generation helpers (used by older code paths) ─────────────────

    public static void generateFromInputs(int slot, Map<String, Integer> itemColors, String aiPrompt, Consumer<Identifier> onReady) {
        if (LOADED.containsKey(slot)) {
            onReady.accept(LOADED.get(slot));
            maybeQueueAiUpgrade(slot, aiPrompt);
            return;
        }

        MinecraftClient.getInstance().execute(() -> {
            Identifier id = registerGeneratedTexture(slot, buildGlyphTexture(
                    firstColor(itemColors, 0xFF8844AA),
                    secondColor(itemColors, 0xFF4422AA),
                    getRarityColor(slot)));
            LOADED.put(slot, id);
            onReady.accept(id);
            maybeQueueAiUpgrade(slot, aiPrompt);
        });
    }

    public static void downloadSprite(String prompt, int slot, Consumer<Identifier> onReady) {
        if (prompt == null || prompt.isBlank()) {
            if (LOADED.containsKey(slot)) {
                onReady.accept(LOADED.get(slot));
            } else {
                generateFallback(slot, onReady);
            }
            return;
        }

        if (AI_READY.contains(slot) && LOADED.containsKey(slot)) {
            onReady.accept(LOADED.get(slot));
            return;
        }

        if (!PENDING_AI_DOWNLOADS.add(slot)) {
            if (LOADED.containsKey(slot)) onReady.accept(LOADED.get(slot));
            return;
        }

        CompletableFuture.supplyAsync(() -> fetchSpriteBytes(prompt, slot))
                .thenAccept(bytes -> MinecraftClient.getInstance().execute(() -> {
                    try {
                        Identifier texId;
                        if (bytes != null) {
                            NativeImage image = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(bytes));
                            texId = registerGeneratedTexture(slot, scaleToPixelArt(image, 16, 16));
                            AI_READY.add(slot);
                        } else {
                            texId = LOADED.getOrDefault(slot,
                                    registerGeneratedTexture(slot, buildPromptTexture(slot, "", prompt, "common")));
                        }
                        LOADED.put(slot, texId);
                        onReady.accept(texId);
                    } catch (Exception e) {
                        AlchemodInit.LOG.error("[Creator] Failed to decode AI sprite", e);
                        Identifier fallback = LOADED.getOrDefault(slot,
                                registerGeneratedTexture(slot, buildPromptTexture(slot, "", prompt, "common")));
                        LOADED.put(slot, fallback);
                        onReady.accept(fallback);
                    } finally {
                        PENDING_AI_DOWNLOADS.remove(slot);
                    }
                }));
    }

    public static void generateFallback(int slot, Consumer<Identifier> onReady) {
        if (LOADED.containsKey(slot)) {
            onReady.accept(LOADED.get(slot));
            return;
        }
        MinecraftClient.getInstance().execute(() -> {
            Identifier id = registerGeneratedTexture(slot, buildPromptTexture(slot, "", "", "common"));
            LOADED.put(slot, id);
            onReady.accept(id);
        });
    }

    public static boolean isLoaded(int slot) {
        return LOADED.containsKey(slot);
    }

    public static Identifier getLoaded(int slot) {
        return LOADED.get(slot);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static byte[] fetchSpriteBytes(String prompt, int slot) {
        try {
            String encodedPrompt = URLEncoder.encode(
                    "16x16 pixel art minecraft item icon, " + prompt
                    + ", transparent background, centered, no text, simple flat style",
                    StandardCharsets.UTF_8).replace("+", "%20");

            String url = "https://image.pollinations.ai/prompt/" + encodedPrompt
                    + "?width=16&height=16&nologo=true&seed=" + slot;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                AlchemodInit.LOG.warn("[Creator] Sprite HTTP {}", response.statusCode());
                return null;
            }
            return response.body().readAllBytes();
        } catch (Exception e) {
            AlchemodInit.LOG.warn("[Creator] Sprite download failed: {}", e.getMessage());
            return null;
        }
    }

    private static void maybeQueueAiUpgrade(int slot, String prompt) {
        if (prompt == null || prompt.isBlank()
                || AI_READY.contains(slot)
                || PENDING_AI_DOWNLOADS.contains(slot)) {
            return;
        }
        downloadSprite(prompt, slot,
                texId -> AlchemodInit.LOG.info("[Creator] Upgraded Pollinations sprite ready for slot {}.", slot));
    }

    private static Map<String, Integer> extractInputColors(String inputA, String inputB) {
        Map<String, Integer> colors = new HashMap<>();
        String keyA = normalise(inputA);
        String keyB = normalise(inputB);
        if (keyA != null && ITEM_COLORS.containsKey("minecraft:" + keyA)) colors.put("minecraft:" + keyA, ITEM_COLORS.get("minecraft:" + keyA));
        if (keyB != null && ITEM_COLORS.containsKey("minecraft:" + keyB)) colors.put("minecraft:" + keyB, ITEM_COLORS.get("minecraft:" + keyB));
        return colors;
    }

    private static Identifier registerGeneratedTexture(int slot, NativeImage image) {
        Identifier id = textureId(slot);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        return id;
    }

    private static Identifier textureId(int slot) {
        return Identifier.of(AlchemodInit.MOD_ID, "dynamic/generated_item_" + slot);
    }

    private static NativeImage scaleToPixelArt(NativeImage source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) return source;
        NativeImage scaled = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int srcX = x * source.getWidth() / width;
                int srcY = y * source.getHeight() / height;
                scaled.setColorArgb(x, y, source.getColorArgb(srcX, srcY));
            }
        }
        return scaled;
    }

    private static NativeImage buildPromptTexture(int slot, String name, String prompt, String rarity) {
        int hash = (name + "|" + prompt + "|" + rarity + "|" + slot).hashCode();
        int primary   = 0xFF000000 | (clamp(70 + ((hash >> 16) & 0x7F)) << 16) | (clamp(70 + ((hash >> 8) & 0x7F)) << 8) | clamp(70 + (hash & 0x7F));
        int secondary = 0xFF000000 | (clamp(45 + ((hash >> 9) & 0x9F)) << 16) | (clamp(45 + ((hash >> 3) & 0x9F)) << 8) | clamp(45 + ((hash >> 19) & 0x9F));
        return buildGlyphTexture(primary, secondary, getRarityColor(rarity));
    }

    private static NativeImage buildGlyphTexture(int primaryColor, int secondaryColor, int rarityColor) {
        int pr = (primaryColor >> 16) & 0xFF, pg = (primaryColor >> 8) & 0xFF, pb = primaryColor & 0xFF;
        int sr = (secondaryColor >> 16) & 0xFF, sg = (secondaryColor >> 8) & 0xFF, sb = secondaryColor & 0xFF;
        int rr = (rarityColor >> 16) & 0xFF, rg = (rarityColor >> 8) & 0xFF, rb = rarityColor & 0xFF;

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = Math.abs(x - 7.5), dy = Math.abs(y - 7.5);
                double diamond = dx + dy;
                boolean outer  = diamond <= 7.2;
                boolean inner  = diamond <= 5.1;
                boolean core   = diamond <= 2.6;
                boolean rune   = inner && !core && ((x + y) % 3 == 0 || x == 7 || x == 8 || y == 7 || y == 8);
                boolean accent = (x == 4 && y == 4) || (x == 11 && y == 4) || (x == 4 && y == 11) || (x == 11 && y == 11);

                int argb;
                if (!outer) {
                    argb = 0x00000000;
                } else if (!inner) {
                    argb = 0xFF000000 | (rr << 16) | (rg << 8) | rb;
                } else if (core) {
                    argb = 0xFF000000 | (clamp((pr + sr + rr) / 3 + 28) << 16) | (clamp((pg + sg + rg) / 3 + 28) << 8) | clamp((pb + sb + rb) / 3 + 28);
                } else if (rune || accent) {
                    argb = 0xFF000000 | (clamp((sr * 3 + rr) / 4 + 12) << 16) | (clamp((sg * 3 + rg) / 4 + 12) << 8) | clamp((sb * 3 + rb) / 4 + 12);
                } else {
                    float blend = (float) ((x * 2 + y * 3) % 11) / 10.0f;
                    argb = 0xFF000000 | (clamp((int)(pr * (1f - blend * 0.35f) + sr * blend * 0.35f)) << 16) | (clamp((int)(pg * (1f - blend * 0.35f) + sg * blend * 0.35f)) << 8) | clamp((int)(pb * (1f - blend * 0.35f) + sb * blend * 0.35f));
                }
                image.setColorArgb(x, y, argb);
            }
        }
        image.setColorArgb(7, 4, 0xFFFFFFFF);
        image.setColorArgb(8, 5, 0xFFF6F2D8);
        image.setColorArgb(10, 9, 0xCCFFFFFF);
        return image;
    }

    private static int firstColor(Map<String, Integer> colors, int fallback) {
        return colors.values().stream().findFirst().orElse(fallback);
    }

    private static int secondColor(Map<String, Integer> colors, int fallback) {
        return colors.values().stream().skip(1).findFirst().orElse(firstColor(colors, fallback));
    }

    private static int getRarityColor(String rarity) {
        return switch (rarity) {
            case "uncommon"  -> 0xFF55AA55;
            case "rare"      -> 0xFF55AAAA;
            case "epic"      -> 0xFFAA55AA;
            case "legendary" -> 0xFFFFAA00;
            default          -> 0xFF9E9E9E;
        };
    }

    private static int getRarityColor(int slot) {
        return switch (slot % 5) {
            case 1 -> 0xFF55AA55;
            case 2 -> 0xFF55AAAA;
            case 3 -> 0xFFAA55AA;
            case 4 -> 0xFFFFAA00;
            default -> 0xFF9E9E9E;
        };
    }

    private static String normalise(String value) {
        return value == null ? "" : value.toLowerCase().replace("minecraft:", "").trim();
    }

    private static NbtCompound getCustomData(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null ? customData.copyNbt() : null;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
}
