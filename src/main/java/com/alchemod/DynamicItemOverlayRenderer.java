package com.alchemod;

import com.alchemod.creator.DynamicItem;
import com.alchemod.item.OddityItem;
import com.alchemod.mixin.HandledScreenAccessor;
import com.alchemod.resource.RuntimeTextureManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class DynamicItemOverlayRenderer {

    private DynamicItemOverlayRenderer() {
    }

    public static void renderHotbar(DrawContext context, MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int startX = (width - 182) / 2 + 3;
        int startY = height - 22 + 3;

        for (int index = 0; index < 9; index++) {
            drawSprite(context, client.player.getInventory().getStack(index), startX + index * 20, startY);
        }

        ItemStack offhand = client.player.getInventory().getStack(40);
        if (!offhand.isEmpty()) {
            drawSprite(context, offhand, (width - 182) / 2 + 195, startY);
        }
    }

    public static void renderScreen(Screen screen, DrawContext context) {
        if (!(screen instanceof HandledScreen<?> handledScreen)) {
            return;
        }

        HandledScreenAccessor accessor = (HandledScreenAccessor) handledScreen;
        int bgX = accessor.getBackgroundX();
        int bgY = accessor.getBackgroundY();

        for (Slot slot : handledScreen.getScreenHandler().slots) {
            if (!slot.isEnabled()) {
                continue;
            }
            drawSprite(context, slot.getStack(), bgX + slot.x, bgY + slot.y);
        }
    }

    private static void drawSprite(DrawContext context, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (!(stack.getItem() instanceof DynamicItem) && !(stack.getItem() instanceof OddityItem)) {
            return;
        }

        Identifier texture = RuntimeTextureManager.ensureForStack(stack);
        if (texture == null) {
            return;
        }

        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 250.0f);
        context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, 0.0f, 0.0f, 16, 16, 16, 16);
        context.getMatrices().pop();
    }
}
