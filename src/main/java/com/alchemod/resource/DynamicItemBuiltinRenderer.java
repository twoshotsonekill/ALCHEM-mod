package com.alchemod.resource;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class DynamicItemBuiltinRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    public static final DynamicItemBuiltinRenderer INSTANCE = new DynamicItemBuiltinRenderer();

    private DynamicItemBuiltinRenderer() {
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        Identifier texture = RuntimeTextureManager.ensureForStack(stack);
        if (texture == null) {
            return;
        }

        matrices.push();
        matrices.scale(1.0f, 1.0f, 0.08f);
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        MatrixStack.Entry entry = matrices.peek();

        renderFace(entry, consumer, light, overlay == 0 ? OverlayTexture.DEFAULT_UV : overlay,
                -0.5f, -0.5f, 0.0f,
                0.5f, 0.5f, 0.0f,
                0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
        renderFace(entry, consumer, light, overlay == 0 ? OverlayTexture.DEFAULT_UV : overlay,
                0.5f, -0.5f, -0.02f,
                -0.5f, 0.5f, -0.02f,
                0.0f, 0.0f, 1.0f, 1.0f, -1.0f);

        matrices.pop();
    }

    private static void renderFace(MatrixStack.Entry entry, VertexConsumer consumer, int light, int overlay,
                                   float minX, float minY, float z1,
                                   float maxX, float maxY, float z2,
                                   float minU, float minV, float maxU, float maxV, float normalZ) {
        consumer.vertex(entry, minX, minY, z1).color(255, 255, 255, 255).texture(minU, maxV).overlay(overlay).light(light).normal(entry, 0.0f, 0.0f, normalZ);
        consumer.vertex(entry, minX, maxY, z1).color(255, 255, 255, 255).texture(minU, minV).overlay(overlay).light(light).normal(entry, 0.0f, 0.0f, normalZ);
        consumer.vertex(entry, maxX, maxY, z2).color(255, 255, 255, 255).texture(maxU, minV).overlay(overlay).light(light).normal(entry, 0.0f, 0.0f, normalZ);
        consumer.vertex(entry, maxX, minY, z2).color(255, 255, 255, 255).texture(maxU, maxV).overlay(overlay).light(light).normal(entry, 0.0f, 0.0f, normalZ);
    }
}
