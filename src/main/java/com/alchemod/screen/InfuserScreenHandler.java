package com.alchemod.screen;

import com.alchemod.block.InfuserBlockEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class InfuserScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    private final PropertyDelegate delegate;

    public InfuserScreenHandler(int syncId, PlayerInventory playerInv) {
        this(syncId, playerInv, new InfuserBlockEntity(
                net.minecraft.util.math.BlockPos.ORIGIN,
                net.minecraft.block.Blocks.AIR.getDefaultState()),
             new ArrayPropertyDelegate(2));
    }

    public InfuserScreenHandler(int syncId, PlayerInventory playerInv,
                                Inventory inv, PropertyDelegate delegate) {
        super(com.alchemod.AlchemodInit.INFUSER_HANDLER, syncId);
        this.inventory = inv;
        this.delegate = delegate;

        // Infuser slots: base, ingredient A, ingredient B, output
        this.addSlot(new Slot(inv, InfuserBlockEntity.SLOT_BASE,      56, 17));
        this.addSlot(new Slot(inv, InfuserBlockEntity.SLOT_INGREDIENT_A, 56, 53));
        this.addSlot(new Slot(inv, InfuserBlockEntity.SLOT_INGREDIENT_B, 80, 35));
        this.addSlot(new Slot(inv, InfuserBlockEntity.SLOT_OUTPUT,      116, 35) {
            @Override
            public boolean canInsert(ItemStack stack) { return false; }
            @Override
            public void onTakeItem(PlayerEntity player, ItemStack stack) {
                if (inv instanceof InfuserBlockEntity infuser) {
                    infuser.onOutputTaken();
                }
                super.onTakeItem(player, stack);
            }
        });

        // Player inventory slots
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        this.addProperties(delegate);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasStack()) {
            ItemStack stack = slot.getStack();
            result = stack.copy();
            if (slotIndex < 4) {
                if (!this.insertItem(stack, 4, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(stack, 0, 4, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return result;
    }

    public Inventory getInventory() { return inventory; }
}
