package com.alchemod.script;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

public final class EffectResolver {

    private EffectResolver() {
    }

    public static RegistryEntry<StatusEffect> resolve(String name) {
        if (name == null) {
            return null;
        }

        return switch (name.toLowerCase().replace("minecraft:", "").trim()) {
            case "speed" -> StatusEffects.SPEED;
            case "slowness" -> StatusEffects.SLOWNESS;
            case "haste" -> StatusEffects.HASTE;
            case "mining_fatigue" -> StatusEffects.MINING_FATIGUE;
            case "strength" -> StatusEffects.STRENGTH;
            case "jump_boost" -> StatusEffects.JUMP_BOOST;
            case "nausea" -> StatusEffects.NAUSEA;
            case "regeneration" -> StatusEffects.REGENERATION;
            case "resistance" -> StatusEffects.RESISTANCE;
            case "fire_resistance" -> StatusEffects.FIRE_RESISTANCE;
            case "water_breathing" -> StatusEffects.WATER_BREATHING;
            case "invisibility" -> StatusEffects.INVISIBILITY;
            case "blindness" -> StatusEffects.BLINDNESS;
            case "night_vision" -> StatusEffects.NIGHT_VISION;
            case "hunger" -> StatusEffects.HUNGER;
            case "weakness" -> StatusEffects.WEAKNESS;
            case "poison" -> StatusEffects.POISON;
            case "wither" -> StatusEffects.WITHER;
            case "health_boost" -> StatusEffects.HEALTH_BOOST;
            case "absorption" -> StatusEffects.ABSORPTION;
            case "glowing" -> StatusEffects.GLOWING;
            case "levitation" -> StatusEffects.LEVITATION;
            case "luck" -> StatusEffects.LUCK;
            case "slow_falling" -> StatusEffects.SLOW_FALLING;
            default -> null;
        };
    }
}
