package com.alchemod.creator;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;

public class DynamicItem extends Item {

    private static final int EFFECT_DURATION = 600;
    private static final int USE_COOLDOWN = 200;

    private final int slotIndex;
    private DynamicItemRegistry.CreatedItemMeta meta;

    public DynamicItem(Settings settings, int slotIndex) {
        super(settings);
        this.slotIndex = slotIndex;
    }

    public void setMeta(DynamicItemRegistry.CreatedItemMeta meta) {
        this.meta = meta;
    }

    public int getSlotIndex() { return slotIndex; }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (meta == null || meta.power().isBlank()) {
            return ActionResult.PASS;
        }
        RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect = resolvePower(meta.power());
        if (effect == null) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            user.addStatusEffect(new StatusEffectInstance(effect, EFFECT_DURATION, 1));
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.4f);
        }
        return ActionResult.SUCCESS;
    }

    private static RegistryEntry<net.minecraft.entity.effect.StatusEffect> resolvePower(String power) {
        return switch (power.toLowerCase().replace("minecraft:", "").trim()) {
            case "speed" -> StatusEffects.SPEED;
            case "strength" -> StatusEffects.STRENGTH;
            case "regeneration" -> StatusEffects.REGENERATION;
            case "resistance" -> StatusEffects.RESISTANCE;
            case "fire_resistance" -> StatusEffects.FIRE_RESISTANCE;
            case "night_vision" -> StatusEffects.NIGHT_VISION;
            case "absorption" -> StatusEffects.ABSORPTION;
            case "luck" -> StatusEffects.LUCK;
            case "haste" -> StatusEffects.HASTE;
            case "jump_boost" -> StatusEffects.JUMP_BOOST;
            case "slow_falling" -> StatusEffects.SLOW_FALLING;
            case "water_breathing" -> StatusEffects.WATER_BREATHING;
            default -> null;
        };
    }
}