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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
            Map.entry("minecraft:diamond", 0xFF00FFFF),
            Map.entry("minecraft:emerald", 0xFF00FF00),
            Map.entry("minecraft:redstone", 0xFFFF0000),
            Map.entry("minecraft:gold_ingot", 0xFFFFD700),
            Map.entry("minecraft:iron_ingot", 0xFFCCCCCC),
            Map.entry("minecraft:lapis_lazuli", 0xFF0000AA),
            Map.entry("minecraft:quartz", 0xFFEEEEEE),
            Map.entry("minecraft:blaze_rod", 0xFFFF6600),
            Map.entry("minecraft:ghast_tear", 0xFFFFFFFF),
            Map.entry("minecraft:ender_pearl", 0xFF666688),
            Map.entry("minecraft:slime_ball", 0xFF00FF00),
            Map.entry("minecraft:spider_eye", 0xFF660000),
            Map.entry("minecraft:bone", 0xFFEEEEEE),
            Map.entry("minecraft:feather", 0xFFFFFFFF),
            Map.entry("minecraft:flint", 0xFF333333),
            Map.entry("minecraft:glass", 0xFFAAFFFF),
            Map.entry("minecraft:obsidian", 0xFF000066),
            Map.entry("minecraft:nether_star", 0xFFFFFFAA),
            Map.entry("minecraft:shulker_shell", 0xFFAA6699),
            Map.entry("minecraft:prismarine_shard", 0xFF00AAAA),
            Map.entry("minecraft:prismarine_crystals", 0xFF55FFFF),
            Map.entry("minecraft:heart_of_the_sea", 0xFF0000FF),
            Map.entry("minecraft:totem_of_undying", 0xFFFFAA00),
            Map.entry("minecraft:netherite_ingot", 0xFF333333),
            Map.entry("minecraft:copper_ingot", 0xFFFF8844),
            Map.entry("minecraft:amethyst_shard", 0xFFAA00FF),
            Map.entry("minecraft:music_disc_11", 0xFF000000),
            Map.entry("minecraft:fire_charge", 0xFFFF4400),
            Map.entry("minecraft:experience_bottle", 0xFF00AAFF)
    );

    private RuntimeTextureManager() {
    }

    public static Identifier ensureForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!(stack.getItem() instanceof OddityItem) && !(stack.getItem() instanceof DynamicItem)) {
            return null;
        }

        NbtCompound tag = getCustomData(stack);
        if (tag == null) {
            return null;
        }

        int uid = resolveUid(stack, tag);
        Identifier loaded = LOADED.get(uid);
        if (loaded != null) {
            return loaded;
        }

        String spriteCommands = tag.getString("creator_sprite_commands");
        if (spriteCommands != null && !spriteCommands.isBlank()) {
            NativeImage image = SpriteCommandRenderer.render(spriteCommands);
            if (image != null) {
                Identifier textureId = registerTexture(uid, image);
                LOADED.put(uid, textureId);
                AI_READY.add(uid);
                return textureId;
            }
            AlchemodInit.LOG.warn("[Creator] Sprite commands present but render failed for uid {}.", uid);
        }

        Identifier textureId = registerTexture(uid, buildFallbackTexture(tag, uid));
        LOADED.put(uid, textureId);
        AI_READY.add(uid);
        return textureId;
    }

    public static boolean isLoaded(int uid) {
        return LOADED.containsKey(uid);
    }

    public static Identifier getLoaded(int uid) {
        return LOADED.get(uid);
    }

    private static void maybeQueueAiUpgrade(int uid, String prompt) {
        if (prompt == null || prompt.isBlank() || AI_READY.contains(uid) || PENDING_AI_DOWNLOADS.contains(uid)) {
            return;
        }
        if (!PENDING_AI_DOWNLOADS.add(uid)) {
            return;
        }

        CompletableFuture.supplyAsync(() -> fetchSpriteBytes(prompt, uid))
                .thenAccept(bytes -> MinecraftClient.getInstance().execute(() -> {
                    try {
                        if (bytes == null) {
                            return;
                        }
                        NativeImage image = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(bytes));
                        Identifier upgraded = registerTexture(uid, scaleToPixelArt(image, 16, 16));
                        LOADED.put(uid, upgraded);
                        AI_READY.add(uid);
                    } catch (Exception e) {
                        AlchemodInit.LOG.warn("[Creator] Failed to decode AI sprite: {}", e.getMessage());
                    } finally {
                        PENDING_AI_DOWNLOADS.remove(uid);
                    }
                }));
    }

    private static byte[] fetchSpriteBytes(String prompt, int uid) {
        try {
            String encoded = URLEncoder.encode(
                    "16x16 pixel art minecraft item icon, " + prompt
                            + ", transparent background, centered, no text, simple flat style",
                    StandardCharsets.UTF_8).replace("+", "%20");
            String url = "https://image.pollinations.ai/prompt/" + encoded
                    + "?width=16&height=16&nologo=true&seed=" + (uid & 0xFFFF);

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

    private static Identifier registerTexture(int uid, NativeImage image) {
        Identifier id = Identifier.of(
                AlchemodInit.MOD_ID,
                "dynamic/item_" + Integer.toUnsignedString(uid, 16));
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        return id;
    }

    private static NativeImage scaleToPixelArt(NativeImage src, int width, int height) {
        if (src.getWidth() == width && src.getHeight() == height) {
            return src;
        }
        NativeImage scaled = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                scaled.setColorArgb(x, y, src.getColorArgb(
                        x * src.getWidth() / width,
                        y * src.getHeight() / height));
            }
        }
        return scaled;
    }

    private static NativeImage buildFallbackTexture(NbtCompound tag, int uid) {
        return ProceduralSpriteGenerator.generate(
                tag.getString("creator_item_type"),
                tag.getString("creator_rarity"),
                tag.getString("creator_name"),
                tag.getString("creator_effects"),
                tag.getString("creator_input_a"),
                tag.getString("creator_input_b"),
                uid);
    }

    private static int extractInputColor(String rawId, int fallback) {
        if (rawId == null || rawId.isBlank()) {
            return fallback;
        }
        String id = rawId.contains(":") ? rawId : "minecraft:" + rawId;
        return ITEM_COLORS.getOrDefault(id, fallback);
    }

    private static NativeImage buildGlyphTexture(int primaryColor, int secondaryColor, int rarityColor, int uid) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        clearTransparent(image);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = Math.abs(x - 7.5);
                double dy = Math.abs(y - 7.5);
                double diamond = dx + dy;

                if (diamond > 7.2) {
                    continue;
                }
                if (diamond > 5.1) {
                    image.setColorArgb(x, y, rarityColor);
                    continue;
                }

                boolean rune = diamond > 2.6 && (((x + y + uid) % 3 == 0) || x == 7 || x == 8 || y == 7 || y == 8);
                if (diamond <= 2.6) {
                    image.setColorArgb(x, y, mix(mix(primaryColor, secondaryColor, 0.5f), 0xFFFFFFFF, 0.25f));
                } else if (rune) {
                    image.setColorArgb(x, y, mix(secondaryColor, rarityColor, 0.55f));
                } else {
                    float blend = ((x * 3 + y * 5 + uid) & 7) / 7.0f;
                    image.setColorArgb(x, y, mix(primaryColor, secondaryColor, blend * 0.45f));
                }
            }
        }

        image.setColorArgb(7, 4, 0xFFFFFFFF);
        image.setColorArgb(8, 5, 0xFFF6F2D8);
        image.setColorArgb(10, 9, 0xCCFFFFFF);
        return image;
    }

    private static void clearTransparent(NativeImage image) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setColorArgb(x, y, 0x00000000);
            }
        }
    }

    private static int getRarityColor(String rarity) {
        return switch (rarity) {
            case "uncommon" -> 0xFF55AA55;
            case "rare" -> 0xFF55AAAA;
            case "epic" -> 0xFFAA55AA;
            case "legendary" -> 0xFFFFAA00;
            default -> 0xFF9E9E9E;
        };
    }

    private static String normalise(String value) {
        return value == null ? "" : value.toLowerCase().replace("minecraft:", "").trim();
    }

    public static int resolveUid(ItemStack stack, NbtCompound tag) {
        if (tag.contains("creator_slot")) {
            return tag.getInt("creator_slot");
        }
        if (stack.getItem() instanceof DynamicItem dynamicItem) {
            return dynamicItem.getSlotIndex();
        }

        int hash = (tag.getString("creator_name")
                + "|" + tag.getString("creator_sprite")
                + "|" + tag.getString("creator_rarity")
                + "|" + tag.getString("creator_item_type"))
                .hashCode();
        return 0x40000000 | (hash & 0x3FFFFFFF);
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
        return (clamp(outA) << 24) | (clamp(outR) << 16) | (clamp(outG) << 8) | clamp(outB);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static NbtCompound getCustomData(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null ? customData.copyNbt() : null;
    }
}
