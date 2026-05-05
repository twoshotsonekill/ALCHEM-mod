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
 * Alchemical Charm - wearable items that provide passive effects.
 * 8 types: Speed, Strength, Health, Luck, Flight, Invisibility, Regeneration, Protection.
 */
public class CharmItem extends Item {

    private final String charmType;
    private final int maxCharges;

    public CharmItem(String charmType, Settings settings) {
        super(settings);
        this.charmType = charmType;
        this.maxCharges = getDefaultCharges(charmType);
    }

    @Override
    public Text getName(ItemStack stack) {
        String name = getCharmName(stack);
        if (!name.isBlank()) {
            return Text.literal(getRarityPrefix(stack) + name);
        }
        return Text.literal(getRarityPrefix(stack) + "Alchemical Charm of " + charmType);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7§oPassive charm effect"));
        String desc = getCharmDesc(stack);
        if (!desc.isBlank()) {
            tooltip.add(Text.literal("§7" + desc));
        }
        int charges = getCharmCharges(stack);
        tooltip.add(Text.literal("§8Charges: " + charges + "/" + maxCharges));

        if (isActive(stack)) {
            tooltip.add(Text.literal("§a§oActive"));
        } else {
            tooltip.add(Text.literal("§c§oInactive"));
        }
    }

    @Override
    public net.minecraft.util.ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return net.minecraft.util.ActionResult.SUCCESS;

        if (!isActive(stack)) {
            int charges = getCharmCharges(stack);
            if (charges <= 0) {
                user.sendMessage(Text.literal("§cNo charges remaining!"), true);
                return net.minecraft.util.ActionResult.FAIL;
            }
            setActive(stack, true);
            user.sendMessage(Text.literal("§aActivated charm: " + charmType), true);
        } else {
            setActive(stack, false);
            user.sendMessage(Text.literal("§cDeactivated charm: " + charmType), true);
        }
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    private static String getCharmName(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return nbt == null ? "" : nbt.copyNbt().getString("charm_name");
    }

    private static String getCharmDesc(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return nbt == null ? "" : nbt.copyNbt().getString("charm_desc");
    }

    private static int getCharmCharges(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return nbt == null ? 0 : nbt.copyNbt().getInt("charm_charges");
    }

    private static boolean isActive(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return nbt != null && nbt.copyNbt().getBoolean("charm_active");
    }

    private static void setActive(ItemStack stack, boolean active) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound tag = nbt != null ? nbt.copyNbt() : new net.minecraft.nbt.NbtCompound();
        tag.putBoolean("charm_active", active);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }

    private static String getRarityPrefix(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        String rarity = nbt == null ? "common" : nbt.copyNbt().getString("charm_rarity");
        return switch (rarity) {
            case "uncommon" -> "§a";
            case "rare"     -> "§b";
            case "epic"     -> "§d";
            case "legendary" -> "§6§l";
            default        -> "§f";
        };
    }

    private static int getDefaultCharges(String charmType) {
        return switch (charmType.toLowerCase()) {
            case "speed", "luck", "regeneration" -> 20;
            case "strength", "protection" -> 15;
            case "flight", "invisibility" -> 10;
            default -> 10;
        };
    }

    public static void setCharmData(ItemStack stack, String name, String desc,
                                      String rarity, int charges, boolean active) {
        net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
        tag.putString("charm_name", name != null ? name : "");
        tag.putString("charm_desc", desc != null ? desc : "");
        tag.putString("charm_rarity", rarity != null ? rarity : "common");
        tag.putInt("charm_charges", charges);
        tag.putBoolean("charm_active", active);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }
}
