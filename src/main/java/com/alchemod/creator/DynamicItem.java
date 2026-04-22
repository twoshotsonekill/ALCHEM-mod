package com.alchemod.creator;

import com.alchemod.script.EffectResolver;
import com.alchemod.script.ItemScriptEngine;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
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

    public int getSlotIndex() {
        return slotIndex;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        DynamicItemRegistry.CreatedItemMeta runtimeMeta = resolveMeta(stack);
        int charges = getCharges(stack);
        if (charges <= 0) {
            user.sendMessage(Text.literal("This item has no charges remaining."), true);
            return ActionResult.FAIL;
        }

        boolean didUse = false;
        String script = readTag(stack, "creator_script");
        if ((script == null || script.isBlank()) && runtimeMeta != null) {
            script = runtimeMeta.script();
        }

        if (script != null && !script.isBlank()) {
            didUse = ItemScriptEngine.execute(script, user, (ServerWorld) world, stack);
        }

        if (!didUse && runtimeMeta != null && runtimeMeta.effects() != null && !runtimeMeta.effects().isEmpty()) {
            String effectName = runtimeMeta.effects().get(0);
            RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect = EffectResolver.resolve(effectName);
            if (effect != null) {
                user.addStatusEffect(new StatusEffectInstance(effect, EFFECT_DURATION, 1));
                didUse = true;
            }
        }

        if (!didUse) {
            return ActionResult.PASS;
        }

        consumeCharge(stack, charges - 1);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.2f);
        user.sendMessage(Text.literal(charges - 1 <= 0 ? "Last charge used." : "Charges remaining: " + (charges - 1)), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        String description = readTag(stack, "creator_desc");
        if (description != null && !description.isBlank()) {
            tooltip.add(Text.literal(description));
        }

        String rarity = readTag(stack, "creator_rarity");
        if (rarity != null && !rarity.isBlank()) {
            tooltip.add(Text.literal(resolveMeta(stack) != null ? resolveMeta(stack).rarityLabel() : rarity));
        }

        String itemType = readTag(stack, "creator_item_type");
        if (itemType != null && !itemType.isBlank()) {
            tooltip.add(Text.literal(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel(itemType)));
        }

        String effectsCsv = readTag(stack, "creator_effects");
        if (effectsCsv != null && !effectsCsv.isBlank()) {
            for (String effect : effectsCsv.split(",")) {
                if (!effect.isBlank()) {
                    tooltip.add(Text.literal("  " + DynamicItemRegistry.CreatedItemMeta.effectLabel(effect.trim())));
                }
            }
        }

        String special = readTag(stack, "creator_special");
        if (special != null && !special.isBlank()) {
            String label = DynamicItemRegistry.CreatedItemMeta.staticSpecialLabel(special);
            if (label != null) {
                tooltip.add(Text.literal("  " + label));
            }
        }

        String script = readTag(stack, "creator_script");
        if (script != null && !script.isBlank()) {
            tooltip.add(Text.literal("§8Sandboxed AI behavior enabled"));
        }

        tooltip.add(Text.literal("Charges: " + getCharges(stack)));
    }

    private DynamicItemRegistry.CreatedItemMeta resolveMeta(ItemStack stack) {
        if (meta != null) {
            return meta;
        }

        NbtCompound tag = getCustomData(stack);
        if (tag != null) {
            int storedSlot = tag.getInt("creator_slot");
            if (storedSlot >= 0) {
                DynamicItemRegistry.CreatedItemMeta storedMeta = DynamicItemRegistry.getMeta(storedSlot);
                if (storedMeta != null) {
                    return storedMeta;
                }
            }
        }

        return DynamicItemRegistry.getMeta(slotIndex);
    }

    private static int getCharges(ItemStack stack) {
        NbtCompound tag = getCustomData(stack);
        return tag != null ? tag.getInt("charges") : 0;
    }

    private static void consumeCharge(ItemStack stack, int charges) {
        NbtCompound tag = getCustomData(stack);
        if (tag == null) {
            tag = new NbtCompound();
        }
        tag.putInt("charges", Math.max(charges, 0));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
    }

    private static String readTag(ItemStack stack, String key) {
        NbtCompound tag = getCustomData(stack);
        return tag != null ? tag.getString(key) : null;
    }

    private static NbtCompound getCustomData(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null ? customData.copyNbt() : null;
    }
}
