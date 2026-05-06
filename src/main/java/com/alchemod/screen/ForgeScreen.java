package com.alchemod.screen;

import com.alchemod.block.ForgeBlockEntity;
import com.alchemod.network.ForgeNbtPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class ForgeScreen extends HandledScreen<ForgeScreenHandler> {

    private static final Identifier BG =
            Identifier.ofVanilla("textures/gui/container/furnace.png");

    private TextFieldWidget nameField;
    private TextFieldWidget loreField;
    private TextFieldWidget enchantField;
    private TextFieldWidget colorField;
    private ButtonWidget   applyButton;
    private ButtonWidget   nbtToggleButton;
    private boolean nbtVisible = false;

    public ForgeScreen(ForgeScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        backgroundWidth  = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;

        nbtToggleButton = ButtonWidget.builder(Text.literal("NBT"), btn -> {
            nbtVisible = !nbtVisible;
            updateFieldVisibility();
        }).dimensions(x + 150, y + 4, 24, 16).build();
        addDrawableChild(nbtToggleButton);

        int fieldWidth = 156;
        int startY = y + 85;

        nameField = new TextFieldWidget(textRenderer, x + 10, startY,         fieldWidth, 16, Text.literal("Name"));
        nameField.setMaxLength(50);
        nameField.setPlaceholder(Text.literal("Custom Name (optional)"));

        loreField = new TextFieldWidget(textRenderer, x + 10, startY + 20,    fieldWidth, 16, Text.literal("Lore"));
        loreField.setMaxLength(100);
        loreField.setPlaceholder(Text.literal("Lore line (optional)"));

        enchantField = new TextFieldWidget(textRenderer, x + 10, startY + 40, 100, 16, Text.literal("Enchant"));
        enchantField.setMaxLength(60);
        enchantField.setPlaceholder(Text.literal("sharpness 3, fire_aspect 2"));

        colorField = new TextFieldWidget(textRenderer, x + 115, startY + 40,  51, 16, Text.literal("Color"));
        colorField.setMaxLength(6);
        colorField.setPlaceholder(Text.literal("hex"));

        applyButton = ButtonWidget.builder(Text.literal("Apply"),
                btn -> applyNbtToBlockEntity()
        ).dimensions(x + 10, startY + 62, 60, 16).build();

        nameField.setVisible(false);
        loreField.setVisible(false);
        enchantField.setVisible(false);
        colorField.setVisible(false);
        applyButton.visible = false;

        addSelectableChild(nameField);
        addSelectableChild(loreField);
        addSelectableChild(enchantField);
        addSelectableChild(colorField);
        addDrawableChild(applyButton);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

        int state    = handler.getState();
        int progress = handler.getProgress();

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

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(textRenderer, title, titleX, titleY, 0x404040, false);

        int state = handler.getState();
        String status = switch (state) {
            case ForgeBlockEntity.STATE_IDLE       -> "Place two items to combine";
            case ForgeBlockEntity.STATE_PROCESSING -> "Combining...";
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
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);

        // Draw NBT fields on top of the base screen when visible
        if (nbtVisible) {
            nameField.render(ctx, mx, my, delta);
            loreField.render(ctx, mx, my, delta);
            enchantField.render(ctx, mx, my, delta);
            colorField.render(ctx, mx, my, delta);
        }

        drawMouseoverTooltip(ctx, mx, my);
    }

    private void updateFieldVisibility() {
        nameField.setVisible(nbtVisible);
        loreField.setVisible(nbtVisible);
        enchantField.setVisible(nbtVisible);
        colorField.setVisible(nbtVisible);
        applyButton.visible = nbtVisible;
    }

    private void applyNbtToBlockEntity() {
        String name = nameField.getText().trim();
        String lore = loreField.getText().trim();
        String ench = enchantField.getText().trim();
        int color = -1;
        try {
            if (!colorField.getText().isBlank()) {
                color = Integer.parseInt(colorField.getText().trim(), 16);
            }
        } catch (NumberFormatException ignored) {
            color = -1;
        }

        java.util.List<String> enchantments = ench.isBlank()
                ? java.util.List.of()
                : java.util.Arrays.stream(ench.split(","))
                        .map(String::trim)
                        .filter(v -> !v.isBlank())
                        .toList();

        // FIX: read the actual position from the ForgeBlockEntity instead of
        // hard-coding BlockPos.ORIGIN, which sent the packet to the wrong block
        // and caused a NullPointerException / "network error" in singleplayer.
        BlockPos blockPos = BlockPos.ORIGIN;
        if (handler.getInventory() instanceof ForgeBlockEntity forge) {
            blockPos = forge.getPos();
        }

        ClientPlayNetworking.send(
                new ForgeNbtPayload(blockPos, name, lore, color, enchantments, 0));
    }
}
