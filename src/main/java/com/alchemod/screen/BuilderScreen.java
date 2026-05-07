package com.alchemod.screen;

import com.alchemod.block.BuilderBlockEntity;
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
    private ButtonWidget buildButton;

    public BuilderScreen(BuilderScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;

        promptField = new TextFieldWidget(textRenderer, x + 8, y + 22, 160, 18, Text.literal("Prompt"));
        promptField.setMaxLength(160);
        promptField.setPlaceholder(Text.literal("Describe the structure to build"));
        addDrawableChild(promptField);
        setInitialFocus(promptField);

        buildButton = ButtonWidget.builder(Text.literal("Build"), button -> sendPrompt())
                .dimensions(x + 122, y + 44, 46, 18)
                .build();
        addDrawableChild(buildButton);
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
        context.fill(x + 6, y + 4, x + 170, y + 20, 0xCC111827);
        context.fill(x + 6, y + 58, x + 170, y + 78, 0xAA1F2937);
        context.fill(x + 6, y + 58, x + 8, y + 78, 0xCC2563EB);

        int fill = switch (handler.getState()) {
            case BuilderBlockEntity.STATE_COMPLETE -> 24;
            case BuilderBlockEntity.STATE_PROCESSING, BuilderBlockEntity.STATE_BUILDING ->
                    handler.getProgress() * 24 / 100;
            default -> 0;
        };
        if (fill > 0) {
            context.drawTexture(RenderLayer::getGuiTextured, BG,
                    x + 79, y + 34, 176, 14, fill, 16, 256, 256);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0xE2E8F0, false);

        String status = switch (handler.getState()) {
            case BuilderBlockEntity.STATE_IDLE -> "Enter a prompt and click Build";
            case BuilderBlockEntity.STATE_PROCESSING -> "Thinking...";
            case BuilderBlockEntity.STATE_BUILDING -> "Placing blocks...";
            case BuilderBlockEntity.STATE_COMPLETE -> "Build complete";
            case BuilderBlockEntity.STATE_ERROR -> "Build failed";
            default -> "";
        };
        int statusColor = switch (handler.getState()) {
            case BuilderBlockEntity.STATE_PROCESSING, BuilderBlockEntity.STATE_BUILDING -> 0x60A5FA;
            case BuilderBlockEntity.STATE_COMPLETE -> 0x4ADE80;
            case BuilderBlockEntity.STATE_ERROR -> 0xF87171;
            default -> 0x94A3B8;
        };
        int statusX = (backgroundWidth - textRenderer.getWidth(status)) / 2;
        context.drawText(textRenderer, status, statusX, 63, statusColor, false);

        context.drawText(textRenderer, "Prompt", 8, 12, 0xCBD5E1, false);
    }

    private void sendPrompt() {
        String prompt = promptField.getText().trim();
        if (prompt.isBlank()) {
            return;
        }
        BlockPos pos = handler.getBlockPos() != null ? handler.getBlockPos() : BlockPos.ORIGIN;
        ClientPlayNetworking.send(new BuilderPromptPayload(pos, prompt));
    }
}
