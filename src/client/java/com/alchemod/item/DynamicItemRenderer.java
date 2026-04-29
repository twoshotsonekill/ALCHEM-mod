package com.alchemod.item;

import com.alchemod.AlchemodInit;
import com.alchemod.creator.DynamicItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.item.ModelTransformationMode;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * BuiltinItemRenderer for all {@link DynamicItem} instances.
 *
 * <p>Renders the item as a flat 1×1 textured quad in the XY plane, identical
 * in screen-space to the default {@code item/generated} flat items.  The quad
 * uses the runtime texture registered by {@link RuntimeTextureManager} so the
 * procedural or AI-generated sprite is visible everywhere — inventory, hotbar,
 * item frames, and first/third-person hand.
 *
 * <p>Falls back to a solid colour tinted by rarity if the texture has not been
 * loaded yet (first frame after creation).
 */
@Environment(EnvType.CLIENT)
public final class DynamicItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    public static final DynamicItemRenderer INSTANCE = new DynamicItemRenderer();

    private DynamicItemRenderer() {}

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay) {

        // Kick off texture generation if needed (no-op after first call).
        RuntimeTextureManager.ensureForStack(stack);

        NbtCompound tag = DynamicItem.getCustomData(stack);
        int slot = tag != null ? tag.getInt("creator_slot") : -1;
        Identifier texId = slot >= 0 ? RuntimeTextureManager.getLoaded(slot) : null;

        matrices.push();

        // Minecraft item transforms put the origin at the item centre.
        // Shift so the quad spans [0,1]×[0,1] centred on the origin.
        matrices.translate(-0.5f, -0.5f, -0.001f);

        if (texId != null) {
            drawTexturedQuad(matrices, vertexConsumers, texId, light, overlay);
        } else {
            // Rarity tint as placeholder until texture is ready
            int color = rarityColor(tag);
            drawColoredQuad(matrices, vertexConsumers, color, light, overlay);
        }

        matrices.pop();
    }

    // ── Quad drawing ──────────────────────────────────────────────────────────

    /**
     * Draws a 1×1 textured quad in the XY plane using the provided texture.
     * Two triangles (CCW) with correct UV, light, and overlay.
     */
    private static void drawTexturedQuad(MatrixStack matrices, VertexConsumerProvider vcp,
            Identifier texId, int light, int overlay) {
        RenderLayer layer = RenderLayer.getEntityCutoutNoCull(texId);
        VertexConsumer buf = vcp.getBuffer(layer);
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f nrm = matrices.peek().getNormalMatrix();

        // Quad: two CCW triangles sharing the diagonal (0,0)→(1,1)
        //  v3(0,1) ── v2(1,1)
        //   │   ╲      │
        //  v0(0,0) ── v1(1,0)
        vertex(buf, pos, nrm, 0, 0, 0, 1, light, overlay);
        vertex(buf, pos, nrm, 1, 0, 1, 1, light, overlay);
        vertex(buf, pos, nrm, 1, 1, 1, 0, light, overlay);
        vertex(buf, pos, nrm, 0, 1, 0, 0, light, overlay);
    }

    private static void vertex(VertexConsumer buf, Matrix4f pos, Matrix3f nrm,
            float x, float y, float u, float v, int light, int overlay) {
        buf.vertex(pos, x, y, 0f)
           .color(255, 255, 255, 255)
           .texture(u, v)
           .overlay(overlay)
           .light(light)
           .normal(nrm, 0f, 0f, 1f);
    }

    /**
     * Solid coloured quad used as a one-frame placeholder while the real texture loads.
     */
    private static void drawColoredQuad(MatrixStack matrices, VertexConsumerProvider vcp,
            int argb, int light, int overlay) {
        // Reuse the entity solid layer — any opaque layer works here
        VertexConsumer buf = vcp.getBuffer(RenderLayer.getEntitySolid(
                Identifier.of(AlchemodInit.MOD_ID, "textures/item/dynamic_placeholder.png")));
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f nrm = matrices.peek().getNormalMatrix();

        int r = (argb >> 16) & 0xFF;
        int g = (argb >>  8) & 0xFF;
        int b =  argb        & 0xFF;

        buf.vertex(pos, 0, 0, 0).color(r,g,b,255).texture(0,1).overlay(overlay).light(light).normal(nrm,0,0,1);
        buf.vertex(pos, 1, 0, 0).color(r,g,b,255).texture(1,1).overlay(overlay).light(light).normal(nrm,0,0,1);
        buf.vertex(pos, 1, 1, 0).color(r,g,b,255).texture(1,0).overlay(overlay).light(light).normal(nrm,0,0,1);
        buf.vertex(pos, 0, 1, 0).color(r,g,b,255).texture(0,0).overlay(overlay).light(light).normal(nrm,0,0,1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int rarityColor(NbtCompound tag) {
        if (tag == null) return 0x9E9E9E;
        return switch (normalise(tag.getString("creator_rarity"))) {
            case "uncommon"  -> 0x55AA55;
            case "rare"      -> 0x55AAFF;
            case "epic"      -> 0xAA55FF;
            case "legendary" -> 0xFFAA00;
            default          -> 0x9E9E9E;
        };
    }

    private static String normalise(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }
}
