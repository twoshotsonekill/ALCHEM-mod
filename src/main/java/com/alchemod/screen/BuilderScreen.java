package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import com.alchemod.block.BuilderBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class BuilderScreen extends HandledScreen<BuilderScreenHandler> {

    private static final Identifier BG = Identifier.ofVanilla("textures/gui/container/furnace.png");

    private TextFieldWidget promptField;
    private float animTimer = 0f;

    public BuilderScreen(BuilderScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;

        // Create text input field
        promptField = new TextFieldWidget(textRenderer, x + 10, y + 30, 156, 20, Text.literal("Prompt"));
        promptField.setMaxLength(200);
        promptField.setPlaceholder(Text.literal("What should I build?"));
        addSelectableChild(promptField);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        animTimer += delta * 0.05f;
        super.render(ctx, mx, my, delta);
        drawMouseoverTooltip(ctx, mx, my);

        // Draw prompt field
        promptField.render(ctx, mx, my, delta);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

        int state = handler.getState();
        int progress = handler.getProgress();

        // Progress arrow
        int fill = switch (state) {
            case BuilderBlockEntity.STATE_COMPLETE   -> 24;
            case BuilderBlockEntity.STATE_PROCESSING,
                 BuilderBlockEntity.STATE_BUILDING   -> progress * 24 / 100;
            default -> 0;
        };
        if (fill > 0) {
            ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                    x + 79, y + 60, 176, 14, fill, 16, 256, 256);
        }
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // Title
        ctx.drawText(textRenderer, title, titleX, titleY, 0x404040, false);

        // Show status below progress bar
        int state = handler.getState();
        String status = switch (state) {
            case BuilderBlockEntity.STATE_IDLE       -> "Enter a prompt and wait...";
            case BuilderBlockEntity.STATE_PROCESSING -> "Generating structure...";
            case BuilderBlockEntity.STATE_BUILDING   -> "Building...";
            case BuilderBlockEntity.STATE_COMPLETE   -> "Structure complete!";
            case BuilderBlockEntity.STATE_ERROR      -> "Error - check console";
            default -> "";
        };
        int color = switch (state) {
            case BuilderBlockEntity.STATE_PROCESSING,
                 BuilderBlockEntity.STATE_BUILDING   -> 0xAA44FF;
            case BuilderBlockEntity.STATE_COMPLETE   -> 0x22EE88;
            case BuilderBlockEntity.STATE_ERROR      -> 0xFF3333;
            default -> 0x888888;
        };
        int sx = (backgroundWidth - textRenderer.getWidth(status)) / 2;
        ctx.drawText(textRenderer, status, sx, 80, color, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (promptField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // Enter key to submit
        if (keyCode == 257 && !promptField.getText().isEmpty()) {
            submitPrompt();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return promptField.charTyped(chr, modifiers);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        promptField.setFocused(mx >= promptField.getX() && mx < promptField.getX() + promptField.getWidth()
                && my >= promptField.getY() && my < promptField.getY() + promptField.getHeight());
    }

    private void submitPrompt() {
        String prompt = promptField.getText().trim();
        if (!prompt.isEmpty()) {
            // Send to server via packet or similar
            AlchemodInit.LOG.info("Builder prompt: {}", prompt);
            promptField.setText("");
        }
    }
}
