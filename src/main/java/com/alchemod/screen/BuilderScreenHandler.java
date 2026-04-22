package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import com.alchemod.block.BuilderBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class BuilderScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    private final PropertyDelegate propertyDelegate;
    private final BlockPos blockPos;

    public BuilderScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate propertyDelegate, BlockPos blockPos) {
        super(AlchemodInit.BUILDER_HANDLER, syncId);
        checkSize(inventory, 2);
        this.inventory = inventory;
        this.propertyDelegate = propertyDelegate;
        this.blockPos = blockPos;
        inventory.onOpen(playerInventory.player);

        addSlot(new Slot(inventory, BuilderBlockEntity.SLOT_A, 30, 53));
        addSlot(new Slot(inventory, BuilderBlockEntity.SLOT_B, 48, 53));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }

        addProperties(propertyDelegate);
    }

    public BuilderScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(2), new ArrayPropertyDelegate(3), null);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack()) {
            return result;
        }

        ItemStack original = slot.getStack();
        result = original.copy();
        if (index < inventory.size()) {
            if (!insertItem(original, inventory.size(), slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!insertItem(original, 0, inventory.size(), false)) {
            return ItemStack.EMPTY;
        }

        if (original.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        return result;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inventory.canPlayerUse(player);
    }

    public int getState() {
        return propertyDelegate.get(0);
    }

    public int getProgress() {
        return propertyDelegate.get(1);
    }

    public int getMode() {
        return propertyDelegate.get(2);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }
}
