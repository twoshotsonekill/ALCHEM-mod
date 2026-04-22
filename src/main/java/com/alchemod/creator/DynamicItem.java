package com.alchemod.creator;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.List;
import java.util.Random;

public class DynamicItem extends Item {

    private static final int EFFECT_DURATION = 600;
    private static final Random RAND = new Random();

    private final int slotIndex;
    private DynamicItemRegistry.CreatedItemMeta meta;

    public static final String[][] FORM_BEHAVIORS = {
            {"amulet", "auto-equip to necklace slot", "§d✦"},
            {"dagger", "+2 damage on hit", "§c⚔"},
            {"crown", "passive aura nearby", "§e👑"},
            {"orb", "orbit around player", "§b🔮"},
            {"staff", "extended reach", "§a⚐"},
            {"tome", "open on right-click", "§9📖"},
            {"shield", "blocking bonus", "§8🛡"}
    };

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

        if (meta == null) {
            return ActionResult.PASS;
        }

        String form = meta != null ? meta.itemType().toLowerCase() : "";

        return switch (form) {
            case "tome" -> openTome(world, user, stack);
            case "orb" -> activateOrb(world, user, stack);
            case "amulet" -> activateAmulet(world, user, stack);
            default -> useDefault(world, user, hand, stack);
        };
    }

    private ActionResult openTome(World world, PlayerEntity user, ItemStack stack) {
        if (!world.isClient) {
            user.sendMessage(Text.literal("You read the ancient tome..."), true);
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
        return ActionResult.SUCCESS;
    }

    private ActionResult activateOrb(World world, PlayerEntity user, ItemStack stack) {
        if (!world.isClient && meta != null && !meta.effects().isEmpty()) {
            String effect = meta.effects().get(0);
            RegistryEntry<net.minecraft.entity.effect.StatusEffect> statusEffect = resolvePower(effect);
            if (statusEffect != null) {
                user.addStatusEffect(new StatusEffectInstance(statusEffect, EFFECT_DURATION, 1));
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.4f);
                user.sendMessage(Text.literal("Orb activated!"), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    private ActionResult activateAmulet(World world, PlayerEntity user, ItemStack stack) {
        if (!world.isClient && meta != null && !meta.effects().isEmpty()) {
            String effect = meta.effects().get(0);
            RegistryEntry<net.minecraft.entity.effect.StatusEffect> statusEffect = resolvePower(effect);
            if (statusEffect != null) {
                user.addStatusEffect(new StatusEffectInstance(statusEffect, EFFECT_DURATION * 2, 1));
                world.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
                user.sendMessage(Text.literal("Amulet aura activated!"), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    private ActionResult useDefault(World world, PlayerEntity user, Hand hand, ItemStack stack) {
        NbtComponent nbtComp = stack.get(DataComponentTypes.CUSTOM_DATA);
        int charges = 0;
        if (nbtComp != null) {
            charges = nbtComp.copyNbt().getInt("charges");
        }

        if (charges <= 0) {
            if (!world.isClient) {
                user.sendMessage(Text.literal("This item has no charges remaining."), true);
            }
            return ActionResult.FAIL;
        }

        String effect = meta != null && !meta.effects().isEmpty() ? meta.effects().get(0) : null;
        if (effect == null) {
            return ActionResult.PASS;
        }

        RegistryEntry<net.minecraft.entity.effect.StatusEffect> statusEffect = resolvePower(effect);
        if (statusEffect == null) {
            return ActionResult.PASS;
        }

        if (!world.isClient) {
            user.addStatusEffect(new StatusEffectInstance(statusEffect, EFFECT_DURATION, 1));
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.4f);

            int newCharges = charges - 1;
            NbtCompound tag = nbtComp != null ? nbtComp.copyNbt() : new NbtCompound();
            tag.putInt("charges", newCharges);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));

            String chargeText = newCharges == 0
                    ? "Last charge used!"
                    : "Charges remaining: " + newCharges;
            user.sendMessage(Text.literal(chargeText), true);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
        NbtComponent nbtComp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComp == null) return;
        NbtCompound tag = nbtComp.copyNbt();

        String desc = tag.getString("creator_desc");
        if (!desc.isBlank()) {
            tooltip.add(Text.literal(desc));
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
        tooltip.add(Text.literal("Charges: " + charges));
    }

    private static RegistryEntry<net.minecraft.entity.effect.StatusEffect> resolvePower(String effect) {
        return switch (effect.toLowerCase().replace("minecraft:", "").trim()) {
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