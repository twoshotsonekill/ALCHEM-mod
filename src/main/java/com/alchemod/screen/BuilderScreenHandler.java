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
        this(syncId, playerInventory, new SimpleInventory(2), new ArrayPropertyDelegate(6), null);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
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

    public int getDiagnosticStatus() {
        return propertyDelegate.size() > 2 ? propertyDelegate.get(2) : 0;
    }

    public boolean wasRepairAttempted() {
        return propertyDelegate.size() > 3 && propertyDelegate.get(3) != 0;
    }

    public int getFallbackReasonCode() {
        return propertyDelegate.size() > 4 ? propertyDelegate.get(4) : 0;
    }

    public int getPlacementCount() {
        return propertyDelegate.size() > 5 ? propertyDelegate.get(5) : 0;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }
}
