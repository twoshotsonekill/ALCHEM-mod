package com.alchemod;

import com.alchemod.creator.DynamicItem;
import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.resource.DynamicItemBuiltinRenderer;
import com.alchemod.screen.BuilderScreen;
import com.alchemod.screen.CreatorScreen;
import com.alchemod.screen.ForgeScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

@Environment(EnvType.CLIENT)
public class AlchemodClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(AlchemodInit.FORGE_HANDLER, ForgeScreen::new);
        HandledScreens.register(AlchemodInit.CREATOR_HANDLER, CreatorScreen::new);
        HandledScreens.register(AlchemodInit.BUILDER_HANDLER, BuilderScreen::new);

        for (int slot = 0; slot < DynamicItemRegistry.POOL_SIZE; slot++) {
            DynamicItem item = DynamicItemRegistry.getSlot(slot);
            if (item != null) {
                BuiltinItemRendererRegistry.INSTANCE.register(item, DynamicItemBuiltinRenderer.INSTANCE);
            }
        }
    }
}
