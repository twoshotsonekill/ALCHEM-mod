package com.alchemod.mixin;

import com.alchemod.creator.DynamicItem;
import com.alchemod.resource.ProceduralSpriteGenerator;
import com.alchemod.resource.RuntimeTextureManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts rendering of all {@link DynamicItem} instances at the lowest
 * level of the item rendering pipeline so the runtime-generated sprite is
 * displayed in every context: GUI inventory, hotbar, hand (first/third person),
 * item frames, dropped items, and anything else that calls
 * {@code ItemRenderer.renderItem}.
 *
 * <p>When the runtime texture is ready we draw a flat 1×1 quad using
 * {@code EntityCutoutNoCull} (supports transparency, uses arbitrary textures
 * outside the atlas). When it is not yet loaded we let the vanilla placeholder
 * model render instead — the question-mark textures are always present so
 * there is never a missing-texture pink square.
 */
@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Inject(
        method = "renderItem(Lnet/minecraft/item/ItemStack;"
               + "Lnet/minecraft/client/render/model/json/ModelTransformationMode;"
               + "ZLnet/minecraft/client/util/math/MatrixStack;"
               + "Lnet/minecraft/client/render/VertexConsumerProvider;"
               + "IILnet/minecraft/client/render/model/BakedModel;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void alchemod$renderDynamicItem(
            ItemStack stack,
            ModelTransformationMode mode,
            boolean leftHanded,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay,
            BakedModel model,
            CallbackInfo ci) {

        if (!(stack.getItem() instanceof DynamicItem)) return;

        // Trigger lazy texture generation (no-op once loaded).
        RuntimeTextureManager.ensureForStack(stack);

        NbtCompound tag = DynamicItem.getCustomData(stack);
        int slot = tag != null ? tag.getInt("creator_slot") : -1;
        Identifier texId = slot >= 0 ? RuntimeTextureManager.getLoaded(slot) : null;

        // If texture not yet loaded, let the vanilla placeholder render.
        if (texId == null) return;

        matrices.push();

        // Apply the per-context item transform (GUI, firstPerson, ground, etc.)
        // then shift origin to the item centre exactly as vanilla does.
        model.getTransformation()
             .getTransformation(mode)
             .apply(leftHanded, matrices);
        matrices.translate(-0.5f, -0.5f, -0.5f);

        drawTexturedQuad(matrices, vertexConsumers, texId, light, overlay);

        matrices.pop();
        ci.cancel();
    }

    // ── Quad geometry ─────────────────────────────────────────────────────────

    /**
     * Draws a 1×1 quad in the XY plane at z=0 using the supplied texture.
     * Two triangles in CCW order (front face = +Z normal).
     * UV origin is top-left so the image renders the right way up.
     *
     * Vertex order: bottom-left → bottom-right → top-right → top-left
     * (standard Minecraft quad winding).
     */
    private static void drawTexturedQuad(MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            Identifier texId, int light, int overlay) {

        VertexConsumer buf = vertexConsumers.getBuffer(
                RenderLayer.getEntityCutoutNoCull(texId));

        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f nrm = matrices.peek().getNormalMatrix();

        // x, y, z  |  u, v  (UV: 0,0 = top-left; 1,1 = bottom-right)
        vertex(buf, pos, nrm, 0, 0, 0, 0, 1, light, overlay); // bottom-left
        vertex(buf, pos, nrm, 1, 0, 0, 1, 1, light, overlay); // bottom-right
        vertex(buf, pos, nrm, 1, 1, 0, 1, 0, light, overlay); // top-right
        vertex(buf, pos, nrm, 0, 1, 0, 0, 0, light, overlay); // top-left
    }

    private static void vertex(VertexConsumer buf,
            Matrix4f pos, Matrix3f nrm,
            float x, float y, float z,
            float u, float v,
            int light, int overlay) {
        buf.vertex(pos, x, y, z)
           .color(255, 255, 255, 255)
           .texture(u, v)
           .overlay(overlay)
           .light(light)
           .normal(nrm, 0f, 0f, 1f);
    }
}
