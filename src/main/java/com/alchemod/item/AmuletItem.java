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
 * Alchemical Amulet - provides passive effects when worn in curio/accessory slot.
 * Different amulets have different effects based on AI generation.
 */
public class AmuletItem extends Item {

    private static final List<String> VALID_EFFECTS = List.of(
            "speed", "haste", "strength", "jump_boost", "regeneration",
            "resistance", "fire_resistance", "water_breathing", "invisibility",
            "health_boost", "absorption", "luck"
    );

    public AmuletItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        String name = getAmuletData(stack, "amulet_name");
        if (!name.isBlank()) {
            return Text.literal(getRarityPrefix(stack) + name);
        }
        return super.getName(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        String desc = getAmuletData(stack, "amulet_desc");
        if (!desc.isBlank()) {
            tooltip.add(Text.literal("§7" + desc));
        }

        String effect = getAmuletData(stack, "amulet_effect");
        if (!effect.isBlank()) {
            tooltip.add(Text.literal("§6Effect: " + effect));
        }

        int charges = getAmuletCharges(stack);
        tooltip.add(Text.literal("§8Charges: " + charges));

        String rarity = getAmuletData(stack, "amulet_rarity");
        if (!rarity.isBlank()) {
            tooltip.add(Text.literal(getRarityLabel(rarity)));
        }
    }

    @Override
    public net.minecraft.util.ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return net.minecraft.util.ActionResult.SUCCESS;

        int charges = getAmuletCharges(stack);
        if (charges <= 0) {
            user.sendMessage(Text.literal("§cNo charges remaining!"), true);
            return net.minecraft.util.ActionResult.FAIL;
        }

        String effectName = getAmuletData(stack, "amulet_effect");
        if (!effectName.isBlank() && VALID_EFFECTS.contains(effectName.toLowerCase())) {
            // Apply the effect (simplified - would use EffectResolver)
            user.sendMessage(Text.literal("§6Activated amulet effect: " + effectName), true);
            setAmuletCharges(stack, charges - 1);
            return net.minecraft.util.ActionResult.SUCCESS;
        }

        return net.minecraft.util.ActionResult.PASS;
    }

    private static String getAmuletData(ItemStack stack, String key) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (nbt == null) return "";
        return nbt.copyNbt().getString(key);
    }

    private static int getAmuletCharges(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (nbt == null) return 0;
        return nbt.copyNbt().getInt("amulet_charges");
    }

    private static void setAmuletCharges(ItemStack stack, int charges) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound tag = nbt != null ? nbt.copyNbt() : new net.minecraft.nbt.NbtCompound();
        tag.putInt("amulet_charges", Math.max(charges, 0));
        if (!tag.getString("amulet_name").isBlank()) {
            tag.putString("amulet_name", tag.getString("amulet_name"));
            tag.putString("amulet_desc", tag.getString("amulet_desc"));
            tag.putString("amulet_effect", tag.getString("amulet_effect"));
            tag.putString("amulet_rarity", tag.getString("amulet_rarity"));
        }
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }

    private static String getRarityPrefix(ItemStack stack) {
        String rarity = getAmuletData(stack, "amulet_rarity");
        return switch (rarity) {
            case "uncommon" -> "§a";
            case "rare"     -> "§b";
            case "epic"     -> "§d";
            case "legendary" -> "§6§l";
            default        -> "§f";
        };
    }

    private static String getRarityLabel(String rarity) {
        return switch (rarity) {
            case "uncommon" -> "§a[Uncommon]";
            case "rare"     -> "§b[Rare]";
            case "epic"     -> "§d[Epic]";
            case "legendary" -> "§6§l[Legendary]";
            default        -> "§f[Common]";
        };
    }

    public static void setAmuletData(ItemStack stack, String name, String desc,
                                      String effect, String rarity, int charges) {
        net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
        tag.putString("amulet_name", name != null ? name : "");
        tag.putString("amulet_desc", desc != null ? desc : "");
        tag.putString("amulet_effect", effect != null ? effect : "");
        tag.putString("amulet_rarity", rarity != null ? rarity : "common");
        tag.putInt("amulet_charges", charges);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }
}
