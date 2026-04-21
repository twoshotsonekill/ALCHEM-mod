package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.screen.ForgeScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ForgeBlockEntity extends BlockEntity
        implements NamedScreenHandlerFactory, Inventory {

    // Slot indices
    public static final int SLOT_A      = 0;
    public static final int SLOT_B      = 1;
    public static final int SLOT_OUTPUT = 2;

    // State values (synced to client via PropertyDelegate index 0)
    public static final int STATE_IDLE       = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_READY      = 2;
    public static final int STATE_ERROR      = 3;

    // Progress constants (synced at index 1)
    private static final int MAX_PROGRESS = 80; // ~4 seconds at 20 tps

    private final DefaultedList<ItemStack> items =
            DefaultedList.ofSize(3, ItemStack.EMPTY);

    private int state    = STATE_IDLE;
    private int progress = 0;           // counts up while AI is pending
    private boolean aiPending = false;

    // Two ints synced to client: state + progress
    private final PropertyDelegate delegate = new PropertyDelegate() {
        @Override public int get(int i) { return i == 0 ? state : progress; }
        @Override public void set(int i, int v) { if (i == 0) state = v; else progress = v; }
        @Override public int size() { return 2; }
    };

    public ForgeBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemodInit.FORGE_BE_TYPE, pos, state);
    }

    // ── Server tick ───────────────────────────────────────────────────────────
    public void serverTick(World world, BlockPos pos) {
        // Start processing when both inputs are filled and output is empty
        if (state == STATE_IDLE
                && !items.get(SLOT_A).isEmpty()
                && !items.get(SLOT_B).isEmpty()
                && items.get(SLOT_OUTPUT).isEmpty()
                && !aiPending) {
            startCombination(world);
        }

        // Animate progress bar while waiting for AI
        if (state == STATE_PROCESSING) {
            progress = Math.min(progress + 1, MAX_PROGRESS - 1); // stop just before done
            markDirty();
        }
    }

    // ── Kick off the OpenRouter request on a worker thread ────────────────────
    private void startCombination(World world) {
        aiPending = true;
        state = STATE_PROCESSING;
        progress = 0;
        markDirty();

        String itemA = items.get(SLOT_A).getName().getString()
                       + " (" + Registries.ITEM.getId(items.get(SLOT_A).getItem()) + ")";
        String itemB = items.get(SLOT_B).getName().getString()
                       + " (" + Registries.ITEM.getId(items.get(SLOT_B).getItem()) + ")";

        AlchemodInit.LOG.info("[Alchemod] Combining: {} + {}", itemA, itemB);

        CompletableFuture.supplyAsync(() -> queryOpenRouter(itemA, itemB))
                .thenAccept(resultId -> {
                    if (world.getServer() != null) {
                        world.getServer().execute(() -> applyResult(resultId));
                    }
                });
    }

    // ── OpenRouter HTTP call ──────────────────────────────────────────────────
    private String queryOpenRouter(String itemA, String itemB) {
        String key = AlchemodInit.OPENROUTER_KEY;
        if (key.isBlank()) {
            AlchemodInit.LOG.error("[Alchemod] OPENROUTER_API_KEY env var not set!");
            return "ERROR:no_key";
        }

        // Tight system prompt — model must reply with only a Minecraft item ID
        String system = "You are a Minecraft alchemist oracle. "
                + "Given two Minecraft items, respond with EXACTLY ONE vanilla Minecraft item ID "
                + "that thematically represents their combination. "
                + "Format: namespace:item_name — example: minecraft:blaze_rod. "
                + "No explanation. No punctuation. Just the item ID.";

        String user = "Combine: " + itemA + " + " + itemB;

        // Build JSON manually — no external dependency needed
        String body = "{"
                + "\"model\":\"openai/gpt-4o-mini\","
                + "\"max_tokens\":30,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + quoted(system) + "},"
                + "{\"role\":\"user\",\"content\":" + quoted(user) + "}"
                + "]}";

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
            AlchemodInit.LOG.info("[Alchemod] API status={} body={}", resp.statusCode(), resp.body());

            if (resp.statusCode() != 200) return "ERROR:http_" + resp.statusCode();

            return parseItemId(resp.body());

        } catch (Exception e) {
            AlchemodInit.LOG.error("[Alchemod] HTTP error", e);
            return "ERROR:" + e.getClass().getSimpleName();
        }
    }

    // Extract item ID from OpenRouter response (e.g. {"choices":[{"message":{"content":"minecraft:blaze_rod"}}]})
    private String parseItemId(String json) {
        // Grab the content field value
        Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String raw = m.group(1).trim();
            // Extract first namespace:id token
            Matcher idMatch = Pattern.compile("([a-z][a-z0-9_]*:[a-z][a-z0-9_/]*)").matcher(raw);
            if (idMatch.find()) return idMatch.group(1);
        }
        return "minecraft:nether_star"; // safe fallback
    }

    // ── Apply the result back on the server thread ────────────────────────────
    private void applyResult(String resultId) {
        aiPending = false;

        if (resultId.startsWith("ERROR:")) {
            AlchemodInit.LOG.warn("[Alchemod] Error from AI: {}", resultId);
            state = STATE_ERROR;
            progress = 0;
            markDirty();
            return;
        }

        // Validate the item ID exists in the registry
        Identifier id = Identifier.tryParse(resultId);
        Item item = id != null ? Registries.ITEM.get(id) : null;
        if (item == null || item == Items.AIR) {
            AlchemodInit.LOG.warn("[Alchemod] Unknown item '{}', falling back to nether_star", resultId);
            item = Items.NETHER_STAR;
        }

        // Consume one of each input, place output
        items.get(SLOT_A).decrement(1);
        items.get(SLOT_B).decrement(1);
        if (items.get(SLOT_A).isEmpty()) items.set(SLOT_A, ItemStack.EMPTY);
        if (items.get(SLOT_B).isEmpty()) items.set(SLOT_B, ItemStack.EMPTY);
        items.set(SLOT_OUTPUT, new ItemStack(item));

        progress = MAX_PROGRESS; // fill bar to 100%
        state = STATE_READY;
        AlchemodInit.LOG.info("[Alchemod] Output: {}", Registries.ITEM.getId(item));
        markDirty();
    }

    // Called by the output slot when the player takes the item
    public void onOutputTaken() {
        // Reset to idle so it can run again
        state = STATE_IDLE;
        progress = 0;
        markDirty();
    }

    // ── NamedScreenHandlerFactory ─────────────────────────────────────────────
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity player) {
        return new ForgeScreenHandler(syncId, playerInv, this, delegate);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.alchemod.alchemical_forge");
    }

    // ── Inventory ─────────────────────────────────────────────────────────────
    @Override public int size() { return 3; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack r = Inventories.splitStack(items, slot, amount);
        if (!r.isEmpty()) markDirty();
        return r;
    }
    @Override public ItemStack removeStack(int slot) { return Inventories.removeStack(items, slot); }
    @Override public void setStack(int slot, ItemStack stack) { items.set(slot, stack); markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) {
        return net.minecraft.inventory.Inventory.canPlayerUse(this, player);
    }
    @Override public void clear() { items.clear(); }

    // Expose for the screen handler
    public PropertyDelegate getDelegate() { return delegate; }
    public int getMaxProgress() { return MAX_PROGRESS; }

    public DefaultedList<ItemStack> getItems() { return items; }

    // ── NBT ───────────────────────────────────────────────────────────────────
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        Inventories.writeNbt(nbt, items, lookup);
        nbt.putInt("State", state);
        nbt.putInt("Progress", progress);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        state    = nbt.getInt("State");
        progress = nbt.getInt("Progress");
        // If it was mid-process when the world saved, reset so it retries
        if (state == STATE_PROCESSING) { state = STATE_IDLE; progress = 0; }
    }

    // ── Util ──────────────────────────────────────────────────────────────────
    private static String quoted(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
