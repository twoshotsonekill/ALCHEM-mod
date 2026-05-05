package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import com.alchemod.block.BuilderBlockEntity;
import com.alchemod.network.BuilderPromptPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public class BuilderScreen extends HandledScreen<BuilderScreenHandler> {

    private static final Identifier BG = Identifier.ofVanilla("textures/gui/container/furnace.png");
    private static final int PANEL_BG = 0xCC111827;
    private static final int PANEL_SOFT = 0xAA1F2937;
    private static final int ACCENT = 0xCC22C55E;

    private TextFieldWidget promptField;

    public BuilderScreen(BuilderScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        promptField = new TextFieldWidget(textRenderer, x + 14, y + 31, 148, 18, Text.literal("Builder Prompt"));
        promptField.setMaxLength(512);
        promptField.setDrawsBackground(false);
        promptField.setPlaceholder(Text.literal("Describe the subject, mood, materials, scale, and scene"));
        promptField.setFocused(true);
        addSelectableChild(promptField);
        setInitialFocus(promptField);
        setFocused(promptField);
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        if (promptField != null && !promptField.isFocused()) {
            promptField.setFocused(true);
            setFocused(promptField);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        promptField.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiTextured, BG,
                x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

        context.fill(x + 8, y + 4, x + backgroundWidth - 8, y + 24, PANEL_BG);
        context.fill(x + 12, y + 27, x + backgroundWidth - 12, y + 52, PANEL_SOFT);
        context.fill(x + 12, y + 54, x + backgroundWidth - 12, y + 78, PANEL_BG);
        context.fill(x + 12, y + 27, x + 14, y + 52, ACCENT);
        context.fill(x + 12, y + 54, x + 14, y + 78, ACCENT);
        context.fill(x + 20, y + 54, x + 68, y + 74, 0xE5162230);
        context.fill(x + 108, y + 54, x + 156, y + 74, 0xE5162230);
        context.fill(x + 24, y + 34, x + 152, y + 35, 0x55FFFFFF);
        context.fill(x + 20, y + 47, x + 156, y + 48, 0x2234D399);

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
        context.drawText(textRenderer, title, titleX, titleY, 0xF3F4F6, false);

        String status = switch (handler.getState()) {
            case BuilderBlockEntity.STATE_IDLE -> "Describe a build and press Enter";
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
        context.drawText(textRenderer, status, textX, 58, color, false);
        context.drawText(textRenderer, "Prompt", 18, 19, 0x9CA3AF, false);
        String summary = getBuildPlanSummary();
        context.drawText(textRenderer,
                summary.isBlank()
                        ? "Text-only builder: scene prompts are always active."
                        : summary,
                18, 66, 0xA7F3D0, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            promptField.setFocused(true);
            setFocused(promptField);
            return true;
        }

        if (promptField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == 257 || keyCode == 335) {
            submitPrompt();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!promptField.isFocused()) {
            promptField.setFocused(true);
            setFocused(promptField);
        }
        return promptField.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = promptField.mouseClicked(mouseX, mouseY, button);
        if (!handled) {
            handled = super.mouseClicked(mouseX, mouseY, button);
        }
        promptField.setFocused(true);
        setFocused(promptField);
        return handled;
    }

    private void submitPrompt() {
        String prompt = promptField.getText().trim();
        if (prompt.isEmpty()) {
            return;
        }

        BlockPos blockPos = handler.getBlockPos() != null ? handler.getBlockPos() : BlockPos.ORIGIN;
        ClientPlayNetworking.send(new BuilderPromptPayload(blockPos, prompt));
        promptField.setText("");
    }

    private String getBuildPlanSummary() {
        BlockPos blockPos = handler.getBlockPos();
        if (blockPos == null || client == null || client.world == null) {
            return "";
        }

        if (client.world.getBlockEntity(blockPos) instanceof BuilderBlockEntity builder) {
            return textRenderer.trimToWidth(builder.getLastBuildPlanSummary(), 160);
        }

        return "";
    }
}
