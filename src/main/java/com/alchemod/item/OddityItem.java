package com.alchemod.item;

import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.script.EffectResolver;
import com.alchemod.script.ItemScriptEngine;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
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
 * A single registered item whose identity is entirely determined by its NBT
 * custom data.  Replaces the 64-slot pool model in which metadata was stored
 * in an in-memory map that reset on server restart.
 *
 * <p>All fields that define what the item does — name, description, rarity,
 * effects, special ability, behavior script, sprite data — are read directly
 * from the {@code CUSTOM_DATA} component on the {@link ItemStack}.  Nothing
 * is looked up from the runtime registry, so items survive world reloads and
 * the pool can never be exhausted.
 */
public class OddityItem extends Item {

    private static final int EFFECT_DURATION = 600;

    public OddityItem(Settings settings) {
        super(settings);
    }

    // ── Display name ──────────────────────────────────────────────────────────

    @Override
    public Text getName(ItemStack stack) {
        NbtCompound tag = getCustomData(stack);
        if (tag != null) {
            String name = tag.getString("creator_name");
            if (!name.isBlank()) return Text.literal(name);
        }
        return super.getName(stack);
    }

    // ── Right-click use ───────────────────────────────────────────────────────

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return ActionResult.SUCCESS;

        NbtCompound tag = getCustomData(stack);
        if (tag == null) return ActionResult.PASS;

        int charges = tag.getInt("charges");
        if (charges <= 0) {
            user.sendMessage(Text.literal("This item has no charges remaining."), true);
            return ActionResult.FAIL;
        }

        boolean didUse = false;

        // Script takes priority over effect list
        String script = tag.getString("creator_script");
        if (!script.isBlank()) {
            didUse = ItemScriptEngine.execute(script, user, (ServerWorld) world, stack);
        }

        // Fall back to listed effects
        if (!didUse) {
            String effectsCsv = tag.getString("creator_effects");
            if (!effectsCsv.isBlank()) {
                for (String effectName : effectsCsv.split(",")) {
                    RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect =
                            EffectResolver.resolve(effectName.trim());
                    if (effect != null) {
                        user.addStatusEffect(new StatusEffectInstance(effect, EFFECT_DURATION, 1));
                        didUse = true;
                    }
                }
            }
        }

        if (!didUse) return ActionResult.PASS;

        consumeCharge(stack, charges - 1);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.2f);
        user.sendMessage(Text.literal(
                charges - 1 <= 0 ? "Last charge used." : "Charges remaining: " + (charges - 1)), true);
        return ActionResult.SUCCESS;
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
            List<Text> tooltip, TooltipType type) {
        NbtCompound tag = getCustomData(stack);
        if (tag == null) return;

        String description = tag.getString("creator_desc");
        if (!description.isBlank()) tooltip.add(Text.literal(description));

        String rarity = tag.getString("creator_rarity");
        if (!rarity.isBlank())
            tooltip.add(Text.literal(DynamicItemRegistry.CreatedItemMeta.staticRarityLabel(rarity)));

        String itemType = tag.getString("creator_item_type");
        if (!itemType.isBlank())
            tooltip.add(Text.literal(DynamicItemRegistry.CreatedItemMeta.staticItemTypeLabel(itemType)));

        String effectsCsv = tag.getString("creator_effects");
        if (!effectsCsv.isBlank()) {
            for (String effect : effectsCsv.split(",")) {
                if (!effect.isBlank())
                    tooltip.add(Text.literal(
                            "  " + DynamicItemRegistry.CreatedItemMeta.effectLabel(effect.trim())));
            }
        }

        String special = tag.getString("creator_special");
        if (!special.isBlank()) {
            String label = DynamicItemRegistry.CreatedItemMeta.staticSpecialLabel(special);
            if (label != null) tooltip.add(Text.literal("  " + label));
        }

        String script = tag.getString("creator_script");
        if (!script.isBlank()) tooltip.add(Text.literal("§8Sandboxed AI behavior enabled"));

        tooltip.add(Text.literal("Charges: " + tag.getInt("charges")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void consumeCharge(ItemStack stack, int newCharges) {
        NbtCompound tag = getCustomData(stack);
        if (tag == null) tag = new NbtCompound();
        tag.putInt("charges", Math.max(newCharges, 0));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
    }

    public static NbtCompound getCustomData(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null ? customData.copyNbt() : null;
    }
}
