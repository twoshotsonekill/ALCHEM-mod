package com.alchemod.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.sound.SoundEvents;

import java.util.List;

/**
 * Alchemical Wand - casts AI-generated spells.
 * Spells defined by prompt: "fireball", "heal", "teleport"
 * Uses "Alchemical Essence" as mana.
 * Cooldown based on spell complexity.
 */
public class AlchemicalWandItem extends Item {

    private static final int MAX_MANA = 200;
    private static final int MANA_PER_ESSENCE = 4; // 1 essence = 4 mana

    public AlchemicalWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        int mana = getMana(stack);
        String prefix = mana > 150 ? "§6§l" : mana > 80 ? "§b§l" : mana > 30 ? "§a§l" : "§7§l";
        return Text.literal(prefix + "Alchemical Wand §8[" + mana + "/" + MAX_MANA + "]");
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        int mana = getMana(stack);
        tooltip.add(Text.literal("§7Right-click to cast AI spells"));
        tooltip.add(Text.literal("§8Mana: " + mana + "/" + MAX_MANA));

        String spell = getCurrentSpell(stack);
        if (!spell.isBlank()) {
            tooltip.add(Text.literal("§6Current spell: " + spell));
        }

        tooltip.add(Text.literal("§8Use /alchem wand to bind spells"));
    }

    @Override
    public net.minecraft.util.ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return net.minecraft.util.ActionResult.SUCCESS;

        int mana = getMana(stack);
        String spell = getCurrentSpell(stack);

        if (spell.isBlank()) {
            user.sendMessage(Text.literal("§cNo spell bound! Use /alchem wand"), true);
            return net.minecraft.util.ActionResult.FAIL;
        }

        if (mana < getSpellCost(spell)) {
            user.sendMessage(Text.literal("§cNot enough mana! Need " + getSpellCost(spell)), true);
            return net.minecraft.util.ActionResult.FAIL;
        }

        // Cast spell (simplified)
        user.sendMessage(Text.literal("§6Casting: " + spell), true);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BLOCK_FIRE_EXTINGUISH, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);

        setMana(stack, mana - getSpellCost(spell));
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    private static int getMana(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return nbt == null ? MAX_MANA : nbt.copyNbt().getInt("wand_mana");
    }

    private static void setMana(ItemStack stack, int mana) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound tag = nbt != null ? nbt.copyNbt() : new net.minecraft.nbt.NbtCompound();
        tag.putInt("wand_mana", Math.max(mana, 0));
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }

    private static String getCurrentSpell(ItemStack stack) {
        var nbt = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return nbt == null ? "" : nbt.copyNbt().getString("wand_spell");
    }

    private static int getSpellCost(String spell) {
        return switch (spell.toLowerCase()) {
            case "fireball" -> 20;
            case "heal" -> 15;
            case "teleport" -> 30;
            default -> 10;
        };
    }

    public static void setWandData(ItemStack stack, String spell, int mana) {
        net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
        tag.putString("wand_spell", spell != null ? spell : "");
        tag.putInt("wand_mana", mana > 0 ? mana : MAX_MANA);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));
    }
}
