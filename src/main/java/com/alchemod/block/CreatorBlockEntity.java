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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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

    // Valid effect IDs the AI may assign
    private static final Set<String> VALID_EFFECTS = Set.of(
            "speed", "strength", "regeneration", "resistance",
            "fire_resistance", "night_vision", "absorption", "luck",
            "haste", "jump_boost", "slow_falling", "water_breathing");

    // Valid special ability IDs
    private static final Set<String> VALID_SPECIALS = Set.of(
            "ignite", "knockback", "heal_aura", "launch",
            "freeze", "drain", "phase", "lightning", "void_step");

    // Valid rarity values
    private static final Set<String> VALID_RARITIES = Set.of(
            "common", "uncommon", "rare", "epic", "legendary");

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

        AlchemodInit.LOG.info("[Creator] Inventing from: {} + {}", itemA, itemB);

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
You are an alchemist inventing entirely NEW magical items that don't exist in Minecraft.
Given two input items, invent a new magical item that is a synthesis of both.

The rarity reflects how thematically interesting or powerful the combination is:
- common: mundane pairings (stone + dirt, two similar items)
- uncommon: mildly interesting pairings (wood + iron)
- rare: interesting thematic match (fire + water, opposites or lore-rich items)
- epic: creative or powerful pairings (dragon egg + beacon, legendary items)
- legendary: extraordinary universe-altering combinations (end crystal + nether star, etc.)

Respond with ONLY valid JSON, no markdown, no explanation:
{"name":"Item Name","description":"One sentence flavour.","sprite_prompt":"pixel art description","rarity":"rare","effects":["strength","haste"],"special":"knockback"}

STRICT rules:
- name: 2-4 words, title case, creative and magical
- description: max 20 words
- sprite_prompt: 8-15 words describing visual appearance for 16x16 pixel art
- rarity: exactly ONE of: common, uncommon, rare, epic, legendary
- effects: JSON array of strings from this EXACT list (case-sensitive):
    speed, strength, regeneration, resistance, fire_resistance, night_vision,
    absorption, luck, haste, jump_boost, slow_falling, water_breathing
  Array length by rarity: common=1, uncommon=2, rare=2, epic=3, legendary=4
- special: exactly ONE of the following strings, or null for common and optionally uncommon:
    ignite, knockback, heal_aura, launch, freeze, drain, phase, lightning, void_step
  Required for rare, epic, legendary. Use null (not the string "null") for common.
