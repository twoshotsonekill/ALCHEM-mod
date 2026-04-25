package com.alchemod.resource;

import com.alchemod.AlchemodInit;
import com.alchemod.creator.DynamicItem;
import com.alchemod.item.OddityItem;
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
 * <h3>Texture resolution order</h3>
 * <ol>
 *   <li><b>Sprite commands</b> — if {@code creator_sprite_commands} is in NBT, rendered
 *       synchronously by {@link SpriteCommandRenderer}.  No network, no Pollinations upgrade.</li>
 *   <li><b>Glyph / input-colour texture</b> — generated instantly from the two input items.</li>
 *   <li><b>Prompt hash texture</b> — seeded procedural diamond glyph from item name + sprite prompt.</li>
 *   <li><b>Pollinations download</b> (async) — replaces any placeholder once the download
 *       completes.  Skipped when sprite commands are present.</li>
 * </ol>
 *
 * <p>Works with both the new {@link OddityItem} stacks (uid ≥ 0x40000000) and the legacy
 * {@link DynamicItem} slots (0–63).  The texture map is keyed by the integer stored in
 * {@code creator_slot}, which is unique regardless of its magnitude.
 */
@Environment(EnvType.CLIENT)
public final class RuntimeTextureManager {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /** uid → registered texture identifier */
    private static final Map<Integer, Identifier> LOADED = new ConcurrentHashMap<>();
    private static final Set<Integer> PENDING_AI_DOWNLOADS = ConcurrentHashMap.newKeySet();
    /** Slots whose texture came from the draw_sprite tool — no Pollinations upgrade needed. */
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

    private RuntimeTextureManager() {}

    // ── Primary entry point ───────────────────────────────────────────────────

