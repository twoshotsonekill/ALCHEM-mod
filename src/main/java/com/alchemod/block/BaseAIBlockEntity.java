package com.alchemod.block;

import com.alchemod.AlchemodInit;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.util.collection.DefaultedList;

/**
 * Base class for AI-driven block entities (Builder, Creator, Forge, Infuser, Transmuter).
 *
 * Consolidates shared state machine logic:
 * - State constants (IDLE, PROCESSING, READY, ERROR)
 * - PropertyDelegate for syncing state and progress to client
 * - NBT persistence of state and progress
 * - AI-pending flag for async operations
 *
 * Subclasses must:
 * 1. Call super() constructor with the appropriate BlockEntityType
 * 2. Define state constants if they need different values
 * 3. Implement serverTick() for game logic
 * 4. Override readNbt/writeNbt if they need custom persistence
 */
public abstract class BaseAIBlockEntity extends BlockEntity implements Inventory {

    // ── State constants (shared across all AI block types) ──────────────────

    public static final int STATE_IDLE = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_ERROR = 3;

    // ── Shared state (all AI blocks have these) ─────────────────────────────

    protected int state = STATE_IDLE;
    protected int progress = 0;
    protected boolean aiPending = false;

    protected final PropertyDelegate delegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> state;
                case 1 -> progress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> state = value;
                case 1 -> progress = value;
                default -> {}
            }
        }

        @Override
        public int size() {
            return 2;
        }
    };

    // ── Constructor ────────────────────────────────────────────────────────

    protected BaseAIBlockEntity(BlockEntityType<?> type, net.minecraft.util.math.BlockPos pos, 
            net.minecraft.block.BlockState state) {
        super(type, pos, state);
    }

    // ── Abstract methods (subclasses must implement) ────────────────────────

    /**
     * Called every server tick to update block entity logic.
     */
    public abstract void serverTick(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos);

    // ── Shared helpers ─────────────────────────────────────────────────────

    /**
     * Get the PropertyDelegate for syncing state to client.
     */
    public PropertyDelegate getDelegate() {
        return delegate;
    }

    /**
     * Get current state.
     */
    public int getState() {
        return state;
    }

    /**
     * Set new state (marks dirty).
     */
    public void setState(int newState) {
        this.state = newState;
        markDirty();
    }

    /**
     * Get current progress (0-100 typically).
     */
    public int getProgress() {
        return progress;
    }

    /**
     * Set progress (marks dirty).
     */
    public void setProgress(int newProgress) {
        this.progress = newProgress;
        markDirty();
    }

    /**
     * Check if AI task is pending (async).
     */
    public boolean isAIPending() {
        return aiPending;
    }

    /**
     * Mark AI task as pending or completed.
     */
    public void setAIPending(boolean pending) {
        this.aiPending = pending;
        markDirty();
    }

    /**
     * Reset block to idle state.
     */
    public void resetToIdle() {
        this.state = STATE_IDLE;
        this.progress = 0;
        this.aiPending = false;
        markDirty();
    }

    // ── NBT persistence (subclasses should call super) ────────────────────

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putInt("State", state);
        nbt.putInt("Progress", progress);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        state = nbt.getInt("State");
        progress = nbt.getInt("Progress");

        // Reset async tasks on load to prevent stuck states
        if (state == STATE_PROCESSING || state == STATE_READY) {
            state = STATE_IDLE;
            progress = 0;
            aiPending = false;
        }
    }

    // ── Inventory stub (subclasses implement if needed) ──────────────────

    @Override
    public int size() {
        return 0; // Override in subclass if this block has inventory
    }

    @Override
    public boolean isEmpty() {
        return true; // Override in subclass
    }

    @Override
    public ItemStack getStack(int slot) {
        return ItemStack.EMPTY; // Override in subclass
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return ItemStack.EMPTY; // Override in subclass
    }

    @Override
    public ItemStack removeStack(int slot) {
        return ItemStack.EMPTY; // Override in subclass
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        // Override in subclass
    }

    @Override
    public void clear() {
        // Override in subclass
    }

    @Override
    public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        return true; // Override in subclass for proper validation
    }
}
