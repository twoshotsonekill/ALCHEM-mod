package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TransmuterScreen extends HandledScreen<TransmuterScreenHandler> {

    private static final Identifier TEXTURE =
            Identifier.ofVanilla("textures/gui/container/furnace.png");

    public TransmuterScreen(TransmuterScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth  = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) /2;
        int y = (height - backgroundHeight) /2;
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0,
                backgroundWidth, backgroundHeight, 256, 256);

        // Draw progress arrow
        TransmuterScreenHandler h = this.handler;
        if (h.getInventory() instanceof com.alchemod.block.TransmuterBlockEntity transmuter) {
            int progress = transmuter.getMaxProgress() >0
                    ? (int) (transmuter.getDelegate().get(1) / (float) transmuter.getMaxProgress() * 24)
                    :0;
            if (progress >0) {
                context.drawTexture(RenderLayer::getGuiTextured, TEXTURE,
                        x + 79, y + 34, 176, 14,
                        progress + 1, 16, 256, 256);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) /2;
        titleY = 6;
        playerInventoryTitleY = backgroundHeight - 94;
    }
}