    /**
     * Returns (and if needed generates) the texture {@link Identifier} for the given stack.
     * Accepts both {@link OddityItem} and legacy {@link DynamicItem} stacks.
     * Always returns immediately; async work is dispatched in the background.
     */
    public static Identifier ensureForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof OddityItem) && !(stack.getItem() instanceof DynamicItem)) return null;

        NbtCompound tag = getCustomData(stack);
        if (tag == null) return null;

        int uid = tag.getInt("creator_slot");
        if (uid == 0 && !tag.contains("creator_slot")) return null;

        // Fast path: already loaded this session.
        Identifier loaded = LOADED.get(uid);
        if (loaded != null) {
            if (!AI_READY.contains(uid))
                maybeQueueAiUpgrade(uid, tag.getString("creator_sprite"));
            return loaded;
        }

        // Priority 1: sprite commands from the draw_sprite tool call
        String spriteCommands = tag.getString("creator_sprite_commands");
        if (spriteCommands != null && !spriteCommands.isBlank()) {
            NativeImage image = SpriteCommandRenderer.render(spriteCommands);
            if (image != null) {
                Identifier texId = registerTexture(uid, image);
                LOADED.put(uid, texId);
                AI_READY.add(uid);
                AlchemodInit.LOG.info("[Creator] Rendered sprite-command texture for uid {}.", uid);
                return texId;
            }
            AlchemodInit.LOG.warn("[Creator] Sprite commands present but render failed for uid {} — falling back.", uid);
        }

        // Priority 2 & 3: procedural glyph texture
        Identifier texId;
        Map<String, Integer> inputColors = extractInputColors(
                tag.getString("creator_input_a"), tag.getString("creator_input_b"));
        if (!inputColors.isEmpty()) {
            texId = registerTexture(uid, buildGlyphTexture(
                    firstColor(inputColors, 0xFF8844AA),
                    secondColor(inputColors, 0xFF4422AA),
                    getRarityColor(normalise(tag.getString("creator_rarity")))));
        } else {
            texId = registerTexture(uid, buildPromptTexture(
                    uid,
                    tag.getString("creator_name"),
                    tag.getString("creator_sprite"),
                    normalise(tag.getString("creator_rarity"))));
        }
        LOADED.put(uid, texId);

        // Priority 4: async Pollinations upgrade
        maybeQueueAiUpgrade(uid, tag.getString("creator_sprite"));
        return texId;
    }

    // ── Manual generation helpers ─────────────────────────────────────────────

    public static void generateFromInputs(int uid, Map<String, Integer> itemColors,
            String aiPrompt, Consumer<Identifier> onReady) {
        if (LOADED.containsKey(uid)) {
            onReady.accept(LOADED.get(uid));
            maybeQueueAiUpgrade(uid, aiPrompt);
            return;
        }
        MinecraftClient.getInstance().execute(() -> {
            Identifier id = registerTexture(uid, buildGlyphTexture(
                    firstColor(itemColors, 0xFF8844AA),
                    secondColor(itemColors, 0xFF4422AA),
                    getRarityColor(uid)));
            LOADED.put(uid, id);
            onReady.accept(id);
            maybeQueueAiUpgrade(uid, aiPrompt);
        });
    }

    public static void downloadSprite(String prompt, int uid, Consumer<Identifier> onReady) {
        if (prompt == null || prompt.isBlank()) {
            if (LOADED.containsKey(uid)) { onReady.accept(LOADED.get(uid)); return; }
            generateFallback(uid, onReady);
            return;
        }
        if (AI_READY.contains(uid) && LOADED.containsKey(uid)) {
            onReady.accept(LOADED.get(uid));
            return;
        }
        if (!PENDING_AI_DOWNLOADS.add(uid)) {
            if (LOADED.containsKey(uid)) onReady.accept(LOADED.get(uid));
            return;
        }
        CompletableFuture.supplyAsync(() -> fetchSpriteBytes(prompt, uid))
                .thenAccept(bytes -> MinecraftClient.getInstance().execute(() -> {
                    try {
                        Identifier texId;
                        if (bytes != null) {
                            NativeImage img = NativeImage.read(NativeImage.Format.RGBA,
                                    new ByteArrayInputStream(bytes));
                            texId = registerTexture(uid, scaleToPixelArt(img, 16, 16));
                            AI_READY.add(uid);
                        } else {
                            texId = LOADED.getOrDefault(uid,
                                    registerTexture(uid, buildPromptTexture(uid, "", prompt, "common")));
                        }
                        LOADED.put(uid, texId);
                        onReady.accept(texId);
                    } catch (Exception e) {
                        AlchemodInit.LOG.error("[Creator] Failed to decode AI sprite", e);
                        Identifier fallback = LOADED.getOrDefault(uid,
                                registerTexture(uid, buildPromptTexture(uid, "", prompt, "common")));
                        LOADED.put(uid, fallback);
                        onReady.accept(fallback);
                    } finally {
                        PENDING_AI_DOWNLOADS.remove(uid);
                    }
                }));
    }

    public static void generateFallback(int uid, Consumer<Identifier> onReady) {
        if (LOADED.containsKey(uid)) { onReady.accept(LOADED.get(uid)); return; }
        MinecraftClient.getInstance().execute(() -> {
            Identifier id = registerTexture(uid, buildPromptTexture(uid, "", "", "common"));
            LOADED.put(uid, id);
            onReady.accept(id);
        });
    }

    public static boolean isLoaded(int uid)       { return LOADED.containsKey(uid); }
    public static Identifier getLoaded(int uid)   { return LOADED.get(uid); }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static byte[] fetchSpriteBytes(String prompt, int uid) {
        try {
            String encoded = URLEncoder.encode(
                    "16x16 pixel art minecraft item icon, " + prompt
                    + ", transparent background, centered, no text, simple flat style",
                    StandardCharsets.UTF_8).replace("+", "%20");
            String url = "https://image.pollinations.ai/prompt/" + encoded
                    + "?width=16&height=16&nologo=true&seed=" + (uid & 0xFFFF);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET().build();
            HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                AlchemodInit.LOG.warn("[Creator] Sprite HTTP {}", resp.statusCode());
                return null;
            }
            return resp.body().readAllBytes();
        } catch (Exception e) {
            AlchemodInit.LOG.warn("[Creator] Sprite download failed: {}", e.getMessage());
            return null;
        }
    }

    private static void maybeQueueAiUpgrade(int uid, String prompt) {
        if (prompt == null || prompt.isBlank()
                || AI_READY.contains(uid) || PENDING_AI_DOWNLOADS.contains(uid)) return;
        downloadSprite(prompt, uid,
                id -> AlchemodInit.LOG.info("[Creator] Upgraded Pollinations sprite ready for uid {}.", uid));
    }

    private static Map<String, Integer> extractInputColors(String inputA, String inputB) {
        Map<String, Integer> colors = new HashMap<>();
        for (String raw : new String[]{inputA, inputB}) {
            if (raw == null || raw.isBlank()) continue;
            // Accept "minecraft:foo" or bare "foo"
            String key = raw.contains(":") ? raw : "minecraft:" + raw;
            Integer color = ITEM_COLORS.get(key);
            if (color != null) colors.put(key, color);
        }
        return colors;
    }

    /**
     * Registers a texture under a stable identifier derived from the uid.
     * Using hex avoids any ambiguity about whether the number is a "slot index".
     */
    private static Identifier registerTexture(int uid, NativeImage image) {
        // Use unsigned hex so both small legacy values (0-63) and large OddityItem
        // uids (≥ 0x40000000) produce valid, distinct path segments.
        Identifier id = Identifier.of(AlchemodInit.MOD_ID,
                "dynamic/item_" + Integer.toUnsignedString(uid, 16));
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        return id;
    }

    private static NativeImage scaleToPixelArt(NativeImage src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        NativeImage scaled = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                scaled.setColorArgb(x, y, src.getColorArgb(
                        x * src.getWidth() / w, y * src.getHeight() / h));
        return scaled;
    }

    private static NativeImage buildPromptTexture(int uid, String name, String prompt, String rarity) {
        int hash = (name + "|" + prompt + "|" + rarity + "|" + uid).hashCode();
        int primary   = 0xFF000000
                | (clamp(70 + ((hash >> 16) & 0x7F)) << 16)
                | (clamp(70 + ((hash >>  8) & 0x7F)) <<  8)
                |  clamp(70 + (hash & 0x7F));
        int secondary = 0xFF000000
                | (clamp(45 + ((hash >>  9) & 0x9F)) << 16)
                | (clamp(45 + ((hash >>  3) & 0x9F)) <<  8)
                |  clamp(45 + ((hash >> 19) & 0x9F));
        return buildGlyphTexture(primary, secondary, getRarityColor(rarity));
    }

    private static NativeImage buildGlyphTexture(int primaryColor, int secondaryColor, int rarityColor) {
        int pr = (primaryColor   >> 16) & 0xFF, pg = (primaryColor   >> 8) & 0xFF, pb = primaryColor   & 0xFF;
        int sr = (secondaryColor >> 16) & 0xFF, sg = (secondaryColor >> 8) & 0xFF, sb = secondaryColor & 0xFF;
        int rr = (rarityColor    >> 16) & 0xFF, rg = (rarityColor    >> 8) & 0xFF, rb = rarityColor    & 0xFF;

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = Math.abs(x - 7.5), dy = Math.abs(y - 7.5);
                double diamond = dx + dy;
                boolean outer  = diamond <= 7.2;
                boolean inner  = diamond <= 5.1;
                boolean core   = diamond <= 2.6;
                boolean rune   = inner && !core && ((x + y) % 3 == 0 || x == 7 || x == 8 || y == 7 || y == 8);
                boolean accent = (x==4&&y==4)||(x==11&&y==4)||(x==4&&y==11)||(x==11&&y==11);

                int argb;
                if (!outer) {
                    argb = 0x00000000;
                } else if (!inner) {
                    argb = 0xFF000000 | (rr << 16) | (rg << 8) | rb;
                } else if (core) {
                    argb = 0xFF000000
                            | (clamp((pr+sr+rr)/3+28) << 16)
                            | (clamp((pg+sg+rg)/3+28) <<  8)
                            |  clamp((pb+sb+rb)/3+28);
                } else if (rune || accent) {
                    argb = 0xFF000000
                            | (clamp((sr*3+rr)/4+12) << 16)
                            | (clamp((sg*3+rg)/4+12) <<  8)
                            |  clamp((sb*3+rb)/4+12);
                } else {
                    float blend = (float)((x*2+y*3)%11)/10f;
                    argb = 0xFF000000
                            | (clamp((int)(pr*(1f-blend*0.35f)+sr*blend*0.35f)) << 16)
                            | (clamp((int)(pg*(1f-blend*0.35f)+sg*blend*0.35f)) <<  8)
                            |  clamp((int)(pb*(1f-blend*0.35f)+sb*blend*0.35f));
                }
                image.setColorArgb(x, y, argb);
            }
        }
        image.setColorArgb(7,  4, 0xFFFFFFFF);
        image.setColorArgb(8,  5, 0xFFF6F2D8);
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
        return switch (rarity == null ? "" : rarity) {
            case "uncommon"  -> 0xFF55AA55;
            case "rare"      -> 0xFF55AAAA;
            case "epic"      -> 0xFFAA55AA;
            case "legendary" -> 0xFFFFAA00;
            default          -> 0xFF9E9E9E;
        };
    }

    /** Slot-index-based fallback for legacy items that have no rarity string. */
    private static int getRarityColor(int uid) {
        return switch (uid % 5) {
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
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        return c != null ? c.copyNbt() : null;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
