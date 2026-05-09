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

/**
 * A dynamic item whose identity — name, effects, script, rarity, etc. — is stored
 * entirely in NBT components on the {@link ItemStack}.  No in-memory registry lookup
 * is performed for any gameplay behaviour, so items survive server restarts correctly.
 *
 * <p>The {@code slotIndex} field is only used as a texture-cache key in the current
 * session; it carries no semantic identity.
 */
public class DynamicItem extends Item {

    private static final int EFFECT_DURATION = 600;

    private final int slotIndex;

    public DynamicItem(Settings settings, int slotIndex) {
        super(settings);
        this.slotIndex = slotIndex;
    }

    /** Called by {@link DynamicItemRegistry} when item metadata is freshly generated
     *  in the same session.  Kept for backwards-compat; gameplay no longer uses it. */
    public void setMeta(DynamicItemRegistry.CreatedItemMeta meta) {
        // No-op — all behaviour is now read from ItemStack NBT.
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    // ── Display name ──────────────────────────────────────────────────────────

    /**
     * Read the custom name stored in NBT so the item displays correctly in
     * inventory, tooltips, and chat rather than showing a raw translation key.
     */
    @Override
    public Text getName(ItemStack stack) {
        String name = readTag(stack, "creator_name");
        if (name != null && !name.isBlank()) {
            return Text.literal(name);
        }
        return super.getName(stack);
    }

    // ── Right-click use ───────────────────────────────────────────────────────

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        int charges = getCharges(stack);
        if (charges <= 0) {
            user.sendMessage(Text.literal("This item has no charges remaining."), true);
            return ActionResult.FAIL;
        }

        boolean didUse = false;

        // Priority 1: run embedded behavior script.
        String script = readTag(stack, "creator_script");
        if (script != null && !script.isBlank()) {
            didUse = ItemScriptEngine.execute(script, user, (ServerWorld) world, stack);
        }

        // Priority 2: fall back to first listed effect.
        if (!didUse) {
            String effectsCsv = readTag(stack, "creator_effects");
            if (effectsCsv != null && !effectsCsv.isBlank()) {
                String firstEffect = effectsCsv.split(",")[0].trim();
                RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect =
                        EffectResolver.resolve(firstEffect);
                if (effect != null) {
                    user.addStatusEffect(new StatusEffectInstance(effect, EFFECT_DURATION, 1));
                    didUse = true;
                }
            }
        }

        if (!didUse) {
            return ActionResult.PASS;
        }

        int remaining = charges - 1;
        consumeCharge(stack, remaining);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.2f);
        user.sendMessage(Text.literal(remaining <= 0
                ? "Last charge used."
                : "Charges remaining: " + remaining), true);
        return ActionResult.SUCCESS;
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
            List<Text> tooltip, TooltipType type) {

        String description = readTag(stack, "creator_desc");
        if (description != null && !description.isBlank()) {
            tooltip.add(Text.literal(description));
        }

        String rarity = readTag(stack, "creator_rarity");
        if (rarity != null && !rarity.isBlank()) {
            tooltip.add(Text.literal(DynamicItemRegistry.CreatedItemMeta.staticRarityLabel(rarity)));
        }

        String itemType = readTag(stack, "creator_item_type");
        if (itemType != null && !itemType.isBlank()) {
            tooltip.add(Text.literal(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel(itemType)));
        }

        String effectsCsv = readTag(stack, "creator_effects");
        if (effectsCsv != null && !effectsCsv.isBlank()) {
            for (String effect : effectsCsv.split(",")) {
                if (!effect.isBlank()) {
                    tooltip.add(Text.literal(
                            "  " + DynamicItemRegistry.CreatedItemMeta.effectLabel(effect.trim())));
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

    // ── NBT helpers ───────────────────────────────────────────────────────────

    public static int getCharges(ItemStack stack) {
        NbtCompound tag = getCustomData(stack);
        return tag != null ? tag.getInt("charges") : 0;
    }

    public static void consumeCharge(ItemStack stack, int remaining) {
        NbtCompound tag = getCustomData(stack);
        if (tag == null) tag = new NbtCompound();
        tag.putInt("charges", Math.max(remaining, 0));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
    }

    public static String readTag(ItemStack stack, String key) {
        NbtCompound tag = getCustomData(stack);
        return tag != null ? tag.getString(key) : null;
    }

    public static NbtCompound getCustomData(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null ? customData.copyNbt() : null;
    }
}
