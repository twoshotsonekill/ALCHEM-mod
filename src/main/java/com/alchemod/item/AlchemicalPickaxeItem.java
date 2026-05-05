package com.alchemod.item;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Alchemical Pickaxe - enhanced mining with AI-selected special modes.
 * Special mode: "Vein Miner" - mines entire ore vein.
 */
public class AlchemicalPickaxeItem extends Item {

    private static final int MAX_CHARGES = 100;
    private static final int VEIN_MINER_COST = 10;

    public AlchemicalPickaxeItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        String name = getToolData(stack, "tool_name");
        if (!name.isBlank()) {
            return Text.literal(getRarityPrefix(stack) + name);
        }
        return super.getName(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        String desc = getToolData(stack, "tool_desc");
        if (!desc.isBlank()) {
            tooltip.add(Text.literal("§7" + desc));
        }

        String mode = getSpecialMode(stack);
        if (!mode.isBlank()) {
            tooltip.add(Text.literal("§6Mode: " + mode));
        }

        int charges = getToolCharges(stack);
        tooltip.add(Text.literal("§8Charges: " + charges + "/" + MAX_CHARGES));

        tooltip.add(Text.literal(getRarityLabel(stack)));
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return ActionResult.SUCCESS;

        // Toggle special mode
        String currentMode = getSpecialMode(stack);
        String newMode = currentMode.isBlank() ? "vein_miner" : "";
        setSpecialMode(stack, newMode);

        if (newMode.isBlank()) {
            user.sendMessage(Text.literal("§cSpecial mode deactivated"), true);
        } else {
            user.sendMessage(Text.literal("§6Special mode: " + newMode), true);
        }

        return ActionResult.SUCCESS;
    }

    private static String getToolData(ItemStack stack, String key) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return nbt == null ? "" : nbt.copyNbt().getString(key);
    }

    private static int getToolCharges(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return nbt == null ? MAX_CHARGES : nbt.copyNbt().getInt("tool_charges");
    }

    private static void setToolCharges(ItemStack stack, int charges) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound tag = nbt != null ? nbt.copyNbt() : new net.minecraft.nbt.NbtCompound();
        tag.putInt("tool_charges", Math.max(charges, 0));
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }

    private static String getSpecialMode(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return nbt == null ? "" : nbt.copyNbt().getString("special_mode");
    }

    private static void setSpecialMode(ItemStack stack, String mode) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound tag = nbt != null ? nbt.copyNbt() : new net.minecraft.nbt.NbtCompound();
        tag.putString("special_mode", mode != null ? mode : "");
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }

    private static String getRarityPrefix(ItemStack stack) {
        String rarity = getToolData(stack, "tool_rarity");
        return switch (rarity) {
            case "uncommon" -> "§a";
            case "rare"     -> "§b";
            case "epic"     -> "§d";
            case "legendary" -> "§6§l";
            default        -> "§f";
        };
    }

    private static String getRarityLabel(ItemStack stack) {
        String rarity = getToolData(stack, "tool_rarity");
        return switch (rarity) {
            case "uncommon" -> "§a[Uncommon]";
            case "rare"     -> "§b[Rare]";
            case "epic"     -> "§d[Epic]";
            case "legendary" -> "§6§l[Legendary]";
            default        -> "§f[Common]";
        };
    }

    public static void setToolData(ItemStack stack, String name, String desc,
                                     String rarity, String mode, int charges) {
        net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
        tag.putString("tool_name", name != null ? name : "");
        tag.putString("tool_desc", desc != null ? desc : "");
        tag.putString("tool_rarity", rarity != null ? rarity : "common");
        tag.putString("special_mode", mode != null ? mode : "");
        tag.putInt("tool_charges", charges > 0 ? charges : MAX_CHARGES);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }
}
