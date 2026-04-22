package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import com.alchemod.block.BuilderBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.alchemod.network.BuilderPromptPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class PromptBuilderScreen extends HandledScreen<BuilderScreenHandler> {

    private static final Identifier BG = Identifier.ofVanilla("textures/gui/container/furnace.png");

    private TextFieldWidget promptField;
    private ButtonWidget modeToggle;
    private ButtonWidget generateButton;
    private float animTimer = 0f;
    private boolean textMode = false;

    public PromptBuilderScreen(BuilderScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;

        promptField = new TextFieldWidget(textRenderer, x + 10, y + 30, 156, 20, Text.literal("Prompt"));
        promptField.setMaxLength(200);
        promptField.setPlaceholder(Text.literal("What should I build?"));
        promptField.setVisible(false);
        addSelectableChild(promptField);

        modeToggle = ButtonWidget.builder(Text.literal("Blocks"), btn -> {
            textMode = !textMode;
            btn.setMessage(Text.literal(textMode ? "Text" : "Blocks"));
            promptField.setVisible(textMode);
        }).dimensions(x + 130, y + 8, 44, 16).build();
        addDrawableChild(modeToggle);

        generateButton = ButtonWidget.builder(Text.literal("Generate"), btn -> {
            if (textMode && !promptField.getText().isBlank()) {
                submitPrompt();
            }
        }).dimensions(x + 10, y + 55, 80, 16).build();
        generateButton.visible = false;
        addDrawableChild(generateButton);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        animTimer += delta * 0.05f;
        super.render(ctx, mx, my, delta);
        drawMouseoverTooltip(ctx, mx, my);

        if (textMode) {
            promptField.render(ctx, mx, my, delta);
            generateButton.visible = !promptField.getText().isBlank();
        } else {
            generateButton.visible = false;
        }
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(textRenderer, title, titleX, titleY, 0x404040, false);

        int state = handler.getState();
        String status = switch (state) {
            case BuilderBlockEntity.STATE_IDLE       -> textMode ? "Enter a prompt and generate" : "Place two items";
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
        if (keyCode == 257 && textMode && !promptField.getText().isEmpty()) {
            submitPrompt();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return promptField.charTyped(chr, modifiers);
    }

    private void submitPrompt() {
        String prompt = promptField.getText().trim();
        if (!prompt.isEmpty()) {
            BlockPos pos = handler.getPos();
            if (pos != null && ClientPlayNetworking.canSend(BuilderPromptPayload.ID)) {
                BuilderPromptPayload payload = new BuilderPromptPayload(pos, prompt);
                ClientPlayNetworking.send(payload);
            }
            AlchemodInit.LOG.info("Builder prompt sent: {}", prompt);
            promptField.setText("");
        }
    }
}