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

public class ForgeScreen extends HandledScreen<ForgeScreenHandler> {

    private static final Identifier BG =
            Identifier.ofVanilla("textures/gui/container/furnace.png");

    // ── Palette — dark teal/amber alchemist theme ─────────────────────────────
    private static final int COL_BG_DARK   = 0xCC0D1117; // near-black panel
    private static final int COL_BG_MID    = 0xAA151C24; // slightly lighter section
    private static final int COL_ACCENT    = 0xCCD97706; // amber accent line
    private static final int COL_ACCENT2   = 0x552BA8A3; // teal glow hint
    private static final int COL_TEXT_DIM  = 0x6B7280;
    private static final int COL_TEXT_BODY = 0xD1D5DB;

    // ── Manual override panel (hidden by default) ─────────────────────────────
    private TextFieldWidget nameField;
    private TextFieldWidget loreField;
    private TextFieldWidget enchantField;
    private ButtonWidget    applyButton;
    private ButtonWidget    overrideToggle;
    private boolean         overrideVisible = false;

    // ── Subtle animation timer ────────────────────────────────────────────────
    private float animTimer = 0f;

    public ForgeScreen(ForgeScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        backgroundWidth  = 176;
        backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;

        // Override-panel toggle (top-right corner, small)
        overrideToggle = ButtonWidget
                .builder(Text.literal("✎"), btn -> {
                    overrideVisible = !overrideVisible;
                    updateFieldVisibility();
                })
                .dimensions(x + 152, y + 4, 18, 14)
                .build();
        addDrawableChild(overrideToggle);

        // Override fields — start hidden
        int fy = y + 84;
        nameField = new TextFieldWidget(textRenderer, x + 10, fy, 156, 14, Text.literal("Name"));
        nameField.setMaxLength(50);
        nameField.setPlaceholder(Text.literal("Custom name"));

        loreField = new TextFieldWidget(textRenderer, x + 10, fy + 18, 156, 14, Text.literal("Lore"));
        loreField.setMaxLength(100);
        loreField.setPlaceholder(Text.literal("Lore line"));

        enchantField = new TextFieldWidget(textRenderer, x + 10, fy + 36, 120, 14, Text.literal("Enchant"));
        enchantField.setMaxLength(80);
        enchantField.setPlaceholder(Text.literal("sharpness 3, fire_aspect 2"));

        applyButton = ButtonWidget
                .builder(Text.literal("Apply"), btn -> sendOverridePayload())
                .dimensions(x + 134, fy + 36, 32, 14)
                .build();

        addSelectableChild(nameField);
        addSelectableChild(loreField);
        addSelectableChild(enchantField);
        addDrawableChild(applyButton);
        updateFieldVisibility();
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        animTimer += delta * 0.04f;
        super.render(ctx, mx, my, delta);
        if (overrideVisible) {
            nameField.render(ctx, mx, my, delta);
            loreField.render(ctx, mx, my, delta);
            enchantField.render(ctx, mx, my, delta);
        }
        drawMouseoverTooltip(ctx, mx, my);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        // Vanilla furnace sprite as structural skeleton
        ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);

        // ── Dark overlay panels ───────────────────────────────────────────────

        // Title bar
        ctx.fill(x + 6, y + 3, x + 170, y + 19, COL_BG_DARK);
        // Left amber accent stripe on title bar
        ctx.fill(x + 6, y + 3, x + 8, y + 19, COL_ACCENT);

        // Status / info band (below arrow, above player inv)
        ctx.fill(x + 6, y + 55, x + 170, y + 76, COL_BG_MID);
        // Teal underline at bottom of info band
        ctx.fill(x + 6, y + 74, x + 170, y + 76, COL_ACCENT2);

        // Slot highlight boxes (INPUT A, INPUT B)
        ctx.fill(x + 52, y + 14, x + 72, y + 34, 0x22D97706);
        ctx.fill(x + 52, y + 50, x + 72, y + 70, 0x22D97706);

        // Output slot glow
        int outGlow = 0x112BA8A3;
        if (handler.getState() == ForgeBlockEntity.STATE_READY) {
            float pulse = (float)(0.45 + 0.4 * Math.abs(Math.sin(animTimer)));
            outGlow = (int)(pulse * 255) << 24 | 0x00D97706;
        }
        ctx.fill(x + 112, y + 32, x + 132, y + 52, outGlow);

