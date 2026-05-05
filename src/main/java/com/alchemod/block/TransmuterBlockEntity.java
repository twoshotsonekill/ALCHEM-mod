package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.ai.OpenRouterClient;
import com.alchemod.item.AlchemicalEssenceItem;
import com.alchemod.screen.TransmuterScreenHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.concurrent.CompletableFuture;

public class TransmuterBlockEntity extends BlockEntity
        implements NamedScreenHandlerFactory, net.minecraft.inventory.Inventory {

    public static final int SLOT_INPUT    = 0;
    public static final int SLOT_OUTPUT   = 1;
    public static final int SLOT_ESSENCE  = 2;

    public static final int STATE_IDLE       = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_READY      = 2;
    public static final int STATE_ERROR      = 3;

    private static final int MAX_PROGRESS = 120;
    private static final int BASE_ESSENCE_COST = 4;

    private final DefaultedList<ItemStack> items =
            DefaultedList.ofSize(3, ItemStack.EMPTY);

    private int state    = STATE_IDLE;
    private int progress = 0;
    private boolean aiPending = false;

    private String lastOutputName = "";
    private int    essenceCost     = BASE_ESSENCE_COST;
    private double successProb     = 0.0;

    private final PropertyDelegate delegate = new PropertyDelegate() {
        @Override public int  get(int i)       { return i == 0 ? state : progress; }
        @Override public void set(int i, int v){ if (i == 0) state = v; else progress = v; }
        @Override public int  size()           { return 2; }
    };

    public TransmuterBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemodInit.TRANS_MUTER_BE_TYPE, pos, state);
    }

    public static <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : (w, p, s, be) -> ((TransmuterBlockEntity) be).serverTick(w, p);
    }

    public void serverTick(World world, BlockPos pos) {
        if (state == STATE_IDLE
                && !items.get(SLOT_INPUT).isEmpty()
                && !items.get(SLOT_ESSENCE).isEmpty()
                && items.get(SLOT_OUTPUT).isEmpty()
                && !aiPending) {
            startTransmutation(world);
        }
        if (state == STATE_PROCESSING) {
            progress = Math.min(progress + 1, MAX_PROGRESS - 1);
            markDirty();
        }
    }

    private void startTransmutation(World world) {
        ItemStack input = items.get(SLOT_INPUT);
        ItemStack essence = items.get(SLOT_ESSENCE);

        // Calculate essence cost based on input item rarity/value
        essenceCost = calculateEssenceCost(input);
        int availableEssence = essence.getCount();

        if (availableEssence < essenceCost) {
            AlchemodInit.LOG.warn("[Transmuter] Not enough essence. Need {}, have {}", essenceCost, availableEssence);
            return;
        }

        aiPending = true;
        state     = STATE_PROCESSING;
        progress  = 0;
        lastOutputName = "";
        successProb     = 0.0;
        markDirty();

        String inputItem = input.getName().getString() + " (" + Registries.ITEM.getId(input.getItem()) + ")";

        AlchemodInit.LOG.info("[Transmuter] Transmuting: {}", inputItem);

        CompletableFuture.supplyAsync(() -> queryOpenRouter(inputItem, essenceCost))
                .thenAccept(result -> {
                    if (world.getServer() != null) {
                        world.getServer().execute(() -> applyResult(result));
                    }
                });
    }

    private TransmuterResult queryOpenRouter(String inputItem, int cost) {
        String key = AlchemodInit.OPENROUTER_KEY;
        if (key.isBlank()) {
            AlchemodInit.LOG.error("[Transmuter] OPENROUTER_API_KEY not set.");
            return TransmuterResult.error("no_key");
        }

        String system = """
                You are an AI alchemist. Given an input item and essence cost,
                determine if transmutation is possible and what the result should be.
                Respond with ONLY valid JSON - no markdown fences, no explanation.

                Required field:
                  "output_item": "namespace:item_name" - a valid vanilla Minecraft item ID

                Optional fields:
                  "success_probability": <0.0-1.0> - chance of successful transmutation
                  "display_name": "Custom name for the output item"
                  "lore": "Flavor text (max 12 words)"
                  "reason": "Why this transmutation makes sense"

                Rules:
                - Output must be a valid vanilla Minecraft 1.21 item
                - Higher essence cost should yield better/more valuable items
                - Success probability should be higher with more essence
                - Common items (dirt, cobblestone) can become uncommon with low cost
                - Rare items need high essence cost (16+)
                - Legendary items need very high essence cost (32+)
                """;

        String user = "Transmute: " + inputItem + " (Essence cost: " + cost + ")";

        OpenRouterClient.ChatResult result = OpenRouterClient.chat(
                key,
                new OpenRouterClient.ChatRequest(
                        AlchemodInit.CONFIG.transmuterModel(),
                        250,
                        AlchemodInit.CONFIG.transmuterTimeoutSeconds(),
                        system,
                        user));

        if (result.isError()) {
            AlchemodInit.LOG.error("[Transmuter] API error: {}", result.error());
            return TransmuterResult.error(result.error());
        }

        return parseTransmuterResult(result.content());
    }

    private TransmuterResult parseTransmuterResult(String content) {
        try {
            String cleaned = stripCodeFence(content != null ? content.trim() : "");
            String jsonBody = extractFirstJsonObject(cleaned);
            if (jsonBody == null) {
                return TransmuterResult.fallback();
            }

            JsonObject obj = JsonParser.parseString(jsonBody).getAsJsonObject();

            String outputItem = getString(obj, "output_item", "minecraft:iron_ingot");
            double prob = obj.has("success_probability") ? obj.get("success_probability").getAsDouble() : 0.5;
            String name = getNullable(obj, "display_name");
            String lore = getNullable(obj, "lore");
            String reason = getNullable(obj, "reason");

            return new TransmuterResult(outputItem, prob, name, lore, reason, null);

        } catch (JsonSyntaxException | IllegalStateException e) {
            AlchemodInit.LOG.warn("[Transmuter] JSON parse failed: {}", e.getMessage());
            return TransmuterResult.fallback();
        }
    }

    private void applyResult(TransmuterResult result) {
        aiPending = false;

        if (result.error() != null) {
            AlchemodInit.LOG.warn("[Transmuter] Error from AI: {}", result.error());
            state    = STATE_ERROR;
            progress = 0;
            markDirty();
            return;
        }

        // Check success probability
        if (Math.random() > result.probability()) {
            AlchemodInit.LOG.info("[Transmuter] Transmutation failed (probability: {})", result.probability());
            state = STATE_ERROR;
            progress = 0;
            markDirty();
            return;
        }

        // Consume input and essence
        items.get(SLOT_INPUT).decrement(1);
        items.get(SLOT_ESSENCE).decrement(essenceCost);

        if (items.get(SLOT_INPUT).isEmpty()) items.set(SLOT_INPUT, ItemStack.EMPTY);
        if (items.get(SLOT_ESSENCE).isEmpty()) items.set(SLOT_ESSENCE, ItemStack.EMPTY);

        // Create output
        Identifier id = Identifier.tryParse(result.outputItem());
        Item item = id != null ? Registries.ITEM.get(id) : null;
        if (item == null || item == net.minecraft.item.Items.AIR) {
            item = net.minecraft.item.Items.IRON_INGOT;
        }

        ItemStack output = new ItemStack(item);
        if (result.displayName() != null && !result.displayName().isBlank()) {
            output.set(DataComponentTypes.CUSTOM_NAME, Text.literal(result.displayName()));
        }
        if (result.lore() != null && !result.lore().isBlank()) {
            output.set(DataComponentTypes.LORE,
                      new net.minecraft.component.type.LoreComponent(
                              java.util.List.of(Text.literal("§7§o" + result.lore()))));
        }

        // Store transmutation data in NBT
        NbtCompound transTag = new NbtCompound();
        transTag.putString("transmute_input", items.get(SLOT_INPUT).getName().getString());
        transTag.putInt("transmute_cost", essenceCost);
        transTag.putString("transmute_reason", result.reason() != null ? result.reason() : "");
        if (!transTag.isEmpty()) {
            NbtCompound root = new NbtCompound();
            root.put("alchemod_transmuter", transTag);
            output.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        }

        items.set(SLOT_OUTPUT, output);
        lastOutputName = result.displayName() != null ? result.displayName() : item.getName().getString();
        successProb     = result.probability();
        progress = MAX_PROGRESS;
        state    = STATE_READY;

        AlchemodInit.LOG.info("[Transmuter] Output: {} (prob={})", lastOutputName, successProb);
        markDirty();
    }

    private int calculateEssenceCost(ItemStack input) {
        // Simple heuristic: rare items need more essence
        String name = input.getName().getString().toLowerCase();
        if (name.contains("diamond") || name.contains("emerald")) return 16;
        if (name.contains("gold") || name.contains("iron")) return 8;
        if (name.contains("stone") || name.contains("cobble")) return 4;
        return BASE_ESSENCE_COST;
    }

    public String getLastOutputName() { return lastOutputName; }
    public int    getEssenceCost()     { return essenceCost; }
    public double getSuccessProbability() { return successProb; }

    @Override
    public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInv,
                                   net.minecraft.entity.player.PlayerEntity player) {
        return new TransmuterScreenHandler(syncId, playerInv, this, delegate);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.alchemod.alchemical_transmuter");
    }

    @Override public int size()  { return 3; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int toTake = Math.min(amount, stack.getCount());
        ItemStack result = stack.split(toTake);
        if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
        markDirty();
        return result;
    }
    @Override public ItemStack removeStack(int slot) { return removeStack(slot, items.get(slot).getCount()); }
    @Override public void setStack(int slot, ItemStack stack) { items.set(slot, stack); markDirty(); }
    @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return net.minecraft.inventory.Inventory.canPlayerUse(this, player); }
    @Override public void clear() { items.clear(); }

    public PropertyDelegate getDelegate()       { return delegate; }
    public int getMaxProgress()                 { return MAX_PROGRESS; }
    public DefaultedList<ItemStack> getItems() { return items; }

    public void onOutputTaken() {
        state    = STATE_IDLE;
        progress = 0;
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        Inventories.writeNbt(nbt, items, lookup);
        nbt.putInt("State",    state);
        nbt.putInt("Progress", progress);
        nbt.putString("LastOutputName", lastOutputName);
        nbt.putInt("EssenceCost",     essenceCost);
        nbt.putDouble("SuccessProb",     successProb);
    }

    @Override
    protected void readNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        state           = nbt.getInt("State");
        progress        = nbt.getInt("Progress");
        lastOutputName  = nbt.getString("LastOutputName");
        essenceCost     = nbt.getInt("EssenceCost");
        successProb     = nbt.getDouble("SuccessProb");
        if (state == STATE_PROCESSING) { state = STATE_IDLE; progress = 0; }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null)
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }

    private static String stripCodeFence(String s) {
        if (!s.startsWith("```")) return s;
        int nl   = s.indexOf('\n');
        int last = s.lastIndexOf("```");
        return (nl < 0 || last <= nl) ? s.replace("```","").trim()
                : s.substring(nl + 1, last).trim();
    }

    private static String extractFirstJsonObject(String content) {
        int start = -1, depth = 0;
        boolean inStr = false, esc = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (esc)        { esc = false; continue; }
            if (c == '\\')  { esc = true;  continue; }
            if (c == '"')   { inStr = !inStr; continue; }
            if (inStr)      continue;
            if (c == '{')   { if (depth++ == 0) start = i; }
            else if (c == '}') { if (--depth == 0 && start >= 0) return content.substring(start, i + 1); }
        }
        return null;
    }

    private static String getString(JsonObject o, String key, String fallback) {
        JsonElement el = o.get(key);
        return (el == null || el.isJsonNull()) ? fallback : el.getAsString().trim();
    }

    private static String getNullable(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull()) return null;
        String v = el.getAsString().trim();
        return v.isBlank() || "null".equalsIgnoreCase(v) ? null : v;
    }

    private record TransmuterResult(
            String outputItem, double probability, String displayName,
            String lore, String reason, String error) {
        static TransmuterResult error(String msg) {
            return new TransmuterResult("minecraft:iron_ingot", 0.0, null, null, null, msg);
        }
        static TransmuterResult fallback() {
            return new TransmuterResult("minecraft:iron_ingot", 0.5,
                                    "Transmuted Iron", "A basic transmutation", null, null);
        }
    }
}
