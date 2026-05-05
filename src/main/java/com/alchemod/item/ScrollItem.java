package com.alchemod.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;

/**
 * Alchemical Scroll - one-time use items with AI-generated spell effects.
 * 5 types: Fire, Ice, Lightning, Healing, Teleportation.
 */
public class ScrollItem extends Item {

    private final String scrollType;

    public ScrollItem(String scrollType, Settings settings) {
        super(settings);
        this.scrollType = scrollType;
    }

    @Override
    public Text getName(ItemStack stack) {
        String name = getScrollData(stack, "scroll_name");
        if (!name.isBlank()) {
            return Text.literal(getRarityPrefix(stack) + name);
        }
        return Text.literal(getRarityPrefix(stack) + "Alchemical Scroll of " + scrollType);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        String desc = getScrollData(stack, "scroll_desc");
        if (!desc.isBlank()) {
            tooltip.add(Text.literal("§7" + desc));
        }
        tooltip.add(Text.literal("§8One-time use"));
        tooltip.add(Text.literal(getRarityLabel(stack)));
    }

    @Override
    public net.minecraft.util.ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return net.minecraft.util.ActionResult.SUCCESS;

        user.sendMessage(Text.literal("§6Used scroll: " + scrollType), true);

        // Apply the scroll effect
        applyScrollEffect(user, scrollType);

        // Consume the scroll
        stack.decrement(1);
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    private void applyScrollEffect(PlayerEntity user, String type) {
        switch (type.toLowerCase()) {
            case "fire" -> {
                user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(),
                        net.minecraft.sound.SoundEvents.BLOCK_FIRE_EXTINGUISH, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
                user.sendMessage(Text.literal("§cFire scroll activated!"), true);
            }
            case "ice" -> {
                user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(),
                        net.minecraft.sound.SoundEvents.BLOCK_GLASS_BREAK, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.5f);
                user.sendMessage(Text.literal("§bIce scroll activated!"), true);
            }
            case "lightning" -> {
                user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(),
                        net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, net.minecraft.sound.SoundCategory.WEATHER, 1.0f, 1.0f);
                user.sendMessage(Text.literal("§eLightning scroll activated!"), true);
            }
            case "healing" -> {
                user.heal(10.0f);
                user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(),
                        net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
                user.sendMessage(Text.literal("§aHealing scroll activated!"), true);
            }
            case "teleportation" -> {
                user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(),
                        net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
                user.sendMessage(Text.literal("§5Teleportation scroll activated!"), true);
            }
        }
    }

    private static String getScrollData(ItemStack stack, String key) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return nbt == null ? "" : nbt.copyNbt().getString(key);
    }

    private static String getRarityPrefix(ItemStack stack) {
        String rarity = getScrollData(stack, "scroll_rarity");
        return switch (rarity) {
            case "uncommon" -> "§a";
            case "rare"     -> "§b";
            case "epic"     -> "§d";
            case "legendary" -> "§6§l";
            default        -> "§f";
        };
    }

    private static String getRarityLabel(ItemStack stack) {
        String rarity = getScrollData(stack, "scroll_rarity");
        return switch (rarity) {
            case "uncommon" -> "§a[Uncommon]";
            case "rare"     -> "§b[Rare]";
            case "epic"     -> "§d[Epic]";
            case "legendary" -> "§6§l[Legendary]";
            default        -> "§f[Common]";
        };
    }

    public static void setScrollData(ItemStack stack, String name, String desc,
                                      String type, String rarity) {
        net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
        tag.putString("scroll_name", name != null ? name : "");
        tag.putString("scroll_desc", desc != null ? desc : "");
        tag.putString("scroll_type", type != null ? type : "fire");
        tag.putString("scroll_rarity", rarity != null ? rarity : "common");
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }
}
