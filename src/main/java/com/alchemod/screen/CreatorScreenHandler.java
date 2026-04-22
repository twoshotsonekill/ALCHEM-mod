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
import net.minecraft.util.math.BlockPos;

public class CreatorScreenHandler extends ScreenHandler {

    private static final int STATE_PROCESSING = 1;

    private final Inventory inventory;
    private final PropertyDelegate delegate;
    private final BlockPos blockPos;

    public CreatorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate delegate, BlockPos blockPos) {
        super(AlchemodInit.CREATOR_HANDLER, syncId);
        checkSize(inventory, 3);
        this.inventory = inventory;
        this.delegate = delegate;
        this.blockPos = blockPos;
        inventory.onOpen(playerInventory.player);

        addSlot(new InputSlot(inventory, CreatorBlockEntity.SLOT_A, 56, 17));
        addSlot(new InputSlot(inventory, CreatorBlockEntity.SLOT_B, 56, 53));
        addSlot(new OutputSlot(inventory, CreatorBlockEntity.SLOT_OUTPUT, 116, 35));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }

        addProperties(delegate);
    }

    public CreatorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(3), new ArrayPropertyDelegate(4), null);
    }

    public int getState() {
        return delegate.get(0);
    }

    public int getProgress() {
        return delegate.get(1);
    }

    public int getLastCreatedSlot() {
        return delegate.get(2);
    }

    public boolean isBehaviorCodeEnabled() {
        return delegate.get(3) != 0;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    private boolean isProcessing() {
        return getState() == STATE_PROCESSING;
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
        } else if (!insertItem(original, 0, 2, false)) {
            return ItemStack.EMPTY;
        }

        if (original.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
            if (index == CreatorBlockEntity.SLOT_OUTPUT && inventory instanceof CreatorBlockEntity creatorBlockEntity) {
                creatorBlockEntity.onOutputTaken();
            }
        } else {
            slot.markDirty();
        }

        return result;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inventory.canPlayerUse(player);
    }

    private final class InputSlot extends Slot {
        private InputSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return !isProcessing();
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return !isProcessing();
        }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            if (inventory instanceof CreatorBlockEntity creatorBlockEntity) {
                creatorBlockEntity.onOutputTaken();
            }
            super.onTakeItem(player, stack);
        }
    }
}
