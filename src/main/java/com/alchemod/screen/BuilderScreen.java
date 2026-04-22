package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import com.alchemod.block.BuilderBlockEntity;
import com.alchemod.network.BuilderModePayload;
import com.alchemod.network.BuilderPromptPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class BuilderScreen extends HandledScreen<BuilderScreenHandler> {

    private static final Identifier BG = Identifier.ofVanilla("textures/gui/container/furnace.png");

    private TextFieldWidget promptField;
    private ButtonWidget modeToggleButton;
    private boolean isTextMode;

    public BuilderScreen(BuilderScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        isTextMode = handler.getMode() == BuilderBlockEntity.MODE_TEXT;

        modeToggleButton = ButtonWidget.builder(Text.empty(), button -> toggleMode())
                .dimensions(x + 98, y + 4, 76, 20)
                .build();
        updateModeButton();
        addDrawableChild(modeToggleButton);

        promptField = new TextFieldWidget(textRenderer, x + 10, y + 30, 156, 18, Text.literal("Builder Prompt"));
        promptField.setMaxLength(280);
        promptField.setPlaceholder(Text.literal("Describe a large structure, mood, and materials"));
        addSelectableChild(promptField);
        setInitialFocus(promptField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (isTextMode) {
            promptField.render(context, mouseX, mouseY, delta);
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiTextured, BG,
                x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

        int state = handler.getState();
        int progress = handler.getProgress();
        int fill = switch (state) {
            case BuilderBlockEntity.STATE_COMPLETE -> 24;
            case BuilderBlockEntity.STATE_PROCESSING, BuilderBlockEntity.STATE_BUILDING -> progress * 24 / 100;
            default -> 0;
        };
        if (fill > 0) {
            context.drawTexture(RenderLayer::getGuiTextured, BG,
                    x + 79, y + 34, 176, 14, fill, 16, 256, 256);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0x404040, false);

        String status = switch (handler.getState()) {
            case BuilderBlockEntity.STATE_IDLE -> isTextMode
                    ? "Describe a large build and press Enter"
                    : "Insert two items to inspire a large build";
            case BuilderBlockEntity.STATE_PROCESSING -> "Planning the structure...";
            case BuilderBlockEntity.STATE_BUILDING -> "Placing blocks...";
            case BuilderBlockEntity.STATE_COMPLETE -> "Build complete";
            case BuilderBlockEntity.STATE_ERROR -> "Build failed - check the logs";
            default -> "";
        };
        int color = switch (handler.getState()) {
            case BuilderBlockEntity.STATE_PROCESSING, BuilderBlockEntity.STATE_BUILDING -> 0xAA44FF;
            case BuilderBlockEntity.STATE_COMPLETE -> 0x22EE88;
            case BuilderBlockEntity.STATE_ERROR -> 0xFF3333;
            default -> 0x888888;
        };
        int textX = (backgroundWidth - textRenderer.getWidth(status)) / 2;
        context.drawText(textRenderer, status, textX, 55, color, false);
        context.drawText(textRenderer,
                isTextMode ? "Try prompts like tower, shrine, ruin, hall, bridge, or fortress."
                        : "Item mode blends both inputs into a larger themed landmark.",
                8, 66, 0x666666, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTextMode && promptField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (isTextMode && (keyCode == 257 || keyCode == 335)) {
            submitPrompt();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return isTextMode && promptField.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isTextMode && promptField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(promptField);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleMode() {
        isTextMode = !isTextMode;
        updateModeButton();

        BlockPos blockPos = handler.getBlockPos();
        if (blockPos == null) {
            AlchemodInit.LOG.warn("[BuilderScreen] Missing block position for mode toggle");
            return;
        }

        int mode = isTextMode ? BuilderBlockEntity.MODE_TEXT : BuilderBlockEntity.MODE_BLOCK;
        ClientPlayNetworking.send(new BuilderModePayload(blockPos, mode));
    }

    private void updateModeButton() {
        if (modeToggleButton != null) {
            modeToggleButton.setMessage(Text.literal(isTextMode ? "Mode: TEXT" : "Mode: ITEMS"));
        }
    }

    private void submitPrompt() {
        String prompt = promptField.getText().trim();
        if (prompt.isEmpty()) {
            return;
        }

        BlockPos blockPos = handler.getBlockPos();
        if (blockPos == null) {
            AlchemodInit.LOG.warn("[BuilderScreen] Missing block position for prompt send");
            return;
        }

        ClientPlayNetworking.send(new BuilderPromptPayload(blockPos, prompt));
        promptField.setText("");
    }
}
