package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.ai.OpenRouterClient;
import com.alchemod.builder.BuilderProgram;
import com.alchemod.builder.BuilderPromptFactory;
import com.alchemod.builder.BuilderResponseParser;
import com.alchemod.builder.BuilderRuntime;
import com.alchemod.screen.BuilderScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class BuilderBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, Inventory {

    public static final int SLOT_A = 0;
    public static final int SLOT_B = 1;

    public static final int STATE_IDLE = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_BUILDING = 2;
    public static final int STATE_COMPLETE = 3;
    public static final int STATE_ERROR = 4;

    private static final int MAX_PROGRESS = 100;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);

    private int state = STATE_IDLE;
    private int progress = 0;
    private String promptText = "";
    private String lastError = "";
    private String lastBuildPlan = "";
    private boolean aiPending = false;

    private final PropertyDelegate delegate = new PropertyDelegate() {
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
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return 2;
        }
    };

    public BuilderBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemodInit.BUILDER_BE_TYPE, pos, state);
    }

    public void serverTick(World world, BlockPos pos) {
        if (state == STATE_PROCESSING) {
            progress = Math.min(progress + 1, MAX_PROGRESS - 1);
            markDirty();
        }
    }

    public void startBuild(String prompt, World world) {
        if (aiPending || prompt == null || prompt.isBlank()) {
            return;
        }

        promptText = prompt.trim();
        aiPending = true;
        state = STATE_PROCESSING;
        progress = 0;
        lastError = "";
        lastBuildPlan = "";
        markDirty();

        CompletableFuture.supplyAsync(() -> requestBuildResponse(promptText))
                .thenAccept(response -> {
                    if (world.getServer() != null) {
                        world.getServer().execute(() -> executeBuildResponse(response, world));
                    }
                });
    }

    public void receivePrompt(String prompt, World world) {
        if (prompt != null && !prompt.isBlank()) {
            startBuild(prompt, world);
        }
    }

    public String getLastBuildPlan() {
        return lastBuildPlan;
    }

    public String getLastBuildPlanSummary() {
        return summariseBuildPlan(lastBuildPlan);
    }

    private String requestBuildResponse(String prompt) {
        OpenRouterClient.ChatResult result = OpenRouterClient.chat(
                AlchemodInit.OPENROUTER_KEY,
                new OpenRouterClient.ChatRequest(
                        AlchemodInit.CONFIG.builderModel(),
                        AlchemodInit.CONFIG.builderMaxTokens(),
                        AlchemodInit.CONFIG.builderTimeoutSeconds(),
                        BuilderPromptFactory.buildSystemPrompt(),
                        BuilderPromptFactory.buildUserPrompt(prompt)));

        if (result.isError()) {
            AlchemodInit.LOG.error("[Builder] Request failed: {}", result.error());
            return "ERROR:" + result.error();
        }

        return result.content();
    }

    private void executeBuildResponse(String response, World world) {
        aiPending = false;

        if (response == null || response.isBlank() || response.startsWith("ERROR:")) {
            state = STATE_ERROR;
            progress = 0;
            lastError = response == null ? "Unknown builder error" : response;
            markDirty();
            return;
        }

        state = STATE_BUILDING;
        progress = 10;
        markDirty();

        try {
            BuilderProgram program = BuilderResponseParser.parse(response);
            lastBuildPlan = program.buildPlan();

            BlockPos origin = getPos().up();
            int fallbackSeed = Objects.hash(promptText, getPos().getX(), getPos().getY(), getPos().getZ());
            BuilderRuntime.ExecutionResult result = BuilderRuntime.execute(program, fallbackSeed,
                    (x, y, z, blockId) -> placeRelativeBlock(world, origin, x, y, z, blockId));

            progress = MAX_PROGRESS;
            state = STATE_COMPLETE;
            lastError = "";

            AlchemodInit.LOG.info("[Builder] Completed build with {} placements (legacy={}, seed={})",
                    result.placements(), result.legacyFallback(), result.seedUsed());
            if (!lastBuildPlan.isBlank()) {
                AlchemodInit.LOG.info("[Builder] Plan:\n{}", lastBuildPlan);
            }
        } catch (IllegalArgumentException e) {
            state = STATE_ERROR;
            progress = 0;
            lastError = e.getMessage();
            if (!lastBuildPlan.isBlank()) {
                AlchemodInit.LOG.warn("[Builder] Failed build plan:\n{}", lastBuildPlan);
            }
        } catch (Exception e) {
            state = STATE_ERROR;
            progress = 0;
            lastError = e.getMessage();
            if (!lastBuildPlan.isBlank()) {
                AlchemodInit.LOG.warn("[Builder] Failed build plan:\n{}", lastBuildPlan);
            }
            AlchemodInit.LOG.error("[Builder] Failed to execute structure code", e);
        }

        markDirty();
    }

    private void placeRelativeBlock(World world, BlockPos origin, int x, int y, int z, String blockId) {
        BlockPos target = origin.add(x, y, z);
        if (!world.isInBuildLimit(target)) {
            return;
        }

        Identifier identifier = Identifier.of(blockId);
        if (!Registries.BLOCK.containsId(identifier)) {
            throw new IllegalArgumentException("Unknown block id: " + blockId);
        }

        Block block = Registries.BLOCK.get(identifier);
        BlockState blockState = block.getDefaultState();
        if (!blockState.getFluidState().isEmpty() || blockState.hasBlockEntity()) {
            throw new IllegalArgumentException("Builder runtime rejected unsafe block " + blockId);
        }

        world.setBlockState(target, blockState, Block.NOTIFY_ALL);
    }

    public static String summariseBuildPlan(String plan) {
        if (plan == null || plan.isBlank()) {
            return "";
        }

        String singleLine = plan.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 117) + "...";
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        items.clear();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.alchemod.build_creator");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new BuilderScreenHandler(syncId, playerInventory, this, delegate, getPos());
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        Inventories.writeNbt(nbt, items, registryLookup);
        nbt.putInt("State", state);
        nbt.putInt("Progress", progress);
        nbt.putString("Prompt", promptText);
        nbt.putString("Error", lastError);
        nbt.putString("BuildPlan", lastBuildPlan);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        Inventories.readNbt(nbt, items, registryLookup);
        state = nbt.getInt("State");
        progress = nbt.getInt("Progress");
        promptText = nbt.getString("Prompt");
        lastError = nbt.getString("Error");
        lastBuildPlan = nbt.getString("BuildPlan");
        if (state == STATE_PROCESSING || state == STATE_BUILDING) {
            state = STATE_IDLE;
            progress = 0;
            aiPending = false;
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    public int getState() {
        return state;
    }

    public int getProgress() {
        return progress;
    }

    public String getPrompt() {
        return promptText;
    }

    public String getError() {
        return lastError;
    }

    public PropertyDelegate getDelegate() {
        return delegate;
    }
}
