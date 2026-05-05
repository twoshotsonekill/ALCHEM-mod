package com.alchemod.item;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.List;

/**
 * Alchemical Armor Set - armor with AI-generated special abilities.
 * Set bonus when all 4 pieces worn.
 */
public class AlchemicalArmorItem extends Item {

    private final EquipmentSlot slot;

    public AlchemicalArmorItem(EquipmentSlot slot, Settings settings) {
        super(settings);
        this.slot = slot;
    }

    @Override
    public Text getName(ItemStack stack) {
        String name = getArmorData(stack, "armor_name");
        if (!name.isBlank()) {
            return Text.literal(getRarityPrefix(stack) + name);
        }
        return super.getName(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        String desc = getArmorData(stack, "armor_desc");
        if (!desc.isBlank()) {
            tooltip.add(Text.literal("§7" + desc));
        }

        String ability = getArmorData(stack, "armor_ability");
        if (!ability.isBlank()) {
            tooltip.add(Text.literal("§6Ability: " + ability));
        }

        int charges = getArmorCharges(stack);
        tooltip.add(Text.literal("§8Charges: " + charges));

        if (isFullSet(stack)) {
            tooltip.add(Text.literal("§6§oFull set bonus active!"));
        }

        tooltip.add(Text.literal(getRarityLabel(stack)));
    }

    @Override
    public net.minecraft.util.ActionResult use(World world, PlayerEntity user, net.minecraft.util.Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return net.minecraft.util.ActionResult.SUCCESS;

        int charges = getArmorCharges(stack);
        if (charges <= 0) {
            user.sendMessage(Text.literal("§cNo charges remaining!"), true);
            return net.minecraft.util.ActionResult.FAIL;
        }

        String ability = getArmorData(stack, "armor_ability");
        if (!ability.isBlank()) {
            user.sendMessage(Text.literal("§6Activated: " + ability), true);
            setArmorCharges(stack, charges - 1);
            return net.minecraft.util.ActionResult.SUCCESS;
        }

        return net.minecraft.util.ActionResult.PASS;
    }

    private boolean isFullSet(ItemStack stack) {
        return false; // Placeholder - would check player equipment slots
    }

    private static String getArmorData(ItemStack stack, String key) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (nbt == null) return "";
        return nbt.copyNbt().getString(key);
    }

    private static int getArmorCharges(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (nbt == null) return 0;
        return nbt.copyNbt().getInt("armor_charges");
    }

    private static void setArmorCharges(ItemStack stack, int charges) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound tag = nbt != null ? nbt.copyNbt() : new net.minecraft.nbt.NbtCompound();
        tag.putInt("armor_charges", Math.max(charges, 0));
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }

    private static String getRarityLabel(ItemStack stack) {
        String rarity = getArmorData(stack, "armor_rarity");
        return switch (rarity) {
            case "uncommon" -> "§a[Uncommon]";
            case "rare"     -> "§b[Rare]";
            case "epic"     -> "§d[Epic]";
            case "legendary" -> "§6§l[Legendary]";
            default        -> "§f[Common]";
        };
    }

    private static String getRarityPrefix(ItemStack stack) {
        String rarity = getArmorData(stack, "armor_rarity");
        return switch (rarity) {
            case "uncommon" -> "§a";
            case "rare"     -> "§b";
            case "epic"     -> "§d";
            case "legendary" -> "§6§l";
            default        -> "§f";
        };
    }

    public static void setArmorData(ItemStack stack, String name, String desc,
                                      String ability, String rarity, int charges) {
        net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
        tag.putString("armor_name", name != null ? name : "");
        tag.putString("armor_desc", desc != null ? desc : "");
        tag.putString("armor_ability", ability != null ? ability : "");
        tag.putString("armor_rarity", rarity != null ? rarity : "common");
        tag.putInt("armor_charges", charges);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }
}
