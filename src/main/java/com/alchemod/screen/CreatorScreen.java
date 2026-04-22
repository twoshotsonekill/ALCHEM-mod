package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import com.alchemod.block.CreatorBlockEntity;
import com.alchemod.creator.DynamicItem;
import com.alchemod.resource.RuntimeTextureManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class CreatorScreen extends HandledScreen<CreatorScreenHandler> {

    private static final Identifier BG =
            Identifier.ofVanilla("textures/gui/container/furnace.png");

    private float animTimer = 0f;

    private static final Map<String, Integer> ITEM_COLORS = new HashMap<>();
    static {
        ITEM_COLORS.put("minecraft:diamond", 0xFF00FFFF);
        ITEM_COLORS.put("minecraft:emerald", 0xFF00FF00);
        ITEM_COLORS.put("minecraft:redstone", 0xFFFF0000);
        ITEM_COLORS.put("minecraft:gold_ingot", 0xFFFFD700);
        ITEM_COLORS.put("minecraft:iron_ingot", 0xFFCCCCCC);
        ITEM_COLORS.put("minecraft:lapis_lazuli", 0xFF0000AA);
        ITEM_COLORS.put("minecraft:quartz", 0xFFEEEEEE);
        ITEM_COLORS.put("minecraft:blaze_rod", 0xFFFF6600);
        ITEM_COLORS.put("minecraft:ghast_tear", 0xFFFFFFFF);
        ITEM_COLORS.put("minecraft:ender_pearl", 0xFF666688);
        ITEM_COLORS.put("minecraft:slime_ball", 0xFF00FF00);
        ITEM_COLORS.put("minecraft:spider_eye", 0xFF660000);
        ITEM_COLORS.put("minecraft:bone", 0xFFEEEEEE);
        ITEM_COLORS.put("minecraft:feather", 0xFFFFFFFF);
        ITEM_COLORS.put("minecraft:flint", 0xFF333333);
        ITEM_COLORS.put("minecraft:glass", 0xFFAAFFFF);
        ITEM_COLORS.put("minecraft:obsidian", 0xFF000066);
        ITEM_COLORS.put("minecraft:nether_star", 0xFFFFFFAA);
        ITEM_COLORS.put("minecraft:shulker_shell", 0xFFAA6699);
        ITEM_COLORS.put("minecraft:prismarine_shard", 0xFF00AAAA);
        ITEM_COLORS.put("minecraft:prismarine_crystals", 0xFF55FFFF);
        ITEM_COLORS.put("minecraft:heart_of_the_sea", 0xFF0000FF);
        ITEM_COLORS.put("minecraft:totem_of_undying", 0xFFFFAA00);
        ITEM_COLORS.put("minecraft:netherite_ingot", 0xFF333333);
        ITEM_COLORS.put("minecraft:copper_ingot", 0xFFFF8844);
        ITEM_COLORS.put("minecraft:amethyst_shard", 0xFFAA00FF);
        ITEM_COLORS.put("minecraft:music_disc_11", 0xFF000000);
        ITEM_COLORS.put("minecraft:fire_charge", 0xFFFF4400);
        ITEM_COLORS.put("minecraft:experience_bottle", 0xFF00AAFF);
    }

    public CreatorScreen(CreatorScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        backgroundWidth  = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        animTimer += delta * 0.05f;
        super.render(ctx, mx, my, delta);
        drawMouseoverTooltip(ctx, mx, my);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

        int state    = handler.getState();
        int progress = handler.getProgress();

        triggerSpriteGenerationIfNeeded();

        if (state == CreatorBlockEntity.STATE_PROCESSING) {
            drawProcessingEffect(ctx);
        }

        // Progress arrow at gui pos (79, 34)
        int fill = switch (state) {
            case CreatorBlockEntity.STATE_READY      -> 24;
            case CreatorBlockEntity.STATE_PROCESSING -> progress * 24 / 100;
            default -> 0;
        };
        if (fill > 0) {
            ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                    x + 79, y + 34, 176, 14, fill, 16, 256, 256);
        }

        // Output slot sprite overlay (absolute coords match slot at 116, 35)
        drawOutputSprite(ctx, state);
    }

    /**
     * drawForeground runs AFTER drawBackground and item rendering, in GUI-relative coords.
     *
     * Status sits at relative y=71 — the 14-px gap between SLOT_B bottom (y=69)
     * and player inventory top (y=83).  We suppress the default "Inventory" label
     * (relative y=72) to avoid overlap.
     */
    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // Title
        ctx.drawText(textRenderer, title, titleX, titleY, 0x404040, false);

        // Status — relative coords
        int state = handler.getState();
        String status = switch (state) {
            case CreatorBlockEntity.STATE_IDLE       -> "Place two items to create";
            case CreatorBlockEntity.STATE_PROCESSING -> "Inventing\u2026";
            case CreatorBlockEntity.STATE_READY      -> "Take your creation!";
            case CreatorBlockEntity.STATE_ERROR      -> "Creation failed \u2014 check logs";
            default -> "";
        };
        int col = switch (state) {
            case CreatorBlockEntity.STATE_PROCESSING -> 0xAA44FF;
            case CreatorBlockEntity.STATE_READY      -> 0x22EE88;
            case CreatorBlockEntity.STATE_ERROR      -> 0xFF3333;
            default -> 0x888888;
        };
        int sx = (backgroundWidth - textRenderer.getWidth(status)) / 2;
        ctx.drawText(textRenderer, status, sx, 71, col, false);

        // Intentionally NOT calling super to suppress the "Inventory" label at
        // relative y=72, which would overlap with our status line at y=71.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void triggerSpriteGenerationIfNeeded() {
        if (handler.slots.size() < 3) return;
        ItemStack out = handler.slots.get(2).getStack();
        if (out.isEmpty() || !(out.getItem() instanceof DynamicItem dynItem)) return;

        int slot = dynItem.getSlotIndex();
        if (RuntimeTextureManager.isLoaded(slot)) return;

        NbtComponent nbtComp = out.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComp == null) return;
        NbtCompound tag = nbtComp.copyNbt();

        // Get input item IDs for color-based texture generation
        String inputA = tag.getString("creator_input_a");
        String inputB = tag.getString("creator_input_b");
        String spritePrompt = tag.getString("creator_sprite");
        String rarity = tag.getString("creator_rarity");

        AlchemodInit.LOG.info("[Creator] Generating texture for slot {} from inputs: {} + {}", slot, inputA, inputB);

        // Build color map from input items
        Map<String, Integer> inputColors = new HashMap<>();
        if (inputA != null && !inputA.isBlank() && ITEM_COLORS.containsKey(inputA)) {
            inputColors.put(inputA, ITEM_COLORS.get(inputA));
        }
        if (inputB != null && !inputB.isBlank() && ITEM_COLORS.containsKey(inputB)) {
            inputColors.put(inputB, ITEM_COLORS.get(inputB));
        }

        // Generate texture from input colors if we have them
        if (!inputColors.isEmpty()) {
            RuntimeTextureManager.generateFromInputs(slot, inputColors, spritePrompt,
                    texId -> AlchemodInit.LOG.info("[Creator] Input-based texture ready for slot {}", slot));
        } else {
            // Fall back to AI sprite generation
            if (spritePrompt != null && !spritePrompt.isBlank()) {
                RuntimeTextureManager.downloadSprite(spritePrompt, slot,
                        texId -> AlchemodInit.LOG.info("[Creator] AI sprite ready for slot {}", slot));
            } else {
                // Generate fallback procedural texture
                RuntimeTextureManager.generateFallback(slot,
                        texId -> AlchemodInit.LOG.info("[Creator] Fallback texture ready for slot {}", slot));
            }
        }
    }

    private void drawProcessingEffect(DrawContext ctx) {
        int cx = x + 91;
        int cy = y + 35;
        for (int i = 0; i < 6; i++) {
            double angle  = animTimer + i * (Math.PI / 3.0);
            double radius = 14 + Math.sin(animTimer * 2 + i) * 3;
            int px = cx + (int)(Math.cos(angle) * radius);
            int py = cy + (int)(Math.sin(angle) * radius);
            int alpha = 100 + (int)(100 * Math.abs(Math.sin(animTimer + i)));
            ctx.fill(px, py, px + 2, py + 2, (alpha << 24) | 0xAA44FF);
        }
        int glowAlpha = (int)(80 * Math.abs(Math.sin(animTimer)));
        ctx.fill(x + 79, y + 34, x + 103, y + 50, (glowAlpha << 24) | 0xAA44FF);
    }

    private void drawOutputSprite(DrawContext ctx, int state) {
        if (state != CreatorBlockEntity.STATE_READY) return;
        int lastSlot = handler.getLastCreatedSlot();
        if (lastSlot < 0 || !RuntimeTextureManager.isLoaded(lastSlot)) return;

        Identifier texId = RuntimeTextureManager.getLoaded(lastSlot);
        ctx.drawTexture(RenderLayer::getGuiTextured, texId,
                x + 116, y + 35, 0, 0, 16, 16, 16, 16);
    }
}
