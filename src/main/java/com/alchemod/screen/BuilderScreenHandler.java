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
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class BuilderScreenHandler extends ScreenHandler {

    private final Inventory inv;
    private final PropertyDelegate propertyDelegate;
    private final BlockPos blockPos;

    public BuilderScreenHandler(int syncId, PlayerInventory playerInventory,
            Inventory inv, PropertyDelegate propertyDelegate, BlockPos blockPos) {
        super(AlchemodInit.BUILDER_HANDLER, syncId);
        checkSize(inv, 2);
        this.inv = inv;
        this.propertyDelegate = propertyDelegate;
        this.blockPos = blockPos;
        inv.onOpen(playerInventory.player);

        addSlot(new Slot(inv, BuilderBlockEntity.SLOT_A, 56, 17));
        addSlot(new Slot(inv, BuilderBlockEntity.SLOT_B, 56, 53));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        addProperties(propertyDelegate);
    }

    public BuilderScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(3), new ArrayPropertyDelegate(2), null);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack result = ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot.hasStack()) {
            ItemStack original = slot.getStack();
            result = original.copy();
            if (index < inv.size()) {
                if (!insertItem(original, inv.size(), slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!insertItem(original, 0, 2, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (original.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return result;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inv.canPlayerUse(player);
    }

    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
    }

    public int getState() {
        return propertyDelegate.get(0);
    }

    public int getProgress() {
        return propertyDelegate.get(1);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public BlockPos getPos() {
        return blockPos;
    }
}
