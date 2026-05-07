package com.alchemod.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected background-position fields of {@link HandledScreen}
 * so the client-side sprite overlay renderer can compute slot screen coordinates
 * without needing a full mixin injection.
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x") int getBackgroundX();
    @Accessor("y") int getBackgroundY();
}
