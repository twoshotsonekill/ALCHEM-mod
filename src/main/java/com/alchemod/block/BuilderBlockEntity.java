package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.ai.OpenRouterClient;
import com.alchemod.builder.BuilderBuildPlanner;
import com.alchemod.builder.BuilderDiagnostics;
import com.alchemod.builder.BuilderProgram;
import com.alchemod.builder.BuilderPromptFactory;
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
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    private BuilderDiagnostics diagnostics = BuilderDiagnostics.idle();
    private boolean aiPending = false;

    // Async block placement queue to prevent server thread freezes
    private final Queue<PlacementTask> placementQueue = new ConcurrentLinkedQueue<>();
    private int totalPlacements = 0;
    private int completedPlacements = 0;
    private static final int PLACEMENTS_PER_TICK = 256;

    // Per-player cooldown to prevent API cost abuse
    private long lastPromptTime = 0;
    private static final long COOLDOWN_MS = 30_000;  // 30 seconds

    // Auto-reset timer for STATE_COMPLETE
    private static final long AUTO_RESET_DELAY_MS = 3_000;  // 3 seconds
    private long completedTime = 0;

    private final PropertyDelegate delegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> state;
                case 1 -> progress;
                case 2 -> diagnostics.parseStatusCode();
                case 3 -> diagnostics.repairAttempted() ? 1 : 0;
                case 4 -> diagnostics.fallbackReasonCode();
                case 5 -> Math.min(BuilderRuntime.MAX_BLOCK_PLACEMENTS, diagnostics.placementCount());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> state = value;
                case 1 -> progress = value;
                case 2 -> diagnostics = new BuilderDiagnostics(
                        BuilderDiagnostics.statusFromCode(value),
                        diagnostics.repairAttempted(),
                        diagnostics.fallbackReason(),
                        diagnostics.placementCount(),
                        diagnostics.seed(),
                        diagnostics.errorSummary());
                case 3 -> diagnostics = new BuilderDiagnostics(
                        diagnostics.parseStatus(),
                        value != 0,
                        diagnostics.fallbackReason(),
                        diagnostics.placementCount(),
                        diagnostics.seed(),
                        diagnostics.errorSummary());
                case 4 -> diagnostics = new BuilderDiagnostics(
                        diagnostics.parseStatus(),
                        diagnostics.repairAttempted(),
                        BuilderDiagnostics.fallbackReasonFromCode(value),
                        diagnostics.placementCount(),
                        diagnostics.seed(),
                        diagnostics.errorSummary());
                case 5 -> diagnostics = new BuilderDiagnostics(
                        diagnostics.parseStatus(),
                        diagnostics.repairAttempted(),
                        diagnostics.fallbackReason(),
                        value,
                        diagnostics.seed(),
                        diagnostics.errorSummary());
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return 6;
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

        // Drain placement queue gradually to prevent server freezes
        if (state == STATE_BUILDING && !placementQueue.isEmpty()) {
            int placed = 0;
            while (!placementQueue.isEmpty() && placed < PLACEMENTS_PER_TICK) {
                PlacementTask task = placementQueue.poll();
                try {
                    placeRelativeBlock(world, task.origin(), task.x(), task.y(), task.z(), task.blockId());
                    completedPlacements++;
                    placed++;
                } catch (Exception e) {
                    AlchemodInit.LOG.warn("[Builder] Failed to place block: {}", e.getMessage());
                }
            }

            // Update progress based on placement completion
            if (totalPlacements > 0) {
                progress = 10 + (int) (85.0 * completedPlacements / totalPlacements);
            }

            // Transition to complete when queue is empty
            if (placementQueue.isEmpty() && completedPlacements >= totalPlacements) {
                progress = MAX_PROGRESS;
                state = STATE_COMPLETE;
                completedTime = System.currentTimeMillis();
                AlchemodInit.LOG.info("[Builder] Completed async build with {} placements", completedPlacements);
            }

            markDirty();
        }

        // Auto-reset STATE_COMPLETE after 3 seconds
        if (state == STATE_COMPLETE && completedTime > 0) {
            if (System.currentTimeMillis() - completedTime > AUTO_RESET_DELAY_MS) {
                state = STATE_IDLE;
                progress = 0;
                completedTime = 0;
                markDirty();
            }
        }
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
        nbt.putString("DiagnosticsParse", diagnostics.parseStatus());
        nbt.putBoolean("DiagnosticsRepair", diagnostics.repairAttempted());
        nbt.putString("DiagnosticsFallback", diagnostics.fallbackReason());
        nbt.putInt("DiagnosticsPlacements", diagnostics.placementCount());
        nbt.putInt("DiagnosticsSeed", diagnostics.seed());
        nbt.putString("DiagnosticsError", diagnostics.errorSummary());
        nbt.putLong("lastPromptTime", lastPromptTime);
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
        diagnostics = new BuilderDiagnostics(
                nbt.getString("DiagnosticsParse").isBlank()
                        ? BuilderDiagnostics.STATUS_IDLE
                        : nbt.getString("DiagnosticsParse"),
                nbt.getBoolean("DiagnosticsRepair"),
                nbt.getString("DiagnosticsFallback"),
                nbt.getInt("DiagnosticsPlacements"),
                nbt.getInt("DiagnosticsSeed"),
                nbt.getString("DiagnosticsError"));
        lastPromptTime = nbt.getLong("lastPromptTime");
        if (state == STATE_PROCESSING || state == STATE_BUILDING) {
            state = STATE_IDLE;
            progress = 0;
            aiPending = false;
        }
}

    public void startBuild(String prompt, World world) {
        if (aiPending || prompt == null || prompt.isBlank()) {
            return;
        }

        // Check cooldown
        long now = System.currentTimeMillis();
        if (now - lastPromptTime < COOLDOWN_MS) {
            long remainingMs = COOLDOWN_MS - (now - lastPromptTime);
            long remainingSeconds = (remainingMs + 999) / 1000;  // Round up
            AlchemodInit.LOG.info("[Builder] Cooldown active: {} seconds remaining", remainingSeconds);
            return;
        }

        lastPromptTime = now;

        promptText = prompt.trim();
        aiPending = true;
        state = STATE_PROCESSING;
        progress = 0;
        lastError = "";
        lastBuildPlan = "";
        diagnostics = BuilderDiagnostics.idle();
        markDirty();

        CompletableFuture.supplyAsync(() -> createBuildPlan(promptText))
                .thenAccept(result -> {
                    if (world.getServer() != null) {
                        world.getServer().execute(() -> queueBuildResult(result, world));
                    }
                });
    }

    public void receivePrompt(String prompt, World world) {
        if (prompt != null && !prompt.isBlank()) {
            startBuild(prompt, world);
        }
    }

    public boolean isOnCooldown() {
        return System.currentTimeMillis() - lastPromptTime < COOLDOWN_MS;
    }

    public long getRemainingCooldownMs() {
        long remaining = COOLDOWN_MS - (System.currentTimeMillis() - lastPromptTime);
        return Math.max(0, remaining);
    }

    public String getLastBuildPlan() {
        return lastBuildPlan;
    }

    public String getLastBuildPlanSummary() {
        return summariseBuildPlan(lastBuildPlan);
    }

    private BuilderBuildPlanner.BuildResult createBuildPlan(String prompt) {
        int fallbackSeed = Objects.hash(prompt, getPos().getX(), getPos().getY(), getPos().getZ());

        OpenRouterClient.ChatResult result = OpenRouterClient.chat(
                AlchemodInit.OPENROUTER_KEY,
                new OpenRouterClient.ChatRequest(
                        AlchemodInit.CONFIG.builderModel(),
                        AlchemodInit.CONFIG.builderMaxTokens(),
                        AlchemodInit.CONFIG.builderTimeoutSeconds(),
                        BuilderPromptFactory.buildSystemPrompt(),
                        BuilderPromptFactory.buildUserPrompt(prompt)));

        String rawResponse = result.rawBody() != null ? result.rawBody() : result.content();
        AlchemodInit.LOG.info("[Builder] Raw response:\n{}", rawResponse);

        BuilderBuildPlanner.BuildResult planned = BuilderBuildPlanner.plan(
                prompt,
                result.content() != null ? result.content() : result.rawBody(),
                result.error(),
                fallbackSeed,
                (systemPrompt, userPrompt) -> {
                    AlchemodInit.LOG.info("[Builder] Attempting one DeepSeek repair retry.");
                    OpenRouterClient.ChatResult repaired = OpenRouterClient.chat(
                            AlchemodInit.OPENROUTER_KEY,
                            new OpenRouterClient.ChatRequest(
                                    AlchemodInit.CONFIG.builderModel(),
                                    AlchemodInit.CONFIG.builderMaxTokens(),
                                    AlchemodInit.CONFIG.builderTimeoutSeconds(),
                                    systemPrompt,
                                    userPrompt));
                    String repairedRaw = repaired.rawBody() != null ? repaired.rawBody() : repaired.content();
                    AlchemodInit.LOG.info("[Builder] Repaired response:\n{}", repairedRaw);
                    return repaired;
                });

        if (planned.validationError() != null && !planned.validationError().isBlank()) {
            AlchemodInit.LOG.warn("[Builder] Validation/fallback reason: {}", planned.validationError());
        }
        if (planned.program() != null) {
            AlchemodInit.LOG.info("[Builder] Final code:\n{}", planned.program().code());
        }
        return planned;
    }

    private void queueBuildResult(BuilderBuildPlanner.BuildResult result, World world) {
        aiPending = false;

        if (result == null || !result.ok()) {
            state = STATE_ERROR;
            progress = 0;
            diagnostics = result != null ? result.diagnostics() : BuilderDiagnostics.failed(false, "Unknown builder error");
            lastError = diagnostics.errorSummary().isBlank() ? "Unknown builder error" : diagnostics.errorSummary();
            markDirty();
            return;
        }

        completedPlacements = 0;
        totalPlacements = 0;
        placementQueue.clear();

        try {
            BuilderProgram program = result.program();
            BuilderRuntime.PlacementPreview preview = result.preview();
            lastBuildPlan = program.buildPlan();
            diagnostics = result.diagnostics();

            BlockPos origin = getPos().up();
            preflightWorldPlacements(world, origin, preview);

            for (BuilderRuntime.Placement placement : preview.placements()) {
                placementQueue.offer(new PlacementTask(
                        origin, placement.x(), placement.y(), placement.z(), placement.blockId()));
            }

            state = STATE_BUILDING;
            progress = 10;
            totalPlacements = preview.placementCount();
            completedPlacements = 0;

            lastError = "";

            AlchemodInit.LOG.info("[Builder] Queued {} placements for async execution (legacy={}, seed={}, repair={}, status={})",
                    preview.placementCount(), preview.legacyFallback(), preview.seedUsed(),
                    diagnostics.repairAttempted(), diagnostics.parseStatus());
            if (!lastBuildPlan.isBlank()) {
                AlchemodInit.LOG.info("[Builder] Plan:\n{}", lastBuildPlan);
            }
        } catch (IllegalArgumentException e) {
            state = STATE_ERROR;
            progress = 0;
            lastError = e.getMessage();
            diagnostics = BuilderDiagnostics.failed(result.diagnostics().repairAttempted(), e.getMessage());
            AlchemodInit.LOG.warn("[Builder] Failed build plan:\n{}", lastBuildPlan);
            AlchemodInit.LOG.warn("[Builder] Validation error: {}", e.getMessage());
        } catch (Exception e) {
            state = STATE_ERROR;
            progress = 0;
            lastError = e.getMessage();
            diagnostics = BuilderDiagnostics.failed(result.diagnostics().repairAttempted(), e.getMessage());
            AlchemodInit.LOG.warn("[Builder] Failed build plan:\n{}", lastBuildPlan);
            AlchemodInit.LOG.error("[Builder] Failed to execute structure code", e);
        }

        markDirty();
    }

    /**
     * Record for a single block placement task in the async queue.
     */
    private record PlacementTask(BlockPos origin, int x, int y, int z, String blockId) {}

    private void preflightWorldPlacements(World world, BlockPos origin, BuilderRuntime.PlacementPreview preview) {
        if (preview == null || preview.placements().isEmpty()) {
            throw new IllegalArgumentException("Builder preview produced no placements");
        }

        for (BuilderRuntime.Placement placement : preview.placements()) {
            BlockPos target = origin.add(placement.x(), placement.y(), placement.z());
            if (!world.isInBuildLimit(target)) {
                throw new IllegalArgumentException("Generated structure exceeded world build limits");
            }

            Identifier identifier = Identifier.of(placement.blockId());
            if (!Registries.BLOCK.containsId(identifier)) {
                throw new IllegalArgumentException("Unknown block id: " + placement.blockId());
            }

            Block block = Registries.BLOCK.get(identifier);
            BlockState blockState = block.getDefaultState();
            if (!blockState.getFluidState().isEmpty() || blockState.hasBlockEntity()) {
                throw new IllegalArgumentException("Builder runtime rejected unsafe block " + placement.blockId());
            }
        }
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

    public BuilderDiagnostics getDiagnostics() {
        return diagnostics;
    }

    public PropertyDelegate getDelegate() {
        return delegate;
    }
}
