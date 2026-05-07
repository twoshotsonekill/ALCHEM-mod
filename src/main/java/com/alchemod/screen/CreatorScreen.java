package com.alchemod.screen;

import com.alchemod.block.CreatorBlockEntity;
import com.alchemod.creator.DynamicItem;
import com.alchemod.item.OddityItem;
import com.alchemod.resource.RuntimeTextureManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class CreatorScreen extends HandledScreen<CreatorScreenHandler> {

    private static final Identifier BG = Identifier.ofVanilla("textures/gui/container/furnace.png");

    public CreatorScreen(CreatorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiTextured, BG,
                x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

        context.fill(x + 8, y + 4, x + 168, y + 22, 0xCC2A112C);
        context.fill(x + 8, y + 58, x + 168, y + 78, 0xAA3B1D40);
        context.fill(x + 8, y + 58, x + 10, y + 78, 0xCCEC4899);

        int fill = switch (handler.getState()) {
            case CreatorBlockEntity.STATE_READY -> 24;
            case CreatorBlockEntity.STATE_PROCESSING -> handler.getProgress() * 24 / 100;
            default -> 0;
        };
        if (fill > 0) {
            context.drawTexture(RenderLayer::getGuiTextured, BG,
                    x + 79, y + 34, 176, 14, fill, 16, 256, 256);
        }

        ItemStack output = handler.slots.size() > 2 ? handler.slots.get(2).getStack() : ItemStack.EMPTY;
        if (!output.isEmpty()
                && (output.getItem() instanceof OddityItem || output.getItem() instanceof DynamicItem)) {
            RuntimeTextureManager.ensureForStack(output);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0xFCE7F3, false);

        String status = switch (handler.getState()) {
            case CreatorBlockEntity.STATE_IDLE -> "Place two items to create";
            case CreatorBlockEntity.STATE_PROCESSING -> "Inventing...";
            case CreatorBlockEntity.STATE_READY -> "Take your creation";
            case CreatorBlockEntity.STATE_ERROR -> "Creation failed";
            default -> "";
        };
        int statusColor = switch (handler.getState()) {
            case CreatorBlockEntity.STATE_PROCESSING -> 0xE879F9;
            case CreatorBlockEntity.STATE_READY -> 0x4ADE80;
            case CreatorBlockEntity.STATE_ERROR -> 0xF87171;
            default -> 0x94A3B8;
        };
        int statusX = (backgroundWidth - textRenderer.getWidth(status)) / 2;
        context.drawText(textRenderer, status, statusX, 63, statusColor, false);

        int uid = handler.getLastCreatedSlot();
        if (handler.getState() == CreatorBlockEntity.STATE_READY && uid >= 0 && RuntimeTextureManager.isLoaded(uid)) {
            Identifier texture = RuntimeTextureManager.getLoaded(uid);
            context.drawTexture(RenderLayer::getGuiTextured, texture,
                    x + 116, y + 35, 0.0f, 0.0f, 16, 16, 16, 16);
        }
    }
}
