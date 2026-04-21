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

        // Progress arrow — furnace sprite at u=176, v=14, 24×16 px, at gui pos (79, 34)
        int arrowFill = switch (state) {
            case ForgeBlockEntity.STATE_READY      -> 24;
            case ForgeBlockEntity.STATE_PROCESSING -> progress * 24 / 80;
            default -> 0;
        };
        if (arrowFill > 0) {
            ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                    x + 79, y + 34, 176, 14, arrowFill, 16, 256, 256);
        }
    }

    /**
     * drawForeground runs AFTER drawBackground and item rendering, using GUI-relative
     * coordinates (origin already translated to (x, y)).
     *
     * We draw only the title and our status line here.
     * The status sits at relative y=71 — safely in the 14-px gap between the bottom
     * of SLOT_B (relative y=53+16=69) and the top of the player inventory (relative y=83).
     * We suppress the default "Inventory" label (relative y=72) so it does not overlap.
     */
    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // Title
        ctx.drawText(textRenderer, title, titleX, titleY, 0x404040, false);

        // Status — relative coords (GUI origin already translated)
        int state = handler.getState();
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
        int sx = (backgroundWidth - textRenderer.getWidth(status)) / 2;
        ctx.drawText(textRenderer, status, sx, 71, col, false);

        // Intentionally NOT calling super — that would draw the "Inventory" label at
        // relative y=72, which collides with our status line at y=71.
        // Player inventory label is omitted; the contents make it self-evident.
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        drawMouseoverTooltip(ctx, mx, my);
    }
}
