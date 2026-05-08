package com.alchemod.mixin;

import net.minecraft.registry.SimpleRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleRegistry.class)
public interface SimpleRegistryAccessor {
    @Accessor("frozen")
    boolean alchemod$isFrozen();

    @Accessor("frozen")
    void alchemod$setFrozen(boolean frozen);
}
