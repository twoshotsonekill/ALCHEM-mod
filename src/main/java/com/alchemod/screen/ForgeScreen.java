package com.alchemod.screen;

import com.alchemod.block.ForgeBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ForgeScreen extends HandledScreen<ForgeScreenHandler> {

    private static final Identifier BG =
            Identifier.ofVanilla("textures/gui/container/furnace.png");

    public ForgeScreen(ForgeScreenHandler handler, PlayerInventory inv, Text title) {
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
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        // Background panel
        ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

        int state    = handler.getState();
        int progress = handler.getProgress();

        // Progress arrow — furnace sprite at u=176, v=14, 24×16 px
        // Sits between the input slot (56,17) and output slot (116,35) at gui pos (79,34)
        int maxProg   = 80;
        int arrowFill = switch (state) {
            case ForgeBlockEntity.STATE_READY      -> 24;
            case ForgeBlockEntity.STATE_PROCESSING -> progress * 24 / maxProg;
            default -> 0;
        };
        if (arrowFill > 0) {
            ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                    x + 79, y + 34, 176, 14, arrowFill, 16, 256, 256);
        }

        // Status line below the GUI
        String status = switch (state) {
            case ForgeBlockEntity.STATE_IDLE       -> "Place two items to combine";
            case ForgeBlockEntity.STATE_PROCESSING -> "Combining\u2026";
            case ForgeBlockEntity.STATE_READY      -> "Take your result!";
            case ForgeBlockEntity.STATE_ERROR      -> "AI error \u2014 check logs";
            default -> "";
        };
        int col = switch (state) {
            case ForgeBlockEntity.STATE_PROCESSING -> 0x3388FF;
            case ForgeBlockEntity.STATE_READY      -> 0x22AA44;
            case ForgeBlockEntity.STATE_ERROR      -> 0xCC2222;
            default -> 0x666666;
        };
        int sx = x + (backgroundWidth - textRenderer.getWidth(status)) / 2;
        ctx.drawText(textRenderer, status, sx, y + 60, col, false);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        drawMouseoverTooltip(ctx, mx, my);
    }
}
