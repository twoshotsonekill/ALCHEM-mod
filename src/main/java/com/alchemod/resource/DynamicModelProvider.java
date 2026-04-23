package com.alchemod.resource;

import com.alchemod.creator.DynamicItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;

/**
 * Client-side helpers used by the Creator screen to draw the live generated
 * sprite in the GUI. Item rendering falls back to packaged item models on
 * modern Fabric versions where the old builtin item renderer hook was removed.
 */
@Environment(EnvType.CLIENT)
public class DynamicModelProvider {

    /**
     * Draws a dynamic item's sprite into a DrawContext at the given screen pixel.
     * Falls back to the item's default renderer if sprite isn't ready yet.
     */
    public static void drawDynamicSprite(net.minecraft.client.gui.DrawContext ctx,
            DynamicItem item, int x, int y) {
        int slot = item.getSlotIndex();
        if (RuntimeTextureManager.isLoaded(slot)) {
            Identifier texId = RuntimeTextureManager.getLoaded(slot);
// Draw the 16x16 sprite scaled to 16x16 in GUI space
            ctx.drawTexture(RenderLayer::getGuiTextured, texId, x, y, 0, 0, 16, 16, 16, 16);
        }
        // If not loaded yet, the slot renders its placeholder model (solid colour block)
    }
}
