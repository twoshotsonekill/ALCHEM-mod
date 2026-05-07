package com.alchemod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class AlchemodClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // DynamicItemOverlayRenderer removed - client rendering simplified
    }
}
