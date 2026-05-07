package com.alchemod.screen;

import com.alchemod.block.ForgeBlockEntity;
import com.alchemod.network.ForgeNbtPayload;
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

import java.util.Arrays;
import java.util.List;

public class ForgeScreen extends HandledScreen<ForgeScreenHandler> {

    private static final Identifier BG = Identifier.ofVanilla("textures/gui/container/furnace.png");

    private TextFieldWidget nameField;
    private TextFieldWidget loreField;
    private TextFieldWidget enchantField;
    private ButtonWidget applyButton;
    private ButtonWidget toggleButton;
    private boolean editorVisible;

    public ForgeScreen(ForgeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;

        int fieldY = y + 84;
        toggleButton = ButtonWidget.builder(Text.literal("Edit"), button -> {
            editorVisible = !editorVisible;
            updateEditorVisibility();
        }).dimensions(x + 130, y + 4, 40, 14).build();
        addDrawableChild(toggleButton);

        nameField = new TextFieldWidget(textRenderer, x + 8, fieldY, 160, 14, Text.literal("Name"));
        nameField.setPlaceholder(Text.literal("Custom name"));
        addDrawableChild(nameField);

        loreField = new TextFieldWidget(textRenderer, x + 8, fieldY + 18, 160, 14, Text.literal("Lore"));
        loreField.setPlaceholder(Text.literal("Lore line"));
        addDrawableChild(loreField);

        enchantField = new TextFieldWidget(textRenderer, x + 8, fieldY + 36, 110, 14, Text.literal("Enchantments"));
        enchantField.setPlaceholder(Text.literal("sharpness 3, fire_aspect 2"));
        addDrawableChild(enchantField);

        applyButton = ButtonWidget.builder(Text.literal("Apply"), button -> sendOverrides())
                .dimensions(x + 122, fieldY + 36, 46, 14)
                .build();
        addDrawableChild(applyButton);

        updateEditorVisibility();
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

        context.fill(x + 6, y + 4, x + 170, y + 20, 0xCC0F172A);
        context.fill(x + 6, y + 58, x + 170, y + 76, 0xAA111827);
        context.fill(x + 6, y + 58, x + 8, y + 76, 0xCCB45309);

        int fill = switch (handler.getState()) {
            case ForgeBlockEntity.STATE_READY -> 24;
            case ForgeBlockEntity.STATE_PROCESSING -> handler.getProgress() * 24 / 80;
            default -> 0;
        };
        if (fill > 0) {
            context.drawTexture(RenderLayer::getGuiTextured, BG,
                    x + 79, y + 34, 176, 14, fill, 16, 256, 256);
        }

        if (editorVisible) {
            context.fill(x + 6, y + 80, x + 170, y + 140, 0xE510172A);
            context.fill(x + 6, y + 80, x + 8, y + 140, 0xCC2563EB);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0xF8FAFC, false);

        String status = switch (handler.getState()) {
            case ForgeBlockEntity.STATE_IDLE -> "Insert two items to transmute";
            case ForgeBlockEntity.STATE_PROCESSING -> "Transmuting...";
            case ForgeBlockEntity.STATE_READY -> "Transmutation complete";
            case ForgeBlockEntity.STATE_ERROR -> "Transmutation failed";
            default -> "";
        };
        int statusColor = switch (handler.getState()) {
            case ForgeBlockEntity.STATE_PROCESSING -> 0xFACC15;
            case ForgeBlockEntity.STATE_READY -> 0x4ADE80;
            case ForgeBlockEntity.STATE_ERROR -> 0xF87171;
            default -> 0x9CA3AF;
        };
        int statusX = (backgroundWidth - textRenderer.getWidth(status)) / 2;
        context.drawText(textRenderer, status, statusX, 63, statusColor, false);

        if (editorVisible) {
            context.drawText(textRenderer, "Manual overrides", 10, 82, 0xCBD5E1, false);
        }
    }

    private void updateEditorVisibility() {
        boolean visible = editorVisible;
        nameField.setVisible(visible);
        nameField.setEditable(visible);
        loreField.setVisible(visible);
        loreField.setEditable(visible);
        enchantField.setVisible(visible);
        enchantField.setEditable(visible);
        applyButton.visible = visible;
        applyButton.active = visible;
    }

    private void sendOverrides() {
        List<String> enchantments = Arrays.stream(enchantField.getText().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();

        ClientPlayNetworking.send(new ForgeNbtPayload(
                BlockPos.ORIGIN,
                nameField.getText().trim(),
                loreField.getText().trim(),
                -1,
                enchantments,
                0));
    }
}
