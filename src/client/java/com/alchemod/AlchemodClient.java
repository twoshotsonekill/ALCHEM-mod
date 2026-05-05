package com.alchemod;

import com.alchemod.screen.BuilderScreen;
import com.alchemod.screen.CreatorScreen;
import com.alchemod.screen.ForgeScreen;
import com.alchemod.screen.InfuserScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;

@Environment(EnvType.CLIENT)
public class AlchemodClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(AlchemodInit.FORGE_HANDLER, ForgeScreen::new);
        HandledScreens.register(AlchemodInit.CREATOR_HANDLER, CreatorScreen::new);
        HandledScreens.register(AlchemodInit.BUILDER_HANDLER, BuilderScreen::new);
        HandledScreens.register(AlchemodInit.INFUSER_HANDLER, InfuserScreen::new);

        // Transparent blocks need their render layer declared client-side.
        BlockRenderLayerMap.INSTANCE.putBlock(
                AlchemodInit.ALCHEMICAL_GLASS_BLOCK, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(
                AlchemodInit.ETHER_CRYSTAL_BLOCK, RenderLayer.getTranslucent());

        // Dynamic item sprites are handled by ItemRendererMixin — no BIER needed.
    }
}
