package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.creator.DynamicItem;
import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.screen.CreatorScreenHandler;
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

public class CreatorBlockEntity extends BlockEntity
        implements NamedScreenHandlerFactory, Inventory {

    public static final int SLOT_A      = 0;
    public static final int SLOT_B      = 1;
    public static final int SLOT_OUTPUT = 2;

    public static final int STATE_IDLE       = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_READY      = 2;
    public static final int STATE_ERROR      = 3;

    private static final int MAX_PROGRESS = 100;

    private final DefaultedList<ItemStack> items =
            DefaultedList.ofSize(3, ItemStack.EMPTY);

    private int state    = STATE_IDLE;
    private int progress = 0;
    private boolean aiPending = false;
    private int lastCreatedSlot = -1;

    private final PropertyDelegate delegate = new PropertyDelegate() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> state;
                case 1 -> progress;
                case 2 -> lastCreatedSlot;
                default -> 0;
            };
        }
        @Override public void set(int i, int v) {
            switch (i) {
                case 0 -> state = v;
                case 1 -> progress = v;
                case 2 -> lastCreatedSlot = v;
            }
        }
        @Override public int size() { return 3; }
    };

    public CreatorBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemodInit.CREATOR_BE_TYPE, pos, state);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public void serverTick(World world, BlockPos pos) {
        if (state == STATE_IDLE
                && !items.get(SLOT_A).isEmpty()
                && !items.get(SLOT_B).isEmpty()
                && items.get(SLOT_OUTPUT).isEmpty()
                && !aiPending) {
            startCreation(world);
        }

        if (state == STATE_PROCESSING) {
            progress = Math.min(progress + 1, MAX_PROGRESS - 1);
            markDirty();
        }
    }

    // ── Start AI creation ─────────────────────────────────────────────────────

    private void startCreation(World world) {
        aiPending = true;
        state = STATE_PROCESSING;
        progress = 0;
        markDirty();

        String itemA = items.get(SLOT_A).getName().getString()
                + " (" + Registries.ITEM.getId(items.get(SLOT_A).getItem()) + ")";
        String itemB = items.get(SLOT_B).getName().getString()
                + " (" + Registries.ITEM.getId(items.get(SLOT_B).getItem()) + ")";

        AlchemodInit.LOG.info("[Creator] Inventing new item from: {} + {}", itemA, itemB);

        CompletableFuture.supplyAsync(() -> queryOpenRouter(itemA, itemB))
                .thenAccept(result -> {
                    if (world.getServer() != null) {
                        world.getServer().execute(() -> applyResult(result));
                    }
                });
    }

    // ── OpenRouter call ───────────────────────────────────────────────────────

    private CreationResult queryOpenRouter(String itemA, String itemB) {
        String key = AlchemodInit.OPENROUTER_KEY;
        if (key.isBlank()) {
            return CreationResult.error("OPENROUTER_API_KEY not set");
        }

        String system = """
                You are an alchemist inventing entirely NEW magical Minecraft items that don't exist yet.
                Given two input items, invent a new item that feels like a magical synthesis of both.
                
                Respond with ONLY valid JSON in this exact format (no markdown, no explanation):
                {"name":"Item Name","description":"One sentence flavour text.","sprite_prompt":"pixel art description","power":"speed"}
                
                Rules:
                - name: 2-4 words, title case, creative and magical-sounding
                - description: max 20 words, first-person perspective as if the item speaks
                - sprite_prompt: 8-15 words describing visual appearance for a 16x16 pixel art sprite
                - power: EXACTLY one of these strings (choose the one that best fits the item's theme):
                  speed, strength, regeneration, resistance, fire_resistance, night_vision,
                  absorption, luck, haste, jump_boost, slow_falling, water_breathing
                """;

        String user = "Create a new item by combining: " + itemA + " + " + itemB;

        String body = "{"
                + "\"model\":\"openai/gpt-4o-mini\","
                + "\"max_tokens\":150,"
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
                    .timeout(Duration.ofSeconds(25))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            AlchemodInit.LOG.info("[Creator] API response: {}", resp.body());

            if (resp.statusCode() != 200)
                return CreationResult.error("HTTP " + resp.statusCode());

            return parseCreationResult(resp.body());

        } catch (Exception e) {
            AlchemodInit.LOG.error("[Creator] API error", e);
            return CreationResult.error(e.getMessage());
        }
    }

    // ── Parse AI JSON response ────────────────────────────────────────────────

    private CreationResult parseCreationResult(String json) {
        // Pull the content field from OpenRouter's outer JSON wrapper
        Pattern contentPat = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher cm = contentPat.matcher(json);
        String content = cm.find()
                ? cm.group(1).replace("\\n", "\n").replace("\\\"", "\"")
                : json;

        String name   = extractJson(content, "name");
        String desc   = extractJson(content, "description");
        String sprite = extractJson(content, "sprite_prompt");
        String power  = extractJson(content, "power");

        if (name == null || name.isBlank())   name   = "Mysterious Relic";
        if (desc == null || desc.isBlank())   desc   = "An enigmatic object of unknown power.";
        if (sprite == null || sprite.isBlank()) sprite = name + " magical glowing artifact";
        if (power == null || power.isBlank()) power  = "luck";   // safe fallback

        AlchemodInit.LOG.info("[Creator] Parsed — name='{}' power='{}' sprite='{}'", name, power, sprite);
        return new CreationResult(name, desc, sprite, power, null);
    }

    private String extractJson(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1).trim() : null;
    }

    // ── Apply result on server thread ─────────────────────────────────────────

    private void applyResult(CreationResult result) {
        aiPending = false;

        if (result.error() != null) {
            AlchemodInit.LOG.warn("[Creator] Error: {}", result.error());
            state = STATE_ERROR;
            progress = 0;
            markDirty();
            return;
        }

        DynamicItem dynItem = DynamicItemRegistry.claimSlot();
        if (dynItem == null) {
            AlchemodInit.LOG.warn("[Creator] No dynamic slots left!");
            state = STATE_ERROR;
            markDirty();
            return;
        }

        int slot = dynItem.getSlotIndex();

        // Build the full metadata record and push it to both the registry and the item
        DynamicItemRegistry.CreatedItemMeta finalMeta =
                new DynamicItemRegistry.CreatedItemMeta(
                        result.name(), result.description(), slot, result.power());
        DynamicItemRegistry.updateSlotMeta(slot, finalMeta);

        // Consume inputs, place output
        items.get(SLOT_A).decrement(1);
        items.get(SLOT_B).decrement(1);
        if (items.get(SLOT_A).isEmpty()) items.set(SLOT_A, ItemStack.EMPTY);
        if (items.get(SLOT_B).isEmpty()) items.set(SLOT_B, ItemStack.EMPTY);
        items.set(SLOT_OUTPUT, new ItemStack(dynItem));

        lastCreatedSlot = slot;
        progress = MAX_PROGRESS;
        state = STATE_READY;

        AlchemodInit.LOG.info("[Creator] Created '{}' (power={}) in slot {}",
                result.name(), result.power(), slot);

        // Store sprite prompt + power in item NBT so the client can download the sprite
        // and so the data survives pick-up / world reload
        ItemStack outStack = items.get(SLOT_OUTPUT);
        NbtCompound tag = new NbtCompound();
        tag.putString("creator_name",   result.name());
        tag.putString("creator_desc",   result.description());
        tag.putString("creator_sprite", result.spritePrompt());
        tag.putString("creator_power",  result.power());
        tag.putInt("creator_slot",      slot);
        outStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));

        markDirty();
    }

    public void onOutputTaken() {
        state = STATE_IDLE;
        progress = 0;
        markDirty();
    }

    public PropertyDelegate getDelegate() { return delegate; }
    public int getMaxProgress() { return MAX_PROGRESS; }
    public DefaultedList<ItemStack> getItems() { return items; }

    // ── NamedScreenHandlerFactory ─────────────────────────────────────────────

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity player) {
        return new CreatorScreenHandler(syncId, playerInv, this, delegate);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.alchemod.item_creator");
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

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        Inventories.writeNbt(nbt, items, lookup);
        nbt.putInt("State", state);
        nbt.putInt("Progress", progress);
        nbt.putInt("LastSlot", lastCreatedSlot);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        state = nbt.getInt("State");
        progress = nbt.getInt("Progress");
        lastCreatedSlot = nbt.getInt("LastSlot");
        if (state == STATE_PROCESSING) { state = STATE_IDLE; progress = 0; }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private static String quoted(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }

    /**
     * Carries all fields from the AI response.
     * Previously only held (name, spritePrompt, error) — description was extracted
     * from the AI JSON but then silently dropped. Now all four fields are kept.
     */
    record CreationResult(String name, String description, String spritePrompt,
                          String power, String error) {
        static CreationResult error(String msg) {
            return new CreationResult(null, null, null, null, msg);
        }
    }
}
