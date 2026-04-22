package com.alchemod;

import com.alchemod.screen.BuilderScreen;
import com.alchemod.screen.CreatorScreen;
import com.alchemod.screen.ForgeScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class AlchemodClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(AlchemodInit.FORGE_HANDLER, ForgeScreen::new);
        HandledScreens.register(AlchemodInit.CREATOR_HANDLER, CreatorScreen::new);
        HandledScreens.register(AlchemodInit.BUILDER_HANDLER, BuilderScreen::new);
    }
}
