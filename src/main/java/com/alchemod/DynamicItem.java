package com.alchemod.creator;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;

public class DynamicItem extends Item {

    private static final int EFFECT_DURATION = 600;

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
        if (meta == null || meta.effects().isEmpty()) {
            return ActionResult.PASS;
        }

        // Check remaining charges from custom NBT data
        NbtComponent nbtComp = stack.get(DataComponentTypes.CUSTOM_DATA);
        int charges = 0;
        if (nbtComp != null) {
            charges = nbtComp.copyNbt().getInt("charges");
        }

        if (charges <= 0) {
            if (!world.isClient) {
                user.sendMessage(Text.literal("§7This item has no charges remaining."), true);
            }
            return ActionResult.FAIL;
        }

        String effect = meta.effects().get(0);
        RegistryEntry<net.minecraft.entity.effect.StatusEffect> statusEffect = resolvePower(effect);
        if (statusEffect == null) {
            return ActionResult.PASS;
        }

        if (!world.isClient) {
            user.addStatusEffect(new StatusEffectInstance(statusEffect, EFFECT_DURATION, 1));
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.4f);

            // Decrement charges
            int newCharges = charges - 1;
            NbtCompound tag = nbtComp != null ? nbtComp.copyNbt() : new NbtCompound();
            tag.putInt("charges", newCharges);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));

            // Notify player of remaining charges
            String chargeText = newCharges == 0
                    ? "§cLast charge used!"
                    : "§7Charges remaining: §f" + newCharges;
            user.sendMessage(Text.literal(chargeText), true);
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Append charge count and effect info to the item tooltip so players
     * know what the item does and how many uses remain.
     */
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
        NbtComponent nbtComp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComp == null) return;
        NbtCompound tag = nbtComp.copyNbt();

        String desc = tag.getString("creator_desc");
        if (!desc.isBlank()) {
            tooltip.add(Text.literal("§7" + desc));
        }

        String rarityLabel = tag.getString("creator_rarity");
        if (!rarityLabel.isBlank() && meta != null) {
            tooltip.add(Text.literal(meta.rarityLabel()));
        }

        String itemTypeLabel = tag.getString("creator_item_type");
        if (!itemTypeLabel.isBlank()) {
            tooltip.add(Text.literal(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel(itemTypeLabel)));
        }

        String effectsCsv = tag.getString("creator_effects");
        if (!effectsCsv.isBlank()) {
            for (String eff : effectsCsv.split(",")) {
                tooltip.add(Text.literal("  " + DynamicItemRegistry.CreatedItemMeta.effectLabel(eff.trim())));
            }
        }

        String special = tag.getString("creator_special");
        if (!special.isBlank()) {
            String specialLabel = DynamicItemRegistry.CreatedItemMeta.staticSpecialLabel(special);
            if (specialLabel != null) tooltip.add(Text.literal("  " + specialLabel));
        }

        int charges = tag.getInt("charges");
        tooltip.add(Text.literal("§8Charges: §f" + charges));
    }

    private static RegistryEntry<net.minecraft.entity.effect.StatusEffect> resolvePower(String effect) {
        return switch (effect.toLowerCase().replace("minecraft:", "").trim()) {
            case "speed"           -> StatusEffects.SPEED;
            case "strength"        -> StatusEffects.STRENGTH;
            case "regeneration"    -> StatusEffects.REGENERATION;
            case "resistance"      -> StatusEffects.RESISTANCE;
            case "fire_resistance" -> StatusEffects.FIRE_RESISTANCE;
            case "night_vision"    -> StatusEffects.NIGHT_VISION;
            case "absorption"      -> StatusEffects.ABSORPTION;
            case "luck"            -> StatusEffects.LUCK;
            case "haste"           -> StatusEffects.HASTE;
            case "jump_boost"      -> StatusEffects.JUMP_BOOST;
            case "slow_falling"    -> StatusEffects.SLOW_FALLING;
            case "water_breathing" -> StatusEffects.WATER_BREATHING;
            default                -> null;
        };
    }
}