        // Progress arrow fill (vanilla sprite row 14, col 176)
        int state    = handler.getState();
        int progress = handler.getProgress();
        int fill = switch (state) {
            case ForgeBlockEntity.STATE_READY      -> 24;
            case ForgeBlockEntity.STATE_PROCESSING -> progress * 24 / 80;
            default -> 0;
        };
        if (fill > 0) {
            ctx.drawTexture(RenderLayer::getGuiTextured, BG,
                    x + 79, y + 34, 176, 14, fill, 16, 256, 256);
        }

        // Processing spark animation
        if (state == ForgeBlockEntity.STATE_PROCESSING) {
            drawSparks(ctx);
        }

        // Override-panel background
        if (overrideVisible) {
            ctx.fill(x + 6, y + 78, x + 170, y + 160, 0xEE0D1117);
            ctx.fill(x + 6, y + 78, x + 8,   y + 160, COL_ACCENT);
        }
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mx, int my) {
        // Title
        ctx.drawText(textRenderer, title, titleX, 7, 0xFEF3C7, false);

        // Status line
        int state = handler.getState();
        String status = switch (state) {
            case ForgeBlockEntity.STATE_IDLE       -> "Slot two items to transmute";
            case ForgeBlockEntity.STATE_PROCESSING -> "Transmuting\u2026";
            case ForgeBlockEntity.STATE_READY      -> "Transmutation complete!";
            case ForgeBlockEntity.STATE_ERROR      -> "Transmutation failed";
            default -> "";
        };
        int statusColor = switch (state) {
            case ForgeBlockEntity.STATE_PROCESSING -> 0xFCD34D;
            case ForgeBlockEntity.STATE_READY      -> 0x6EE7B7;
            case ForgeBlockEntity.STATE_ERROR      -> 0xF87171;
            default                                -> COL_TEXT_DIM;
        };
        int sx = (backgroundWidth - textRenderer.getWidth(status)) / 2;
        ctx.drawText(textRenderer, status, sx, 62, statusColor, false);

        // Result name/rarity hint (shown when output is ready)
        if (state == ForgeBlockEntity.STATE_READY
                && handler.getInventory() instanceof ForgeBlockEntity be) {
            String rname = be.getLastResultName();
            if (!rname.isBlank()) {
                String rarityColor = rarityHex(be.getLastResultRarity());
                String hint = rarityColor + rname;
                int hx = (backgroundWidth - textRenderer.getWidth(hint)) / 2;
                ctx.drawText(textRenderer, hint, hx, 70, 0xFFFFFF, false);
            }
        }

        // Tiny override label when panel is open
        if (overrideVisible) {
            ctx.drawText(textRenderer, "Manual override", 11, 80, COL_TEXT_DIM, false);
        }
    }

    // ── Sparks ────────────────────────────────────────────────────────────────

    private void drawSparks(DrawContext ctx) {
        int cx = x + 91, cy = y + 42;
        for (int i = 0; i < 5; i++) {
            double angle  = animTimer * 1.3 + i * (Math.PI * 2.0 / 5);
            double radius = 9 + Math.sin(animTimer * 3 + i) * 3;
            int px = cx + (int)(Math.cos(angle) * radius);
            int py = cy + (int)(Math.sin(angle) * radius);
            int alpha = 80 + (int)(120 * Math.abs(Math.sin(animTimer * 2 + i)));
            ctx.fill(px, py, px + 2, py + 2, (alpha << 24) | 0xD97706);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateFieldVisibility() {
        boolean v = overrideVisible;
        nameField.setVisible(v);
        loreField.setVisible(v);
        enchantField.setVisible(v);
        applyButton.visible = v;
    }

    private void sendOverridePayload() {
        String name   = nameField.getText().trim();
        String lore   = loreField.getText().trim();
        String ench   = enchantField.getText().trim();

        java.util.List<String> enchList = ench.isBlank()
                ? java.util.List.of()
                : java.util.Arrays.stream(ench.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();

        ClientPlayNetworking.send(new ForgeNbtPayload(
                BlockPos.ORIGIN, name, lore, -1, enchList, 0));
    }

    private static String rarityHex(String rarity) {
        return switch (rarity != null ? rarity : "") {
            case "uncommon"  -> "§a";
            case "rare"      -> "§b";
            case "epic"      -> "§d";
            case "legendary" -> "§6§l";
            default          -> "§f";
        };
    }
}
