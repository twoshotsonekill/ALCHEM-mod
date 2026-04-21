package com.alchemod.screen;

import com.alchemod.AlchemodInit;
import com.alchemod.block.ForgeBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class ForgeScreenHandler extends ScreenHandler {

    private final Inventory inv;
    private final PropertyDelegate delegate;

    // Server-side constructor (real inventory passed in)
    public ForgeScreenHandler(int syncId, PlayerInventory playerInv,
            Inventory inv, PropertyDelegate delegate) {
        super(AlchemodInit.FORGE_HANDLER, syncId);
        checkSize(inv, 3);
        this.inv      = inv;
        this.delegate = delegate;
        inv.onOpen(playerInv.player);

        // Slot A — left input  (centred in the GUI at x=44, y=35)
        addSlot(new Slot(inv, ForgeBlockEntity.SLOT_A, 44, 35));

        // Slot B — right input  (x=80, y=35)
        addSlot(new Slot(inv, ForgeBlockEntity.SLOT_B, 80, 35));

        // Output slot — locked to insertion, notifies BE when taken
        addSlot(new OutputSlot(inv, ForgeBlockEntity.SLOT_OUTPUT, 134, 35));

        // Player main inventory (3 rows)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));

        // Hotbar
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));

        addProperties(delegate);
    }

    // Client-side constructor (dummy inventory, server will sync)
    public ForgeScreenHandler(int syncId, PlayerInventory playerInv) {
        this(syncId, playerInv, new SimpleInventory(3), new ArrayPropertyDelegate(2));
    }

    // ── Expose synced values to the Screen ────────────────────────────────────
    public int getState()       { return delegate.get(0); }
    public int getProgress()    { return delegate.get(1); }

    // ── Shift-click ───────────────────────────────────────────────────────────
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasStack()) {
            ItemStack original = slot.getStack();
            result = original.copy();
            if (index < inv.size()) {
                // Machine → player
                if (!insertItem(original, inv.size(), slots.size(), true))
                    return ItemStack.EMPTY;
            } else {
                // Player → first free input slot
                if (!insertItem(original, 0, 2, false))
                    return ItemStack.EMPTY;
            }
            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return result;
    }

    @Override
    public boolean canUse(PlayerEntity player) { return inv.canPlayerUse(player); }

    // ── Output slot ───────────────────────────────────────────────────────────
    private static class OutputSlot extends Slot {
        OutputSlot(Inventory inv, int index, int x, int y) { super(inv, index, x, y); }

        @Override public boolean canInsert(ItemStack stack) { return false; }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            if (inventory instanceof ForgeBlockEntity be) be.onOutputTaken();
            super.onTakeItem(player, stack);
        }
    }
}