""";

        String user = "Create a new item by combining: " + itemA + " + " + itemB;

        String body = "{"
                + "\"model\":\"openai/gpt-4o-mini\","
                + "\"max_tokens\":200,"
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
        // Unwrap OpenRouter's outer JSON to get the model's content string
        Pattern contentPat = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher cm = contentPat.matcher(json);
        String content = cm.find()
                ? cm.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\/", "/")
                : json;

        String       name    = extractJsonString(content, "name");
        String       desc    = extractJsonString(content, "description");
        String       sprite  = extractJsonString(content, "sprite_prompt");
        String       rarity  = extractJsonString(content, "rarity");
        List<String> effects = extractJsonArray (content, "effects");
        String       special = extractJsonStringNullable(content, "special");

        // ── Sanitise / fallback ───────────────────────────────────────────────

        if (name == null   || name.isBlank())   name   = "Mysterious Relic";
        if (desc == null   || desc.isBlank())   desc   = "An enigmatic object of unknown power.";
        if (sprite == null || sprite.isBlank()) sprite = name + " magical glowing artifact";

        // Rarity: default common if unrecognised
        rarity = (rarity != null && VALID_RARITIES.contains(rarity.toLowerCase()))
                ? rarity.toLowerCase() : "common";

        // Filter effects to only known IDs; guarantee at least one
        List<String> cleanEffects = new ArrayList<>();
        for (String e : effects) {
            String norm = e.toLowerCase().replace("minecraft:", "").trim();
            if (VALID_EFFECTS.contains(norm)) cleanEffects.add(norm);
        }
        if (cleanEffects.isEmpty()) cleanEffects.add("luck");

        // Cap effect count to rarity maximum (just in case the model over-generates)
        int maxEffects = switch (rarity) {
            case "uncommon"  -> 2;
            case "rare"      -> 2;
            case "epic"      -> 3;
            case "legendary" -> 4;
            default          -> 1;
        };
        if (cleanEffects.size() > maxEffects) {
            cleanEffects = cleanEffects.subList(0, maxEffects);
        }

        // Special: validate or nullify
        if (special != null) {
            special = special.toLowerCase().replace("minecraft:", "").trim();
            if (!VALID_SPECIALS.contains(special)) special = null;
        }
        // Ensure rare+ always have a special; assign one based on primary effect if missing
        if (special == null && (rarity.equals("rare") || rarity.equals("epic") || rarity.equals("legendary"))) {
            special = fallbackSpecial(cleanEffects.get(0));
        }

        AlchemodInit.LOG.info("[Creator] Parsed — name='{}' rarity='{}' effects={} special='{}'",
                name, rarity, cleanEffects, special);

        return new CreationResult(name, desc, sprite, rarity, cleanEffects, special, null);
    }

    /** Regex-extract a JSON string value, returning null if absent. */
    private String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Like {@link #extractJsonString} but also handles a bare {@code null} value,
     * returning Java {@code null} in that case.
     */
    private String extractJsonStringNullable(String json, String key) {
        // Check for JSON null first
        Pattern nullP = Pattern.compile("\"" + key + "\"\\s*:\\s*null");
        if (nullP.matcher(json).find()) return null;
        return extractJsonString(json, key);
    }

    /** Regex-extract a JSON array of strings. */
    private List<String> extractJsonArray(String json, String key) {
        List<String> result = new ArrayList<>();
        try {
            Pattern ap = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*?)\\]");
            Matcher am = ap.matcher(json);
            if (!am.find()) return result;
            Pattern ip = Pattern.compile("\"([^\"]+)\"");
            Matcher im = ip.matcher(am.group(1));
            while (im.find()) result.add(im.group(1).trim());
        } catch (Exception e) {
            AlchemodInit.LOG.warn("[Creator] Array parse error for key '{}': {}", key, e.getMessage());
        }
        return result;
    }

    /** Pick a sensible special ability when the AI omitted one for a rare+ item. */
    private static String fallbackSpecial(String primaryEffect) {
        return switch (primaryEffect) {
            case "strength"        -> "knockback";
            case "speed"           -> "void_step";
            case "regeneration"    -> "heal_aura";
            case "jump_boost"      -> "launch";
            case "fire_resistance" -> "ignite";
            case "night_vision"    -> "phase";
            case "water_breathing" -> "freeze";
            case "haste"           -> "lightning";
            default                -> "drain";
        };
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

        DynamicItemRegistry.CreatedItemMeta meta =
                new DynamicItemRegistry.CreatedItemMeta(
                        result.name(),
                        result.description(),
                        slot,
                        result.rarity(),
                        result.effects(),
                        result.special());

        DynamicItemRegistry.updateSlotMeta(slot, meta);

        // Consume inputs
        items.get(SLOT_A).decrement(1);
        items.get(SLOT_B).decrement(1);
        if (items.get(SLOT_A).isEmpty()) items.set(SLOT_A, ItemStack.EMPTY);
        if (items.get(SLOT_B).isEmpty()) items.set(SLOT_B, ItemStack.EMPTY);

        ItemStack outStack = new ItemStack(dynItem);

        // Persist all meta into CUSTOM_DATA so the client can read it from the
        // item's NBT even before DynamicItemRegistry is populated client-side
        NbtCompound tag = new NbtCompound();
        tag.putString("creator_name",    result.name());
        tag.putString("creator_desc",    result.description());
        tag.putString("creator_sprite",  result.spritePrompt());
        tag.putString("creator_rarity",  result.rarity());
        // Effects stored as comma-separated for easy re-parsing
        tag.putString("creator_effects", String.join(",", result.effects()));
        tag.putString("creator_special", result.special() != null ? result.special() : "");
        tag.putInt   ("creator_slot",    slot);
        // Initialise charges based on rarity
        tag.putInt   ("charges",         meta.startingCharges());
        outStack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));

        items.set(SLOT_OUTPUT, outStack);
        lastCreatedSlot = slot;
        progress = MAX_PROGRESS;
        state = STATE_READY;

        AlchemodInit.LOG.info("[Creator] Created '{}' (rarity={} effects={} special={}) in slot {}",
                result.name(), result.rarity(), result.effects(), result.special(), slot);
        markDirty();
    }

    public void onOutputTaken() {
        state = STATE_IDLE;
        progress = 0;
        markDirty();
    }

    public PropertyDelegate getDelegate()  { return delegate; }
    public int getMaxProgress()            { return MAX_PROGRESS; }
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

    @Override public int size()   { return 3; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot)            { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack r = Inventories.splitStack(items, slot, amount);
        if (!r.isEmpty()) markDirty();
        return r;
    }
    @Override public ItemStack removeStack(int slot)         { return Inventories.removeStack(items, slot); }
    @Override public void setStack(int slot, ItemStack stack){ items.set(slot, stack); markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) {
        return net.minecraft.inventory.Inventory.canPlayerUse(this, player);
    }
    @Override public void clear() { items.clear(); }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        Inventories.writeNbt(nbt, items, lookup);
        nbt.putInt("State",    state);
        nbt.putInt("Progress", progress);
        nbt.putInt("LastSlot", lastCreatedSlot);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        state           = nbt.getInt("State");
        progress        = nbt.getInt("Progress");
        lastCreatedSlot = nbt.getInt("LastSlot");
        if (state == STATE_PROCESSING) { state = STATE_IDLE; progress = 0; }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private static String quoted(String s) {
        return "\"" + s.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "") + "\"";
    }

    // ── CreationResult ────────────────────────────────────────────────────────

    record CreationResult(
            String       name,
            String       description,
            String       spritePrompt,
            String       rarity,
            List<String> effects,
            String       special,
            String       error
    ) {
        static CreationResult error(String msg) {
            return new CreationResult(null, null, null, null, List.of(), null, msg);
        }
    }
}
