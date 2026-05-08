package com.alchemod.mixin;

import com.alchemod.creator.DynamicItem;
import com.alchemod.item.OddityItem;
import com.alchemod.resource.RuntimeTextureManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Inject(
            method = "renderItem(Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/item/ModelTransformationMode;"
                    + "II"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;"
                    + "Lnet/minecraft/world/World;"
                    + "I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void alchemod$renderGeneratedItem(
            ItemStack stack,
            ModelTransformationMode mode,
            int light,
            int overlay,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            World world,
            int seed,
            CallbackInfo ci) {

        if (!(stack.getItem() instanceof OddityItem) && !(stack.getItem() instanceof DynamicItem)) {
            return;
        }

        Identifier texture = RuntimeTextureManager.ensureForStack(stack);
        if (texture == null) {
            return;
        }

        matrices.push();
        matrices.translate(-0.5f, -0.5f, -0.5f);
        drawTexturedQuad(matrices, vertexConsumers, texture, light, overlay);
        matrices.pop();
        ci.cancel();
    }

    private static void drawTexturedQuad(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            Identifier texture,
            int light,
            int overlay
    ) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        Matrix4f position = matrices.peek().getPositionMatrix();

        vertex(buffer, position, 0, 0, 0, 0, 1, light, overlay);
        vertex(buffer, position, 1, 0, 0, 1, 1, light, overlay);
        vertex(buffer, position, 1, 1, 0, 1, 0, light, overlay);
        vertex(buffer, position, 0, 1, 0, 0, 0, light, overlay);
    }

    private static void vertex(
            VertexConsumer buffer,
            Matrix4f position,
            float x,
            float y,
            float z,
            float u,
            float v,
            int light,
            int overlay
    ) {
        buffer.vertex(position, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(0f, 0f, 1f);
    }
}
