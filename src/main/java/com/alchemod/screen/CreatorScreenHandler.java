package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import com.alchemod.block.CreatorBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class CreatorScreenHandler extends ScreenHandler {

    private final Inventory inv;
    private final PropertyDelegate delegate;

    public CreatorScreenHandler(int syncId, PlayerInventory playerInv,
            Inventory inv, PropertyDelegate delegate) {
        super(AlchemodInit.CREATOR_HANDLER, syncId);
        checkSize(inv, 3);
        this.inv      = inv;
        this.delegate = delegate;
        inv.onOpen(playerInv.player);

        // Input A
        addSlot(new Slot(inv, CreatorBlockEntity.SLOT_A, 44, 35));
        // Input B
        addSlot(new Slot(inv, CreatorBlockEntity.SLOT_B, 80, 35));
        // Output (locked)
        addSlot(new OutputSlot(inv, CreatorBlockEntity.SLOT_OUTPUT, 134, 35));

        // Player inventory
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));

        addProperties(delegate);
    }

    public CreatorScreenHandler(int syncId, PlayerInventory playerInv) {
        this(syncId, playerInv, new SimpleInventory(3), new ArrayPropertyDelegate(3));
    }

    public int getState()           { return delegate.get(0); }
    public int getProgress()        { return delegate.get(1); }
    public int getLastCreatedSlot() { return delegate.get(2); }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasStack()) {
            ItemStack original = slot.getStack();
            result = original.copy();
            if (index < inv.size()) {
                if (!insertItem(original, inv.size(), slots.size(), true))
                    return ItemStack.EMPTY;
            } else {
                if (!insertItem(original, 0, 2, false))
                    return ItemStack.EMPTY;
            }
            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return result;
    }

    @Override public boolean canUse(PlayerEntity player) { return inv.canPlayerUse(player); }

    private static class OutputSlot extends Slot {
        OutputSlot(Inventory inv, int index, int x, int y) { super(inv, index, x, y); }
        @Override public boolean canInsert(ItemStack stack) { return false; }
        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            if (inventory instanceof CreatorBlockEntity be) be.onOutputTaken();
            super.onTakeItem(player, stack);
        }
    }
}
