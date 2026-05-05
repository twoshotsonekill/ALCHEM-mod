package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class InfuserScreen extends HandledScreen<InfuserScreenHandler> {

    private static final Identifier TEXTURE =
            Identifier.ofVanilla("textures/gui/container/furnace.png");

    public InfuserScreen(InfuserScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth  = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) /2;
        int y = (height - backgroundHeight) /2;
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0.0f, 0.0f,
                backgroundWidth, backgroundHeight, 256, 256);

        // Draw progress arrow
        InfuserScreenHandler h = this.handler;
        if (h.getInventory() instanceof com.alchemod.block.InfuserBlockEntity infuser) {
            int progress = infuser.getMaxProgress() > 0
                    ? (int) (infuser.getDelegate().get(1) / (float) infuser.getMaxProgress() * 24)
                    : 0;
            if (progress > 0) {
                context.drawTexture(RenderLayer::getGuiTextured, TEXTURE,
                        x + 79, y + 34, 176.0f, 14.0f,
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
