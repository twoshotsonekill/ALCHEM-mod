package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.screen.BuilderScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuilderBlockEntity extends BlockEntity
        implements NamedScreenHandlerFactory, Inventory {

    // Inventory slots
    public static final int SLOT_A      = 0;
    public static final int SLOT_B      = 1;

    // State values (synced to client via PropertyDelegate)
    public static final int STATE_IDLE       = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_BUILDING   = 2;
    public static final int STATE_COMPLETE   = 3;
    public static final int STATE_ERROR      = 4;

    private static final int MAX_PROGRESS = 100;

    // 2 inventory slots - two inputs only
    private final net.minecraft.util.collection.DefaultedList<ItemStack> items =
            net.minecraft.util.collection.DefaultedList.ofSize(2, ItemStack.EMPTY);

    private int state = STATE_IDLE;
    private int progress = 0;
    private String promptText = "";
    private String lastError = "";
    private boolean aiPending = false;

    private final PropertyDelegate delegate = new PropertyDelegate() {
        @Override public int get(int i) { return i == 0 ? state : progress; }
        @Override public void set(int i, int v) { if (i == 0) state = v; else progress = v; }
        @Override public int size() { return 2; }
    };

    public BuilderBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemodInit.BUILDER_BE_TYPE, pos, state);
    }

    // ── Server tick ───────────────────────────────────────────────────────────
    public void serverTick(World world, BlockPos pos) {
        // Start building when both input slots have items and output is empty
        if (state == STATE_IDLE
                && !items.get(SLOT_A).isEmpty()
                && !items.get(SLOT_B).isEmpty()
                && !aiPending) {
            String itemA = items.get(SLOT_A).getName().getString();
            String itemB = items.get(SLOT_B).getName().getString();
            startBuild(itemA + " and " + itemB, world);
        }

        // Animate progress bar while waiting for AI
        if (state == STATE_PROCESSING && progress < MAX_PROGRESS - 1) {
            progress++;
            markDirty();
        }

        // Building completes automatically after generation
        if (state == STATE_BUILDING) {
            progress++;
            if (progress >= MAX_PROGRESS) {
                state = STATE_COMPLETE;
                // Consume input items on completion
                items.get(SLOT_A).decrement(1);
                items.get(SLOT_B).decrement(1);
                markDirty();
            }
        }
    }

    // ── Kick off AI generation ─────────────────────────────────────────────────
    public void startBuild(String prompt, World world) {
        if (aiPending) return;

        promptText = prompt;
        aiPending = true;
        state = STATE_PROCESSING;
        progress = 0;
        lastError = "";
        markDirty();

        AlchemodInit.LOG.info("[Alchemod] Builder generating structure from prompt: {}", prompt);

        CompletableFuture.supplyAsync(() -> generateStructureCode(prompt))
                .thenAccept(code -> {
                    if (world.getServer() != null) {
                        world.getServer().execute(() -> executeStructureCode(code, world));
                    }
                });
    }

    // ── Generate code using OpenRouter ────────────────────────────────────────
    private String generateStructureCode(String prompt) {
        String key = AlchemodInit.OPENROUTER_KEY;
        if (key.isBlank()) {
            lastError = "API key not set";
            AlchemodInit.LOG.error("[Alchemod] OPENROUTER_API_KEY env var not set!");
            return "ERROR:no_key";
        }

        // System prompt instructs the AI to generate structure code
        String system = "You are a Minecraft builder AI. Generate JavaScript code that uses these functions:\n" +
                "- block(x, y, z, type) places a single block\n" +
                "- box(x1, y1, z1, x2, y2, z2, type) fills a rectangular region\n" +
                "- line(x1, y1, z1, x2, y2, z2, type) creates a line of blocks\n" +
                "- sphere(xc, yc, zc, r, type) creates a hollow sphere\n\n" +
                "Valid block types: stone, dirt, grass_block, oak_wood, oak_planks, obsidian, diamond_block, gold_ore.\n" +
                "Return ONLY valid JavaScript code that calls these functions. No explanation, no markdown.";

        String user = "Build this structure: " + prompt;

        // Build JSON request
        String body = "{" +
                "\"model\":\"openai/gpt-4o-mini\"," +
                "\"max_tokens\":500," +
                "\"messages\":[" +
                "{\"role\":\"system\",\"content\":" + quoted(system) + "}," +
                "{\"role\":\"user\",\"content\":" + quoted(user) + "}" +
                "]}";

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .header("HTTP-Referer", "https://github.com/alchemod")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(20))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                String errMsg = "HTTP " + resp.statusCode();
                lastError = errMsg;
                AlchemodInit.LOG.error("[Alchemod] OpenRouter error: {}", errMsg);
                return "ERROR:" + errMsg;
            }

            // Extract code from response
            String respBody = resp.body();
            try {
                Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(respBody);
                if (m.find()) {
                    String code = m.group(1)
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                    return code;
                }
            } catch (Exception e) {
                lastError = "Parse error";
                AlchemodInit.LOG.error("[Alchemod] Failed to parse response: {}", e.getMessage());
            }

            return "ERROR:parse";
        } catch (Exception e) {
            lastError = e.getMessage();
            AlchemodInit.LOG.error("[Alchemod] Builder request failed: {}", e.getMessage());
            return "ERROR:" + e.getMessage();
        }
    }

    // ── Execute the generated code ─────────────────────────────────────────────
    private void executeStructureCode(String code, World world) {
        if (code.startsWith("ERROR:")) {
            state = STATE_ERROR;
            lastError = code;
            aiPending = false;
            markDirty();
            AlchemodInit.LOG.error("[Alchemod] Structure generation failed: {}", code);
            return;
        }

        state = STATE_BUILDING;
        progress = 0;
        aiPending = false;
        markDirty();

        // Simple code execution - place blocks based on parsed instructions
        try {
            // Parse and execute basic block placement commands
            String[] lines = code.split("\n");
            BlockPos origin = getPos().up(); // Place structures above the builder block

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) continue;

                // Parse block(x, y, z, type)
                if (line.startsWith("block(")) {
                    try {
                        String args = line.substring(6, line.lastIndexOf(")"));
                        String[] parts = args.split(",");
                        if (parts.length >= 4) {
                            int x = Integer.parseInt(parts[0].trim());
                            int y = Integer.parseInt(parts[1].trim());
                            int z = Integer.parseInt(parts[2].trim());
                            String blockType = parts[3].trim().replace("\"", "").replace("'", "");

                            placeBlock(world, origin.add(x, y, z), blockType);
                        }
                    } catch (Exception e) {
                        AlchemodInit.LOG.warn("[Alchemod] Failed to execute block command: {}", line);
                    }
                }

                // Parse box(x1, y1, z1, x2, y2, z2, type)
                if (line.startsWith("box(")) {
                    try {
                        String args = line.substring(4, line.lastIndexOf(")"));
                        String[] parts = args.split(",");
                        if (parts.length >= 7) {
                            int x1 = Integer.parseInt(parts[0].trim());
                            int y1 = Integer.parseInt(parts[1].trim());
                            int z1 = Integer.parseInt(parts[2].trim());
                            int x2 = Integer.parseInt(parts[3].trim());
                            int y2 = Integer.parseInt(parts[4].trim());
                            int z2 = Integer.parseInt(parts[5].trim());
                            String blockType = parts[6].trim().replace("\"", "").replace("'", "");

                            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                                        placeBlock(world, origin.add(x, y, z), blockType);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        AlchemodInit.LOG.warn("[Alchemod] Failed to execute box command: {}", line);
                    }
                }
            }

            AlchemodInit.LOG.info("[Alchemod] Structure built successfully");
            state = STATE_COMPLETE;
        } catch (Exception e) {
            state = STATE_ERROR;
            lastError = e.getMessage();
            AlchemodInit.LOG.error("[Alchemod] Failed to execute structure code: {}", e.getMessage());
        }
        markDirty();
    }

    // ── Helper: place a block by type name ──────────────────────────────────────
    private void placeBlock(World world, BlockPos pos, String blockType) {
        try {
            // Parse "namespace:block_name" or default to minecraft: namespace
            var block = blockType.contains(":") 
                ? Registries.BLOCK.get(net.minecraft.util.Identifier.of(blockType))
                : Registries.BLOCK.get(net.minecraft.util.Identifier.of("minecraft", blockType));
            
            if (block != null) {
                world.setBlockState(pos, block.getDefaultState());
            }
        } catch (Exception e) {
            AlchemodInit.LOG.debug("[Alchemod] Invalid block type: {}", blockType);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private static String quoted(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ── Inventory implementation ───────────────────────────────────────────────
    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getStack(int slot) {
        return items.get(slot);
    }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack result = net.minecraft.inventory.Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }
    @Override public ItemStack removeStack(int slot) {
        return net.minecraft.inventory.Inventories.removeStack(items, slot);
    }
    @Override public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }
    @Override public void markDirty() {
        if (this.world != null) this.world.markDirty(this.pos);
    }
    @Override public boolean canPlayerUse(PlayerEntity player) {
        if (world == null) return false;
        return !(world.getBlockEntity(pos) != this) && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
    @Override public void clear() {
        items.clear();
    }
    @Override public void onOpen(PlayerEntity player) { }
    @Override public void onClose(PlayerEntity player) { }

    // ── Screen handler factory ─────────────────────────────────────────────────
    @Override
    public Text getDisplayName() {
        return Text.literal("Build Creator");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new BuilderScreenHandler(syncId, playerInventory, this, delegate, getPos());
    }

    // ── NBT persistence ───────────────────────────────────────────────────────
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.putInt("state", state);
        nbt.putInt("progress", progress);
        nbt.putString("prompt", promptText);
        nbt.putString("error", lastError);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        state = nbt.getInt("state");
        progress = nbt.getInt("progress");
        promptText = nbt.getString("prompt");
        lastError = nbt.getString("error");
    }

    // ── Getters ────────────────────────────────────────────────────────────────
    public int getState() { return state; }
    public int getProgress() { return progress; }
    public String getPrompt() { return promptText; }
    public String getError() { return lastError; }
    public PropertyDelegate getDelegate() { return delegate; }

    // Called from server when player sends text prompt via packet
    public void receivePrompt(String prompt, World world) {
        if (prompt != null && !prompt.isBlank()) {
            startBuild(prompt, world);
        }
    }
}
