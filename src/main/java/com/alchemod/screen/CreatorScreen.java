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

public class CreatorScreen extends HandledScreen<CreatorScreenHandler> {

    private static final Identifier BG =
            Identifier.ofVanilla("textures/gui/container/furnace.png");

    // Animation timer — only incremented once per render() call
    private float animTimer = 0f;

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
        animTimer += delta * 0.05f;  // advance once per frame here only
        super.render(ctx, mx, my, delta);
        drawMouseoverTooltip(ctx, mx, my);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

        int state    = handler.getState();
        int progress = handler.getProgress();

        triggerSpriteDownloadIfNeeded();

        // ── Processing particle effect ──────────────────────────────────────
        if (state == CreatorBlockEntity.STATE_PROCESSING) {
            drawProcessingEffect(ctx);
        }

        // ── Progress arrow ──────────────────────────────────────────────────
        int fill = switch (state) {
            case CreatorBlockEntity.STATE_READY      -> 24;
            case CreatorBlockEntity.STATE_PROCESSING -> progress * 24 / 100;
            default -> 0;
        };
        if (fill > 0) {
            ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                    x + 79, y + 34, 176, 14, fill, 16, 256, 256);
        }

        // ── Status text ──────────────────────────────────────────────────────
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
        int sx = x + (backgroundWidth - textRenderer.getWidth(status)) / 2;
        ctx.drawText(textRenderer, status, sx, y + 60, col, false);

        // ── Output sprite overlay (drawn on top of the slot icon) ────────────
        drawOutputSprite(ctx, state);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void triggerSpriteDownloadIfNeeded() {
        if (handler.slots.size() < 3) return;
        ItemStack out = handler.slots.get(2).getStack();
        if (out.isEmpty() || !(out.getItem() instanceof DynamicItem dynItem)) return;

        int slot = dynItem.getSlotIndex();
        if (RuntimeTextureManager.isLoaded(slot)) return;

        NbtComponent nbtComp = out.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComp == null) return;
        NbtCompound tag = nbtComp.copyNbt();
        String spritePrompt = tag.getString("creator_sprite");
        if (spritePrompt.isBlank()) return;

        AlchemodInit.LOG.info("[Creator] Triggering sprite download for slot {}: {}", slot, spritePrompt);
        RuntimeTextureManager.downloadSprite(spritePrompt, slot, texId ->
                AlchemodInit.LOG.info("[Creator] Sprite ready for slot {}", slot));
    }

    private void drawProcessingEffect(DrawContext ctx) {
        int cx = x + 91;
        int cy = y + 42;
        for (int i = 0; i < 6; i++) {
            double angle  = animTimer + i * (Math.PI / 3.0);
            double radius = 14 + Math.sin(animTimer * 2 + i) * 3;
            int px = cx + (int)(Math.cos(angle) * radius);
            int py = cy + (int)(Math.sin(angle) * radius);
            int alpha = 100 + (int)(100 * Math.abs(Math.sin(animTimer + i)));
            ctx.fill(px, py, px + 2, py + 2, (alpha << 24) | 0xAA44FF);
        }
        int glowAlpha = (int)(80 * Math.abs(Math.sin(animTimer)));
        ctx.fill(x + 79, y + 34, x + 79 + 24, y + 34 + 16, (glowAlpha << 24) | 0xAA44FF);
    }

    private void drawOutputSprite(DrawContext ctx, int state) {
        if (state != CreatorBlockEntity.STATE_READY) return;
        int lastSlot = handler.getLastCreatedSlot();
        if (lastSlot < 0) return;
        if (!RuntimeTextureManager.isLoaded(lastSlot)) return;

        Identifier texId = RuntimeTextureManager.getLoaded(lastSlot);
        // Draw over the output slot position (134, 35) — same as the slot in the handler
        ctx.drawTexture(RenderLayer::getGuiTextured, texId,
                x + 134, y + 35, 0, 0, 16, 16, 16, 16);
    }
}
