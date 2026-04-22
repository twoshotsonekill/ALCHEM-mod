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

    // ── Allowed values ────────────────────────────────────────────────────────

    private static final Set<String> VALID_EFFECTS = Set.of(
            "speed", "strength", "regeneration", "resistance",
            "fire_resistance", "night_vision", "absorption", "luck",
            "haste", "jump_boost", "slow_falling", "water_breathing");

    private static final Set<String> VALID_SPECIALS = Set.of(
            "ignite", "knockback", "heal_aura", "launch",
            "freeze", "drain", "phase", "lightning", "void_step");

    private static final Set<String> VALID_RARITIES = Set.of(
            "common", "uncommon", "rare", "epic", "legendary");

    private static final Set<String> VALID_ITEM_TYPES = Set.of(
            "use_item", "bow", "spawn_egg", "food", "sword", "totem", "throwable");

    /** Mob IDs that can be safely spawned and modified at runtime. */
    private static final Set<String> VALID_MOBS = Set.of(
            "zombie", "skeleton", "creeper", "spider", "enderman", "blaze", "witch",
            "phantom", "slime", "magma_cube", "hoglin", "strider",
            "cow", "pig", "sheep", "chicken", "bat", "wolf",
            "rabbit", "fox", "bee", "axolotl", "parrot");

    // ── State ─────────────────────────────────────────────────────────────────

    private final DefaultedList<ItemStack> items =
            DefaultedList.ofSize(3, ItemStack.EMPTY);

    private int     state           = STATE_IDLE;
    private int     progress        = 0;
    private boolean aiPending       = false;
    private int     lastCreatedSlot = -1;

    private final PropertyDelegate delegate = new PropertyDelegate() {
        @Override public int get(int i) {
            return switch (i) { case 0 -> state; case 1 -> progress; case 2 -> lastCreatedSlot; default -> 0; };
        }
        @Override public void set(int i, int v) {
            switch (i) { case 0 -> state = v; case 1 -> progress = v; case 2 -> lastCreatedSlot = v; }
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
        state     = STATE_PROCESSING;
        progress  = 0;
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
        if (key.isBlank()) return CreationResult.error("OPENROUTER_API_KEY not set");

        String system = """
You are a creative Minecraft alchemist inventing wildly imaginative NEW items.
Given two input items, invent a magical synthesis that could be any kind of item.

=== ITEM TYPES ===
Choose item_type based purely on what the combination SUGGESTS. Be creative!

  use_item   – instant right-click ability (potions, wands, orbs)
  bow        – hold to charge, fire a magical projectile
               → Use when inputs include: bow, arrow, crossbow, string, feather, blaze rod
  spawn_egg  – summons a magical creature when right-clicked on ground
               → Use when inputs include: any spawn egg, mob drops (bone, slimeball, spider eye,
                 feather, leather, wool), monster-related items, seeds/crops + mob items
  food       – hold right-click to eat and gain powerful effects
               → Use when inputs include: any food, apple, bread, melon, carrot, fish, cake
  sword      – right-click to sweep nearby enemies with magical damage
               → Use when inputs include: sword, axe, trident, combat items
  totem      – hold in off-hand for persistent passive effects on you and nearby allies
               → Use when inputs include: totem, shield, armor, pearl, beacon, conduit
  throwable  – throw at a target point for area explosion/effects
               → Use when inputs include: snowball, egg, ender pearl, fireball, bomb-like items

=== RARITY GUIDE ===
  common    – mundane pairing, one weak effect
  uncommon  – mildly interesting, two effects
  rare      – creative/thematic match, good effects + special ability
  epic      – powerful/lore-rich combination, multiple effects + strong special
  legendary – world-altering or iconic combination, maximum effects + overpowered special

=== VALID EFFECTS (use exact strings) ===
speed, strength, regeneration, resistance, fire_resistance, night_vision,
absorption, luck, haste, jump_boost, slow_falling, water_breathing

Effects count by rarity: common=1, uncommon=2, rare=2, epic=3, legendary=4

=== VALID SPECIALS ===
ignite, knockback, heal_aura, launch, freeze, drain, phase, lightning, void_step
Required for rare/epic/legendary. Set to null for common. Optional for uncommon.

=== VALID MOBS (spawn_egg only) ===
zombie, skeleton, creeper, spider, enderman, blaze, witch,
phantom, slime, magma_cube, bat, cow, pig, sheep, chicken,
wolf, rabbit, fox, bee, axolotl, parrot

=== OUTPUT FORMAT ===
Respond with ONLY valid JSON, no markdown, no explanation:
{"name":"Item Name","description":"One sentence flavour.","sprite_prompt":"pixel art description 8-15 words","rarity":"rare","item_type":"bow","effects":["strength","haste"],"special":"ignite","mob_type":null}

mob_type: for spawn_egg ONLY — a string from the mob list above. null for everything else.

CREATIVE EXAMPLES:
- Bow + Blaze Rod → {"name":"Inferno Bow","item_type":"bow","special":"ignite","rarity":"rare",...}
- Melon + Phantom Membrane → {"name":"Flying Melon Egg","item_type":"spawn_egg","mob_type":"phantom","special":"launch","rarity":"uncommon",...}
- Golden Apple + Nether Star → {"name":"Divine Fruit","item_type":"food","special":"heal_aura","rarity":"legendary",...}
- Ender Pearl + Snowball → {"name":"Freeze Pearl","item_type":"throwable","special":"freeze","rarity":"rare",...}
- Diamond Sword + Fire Charge → {"name":"Blazing Blade","item_type":"sword","special":"ignite","rarity":"epic",...}
- Totem of Undying + Emerald → {"name":"Eternal Emerald","item_type":"totem","special":"heal_aura","rarity":"epic",...}
""";

        String user = "Create a new item by combining: " + itemA + " + " + itemB;

        String body = "{"
                + "\"model\":\"openai/gpt-4o-mini\","
                + "\"max_tokens\":220,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + quoted(system) + "},"
                + "{\"role\":\"user\",\"content\":" + quoted(user) + "}"
                + "]}";

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
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

            if (resp.statusCode() != 200) return CreationResult.error("HTTP " + resp.statusCode());

            return parseCreationResult(resp.body());

        } catch (Exception e) {
            AlchemodInit.LOG.error("[Creator] API error", e);
            return CreationResult.error(e.getMessage());
        }
    }

    // ── Parse AI JSON response ────────────────────────────────────────────────

    private CreationResult parseCreationResult(String json) {
        // Unwrap OpenRouter outer envelope
        Pattern contentPat = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher cm = contentPat.matcher(json);
        String content = cm.find()
                ? cm.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\/", "/")
                : json;

        String       name     = extractStr(content, "name");
        String       desc     = extractStr(content, "description");
        String       sprite   = extractStr(content, "sprite_prompt");
        String       rarity   = extractStr(content, "rarity");
        String       itemType = extractStr(content, "item_type");
        List<String> effects  = extractArray(content, "effects");
        String       special  = extractStrNullable(content, "special");
        String       mobType  = extractStrNullable(content, "mob_type");

        // ── Defaults ─────────────────────────────────────────────────────────

        if (isBlank(name))     name     = "Mysterious Relic";
        if (isBlank(desc))     desc     = "An enigmatic object of unknown power.";
        if (isBlank(sprite))   sprite   = name + " magical glowing artifact";

        rarity   = VALID_RARITIES.contains(normalise(rarity))   ? normalise(rarity)   : "common";
        itemType = VALID_ITEM_TYPES.contains(normalise(itemType)) ? normalise(itemType) : "use_item";

        // Validate effects
        List<String> cleanEffects = effects.stream()
                .map(this::normalise)
                .filter(VALID_EFFECTS::contains)
                .toList();
        if (cleanEffects.isEmpty()) cleanEffects = List.of("luck");

        // Cap effects to rarity max
        int maxEff = switch (rarity) { case "uncommon", "rare" -> 2; case "epic" -> 3; case "legendary" -> 4; default -> 1; };
        if (cleanEffects.size() > maxEff) cleanEffects = cleanEffects.subList(0, maxEff);

        // Validate special
        if (special != null) {
            special = normalise(special);
            if (!VALID_SPECIALS.contains(special)) special = null;
        }
        // Guarantee rare+ have a special
        if (special == null && Set.of("rare", "epic", "legendary").contains(rarity)) {
            special = fallbackSpecial(cleanEffects.get(0));
        }

        // Validate mob_type — only meaningful for spawn_egg
        if (!"spawn_egg".equals(itemType)) {
            mobType = null;
        } else {
            if (mobType != null) mobType = normalise(mobType);
            if (!VALID_MOBS.contains(mobType)) mobType = "bat"; // safe default
        }

        AlchemodInit.LOG.info("[Creator] Parsed — '{}' type={} rarity={} effects={} special={} mob={}",
                name, itemType, rarity, cleanEffects, special, mobType);

        return new CreationResult(name, desc, sprite, rarity, itemType, cleanEffects, special, mobType, null);
    }

    // ── Apply result on server thread ─────────────────────────────────────────

    private void applyResult(CreationResult r) {
        aiPending = false;

        if (r.error() != null) {
            AlchemodInit.LOG.warn("[Creator] Error: {}", r.error());
            state = STATE_ERROR; progress = 0; markDirty(); return;
        }

        DynamicItem dynItem = DynamicItemRegistry.claimSlot();
        if (dynItem == null) {
            AlchemodInit.LOG.warn("[Creator] No dynamic slots left!");
            state = STATE_ERROR; markDirty(); return;
        }

        int slot = dynItem.getSlotIndex();
        DynamicItemRegistry.CreatedItemMeta meta =
                new DynamicItemRegistry.CreatedItemMeta(
                        r.name(), r.description(), slot, r.rarity(),
                        r.itemType(), r.effects(), r.special(), r.mobType());
        DynamicItemRegistry.updateSlotMeta(slot, meta);

        // Consume inputs
        items.get(SLOT_A).decrement(1); items.get(SLOT_B).decrement(1);
        if (items.get(SLOT_A).isEmpty()) items.set(SLOT_A, ItemStack.EMPTY);
        if (items.get(SLOT_B).isEmpty()) items.set(SLOT_B, ItemStack.EMPTY);

// Store input item IDs for texture generation (before consuming)
        String inputA = Registries.ITEM.getId(items.get(SLOT_A).getItem()).toString();
        String inputB = Registries.ITEM.getId(items.get(SLOT_B).getItem()).toString();

        // Consume inputs
        items.get(SLOT_A).decrement(1); items.get(SLOT_B).decrement(1);
        if (items.get(SLOT_A).isEmpty()) items.set(SLOT_A, ItemStack.EMPTY);
        if (items.get(SLOT_B).isEmpty()) items.set(SLOT_B, ItemStack.EMPTY);

        // Build output stack with full NBT
        ItemStack out = new ItemStack(dynItem);
        NbtCompound tag = new NbtCompound();
        tag.putString("creator_name",      r.name());
        tag.putString("creator_desc",      r.description());
        tag.putString("creator_sprite",    r.spritePrompt());
        tag.putString("creator_rarity",    r.rarity());
        tag.putString("creator_item_type", r.itemType());
        tag.putString("creator_effects",   String.join(",", r.effects()));
        tag.putString("creator_special",   r.special() != null ? r.special() : "");
        tag.putString("creator_mob_type",  r.mobType() != null ? r.mobType() : "");
        tag.putInt   ("creator_slot",      slot);
        tag.putInt   ("charges",           meta.startingCharges());
        tag.putString("creator_input_a",   inputA);
        tag.putString("creator_input_b",   inputB);
        out.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(tag));

        items.set(SLOT_OUTPUT, out);
        lastCreatedSlot = slot;
        progress = MAX_PROGRESS;
        state    = STATE_READY;
        AlchemodInit.LOG.info("[Creator] Created '{}' (type={} rarity={}) slot {}",
                r.name(), r.itemType(), r.rarity(), slot);
        markDirty();
    }

    public void onOutputTaken() { state = STATE_IDLE; progress = 0; markDirty(); }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private String extractStr(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractStrNullable(String json, String key) {
        if (Pattern.compile("\"" + key + "\"\\s*:\\s*null").matcher(json).find()) return null;
        return extractStr(json, key);
    }

    private List<String> extractArray(String json, String key) {
        List<String> result = new ArrayList<>();
        try {
            Matcher am = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*?)]").matcher(json);
            if (!am.find()) return result;
            Matcher im = Pattern.compile("\"([^\"]+)\"").matcher(am.group(1));
            while (im.find()) result.add(im.group(1).trim());
        } catch (Exception e) {
            AlchemodInit.LOG.warn("[Creator] Array parse error for '{}': {}", key, e.getMessage());
        }
        return result;
    }

    private String normalise(String s) {
        return s == null ? "" : s.toLowerCase().replace("minecraft:", "").trim();
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String fallbackSpecial(String effect) {
        return switch (effect) {
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

    // ── Accessors ─────────────────────────────────────────────────────────────

    public PropertyDelegate getDelegate()            { return delegate; }
    public int getMaxProgress()                      { return MAX_PROGRESS; }
    public DefaultedList<ItemStack> getItems()       { return items; }

    // ── NamedScreenHandlerFactory ─────────────────────────────────────────────

    @Override public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new CreatorScreenHandler(syncId, inv, this, delegate);
    }
    @Override public Text getDisplayName() {
        return Text.translatable("block.alchemod.item_creator");
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    @Override public int size()       { return 3; }
    @Override public boolean isEmpty(){ return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot)           { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack r = Inventories.splitStack(items, slot, amount);
        if (!r.isEmpty()) markDirty(); return r;
    }
    @Override public ItemStack removeStack(int slot)        { return Inventories.removeStack(items, slot); }
    @Override public void setStack(int slot, ItemStack stack){ items.set(slot, stack); markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity p)   { return net.minecraft.inventory.Inventory.canPlayerUse(this, p); }
    @Override public void clear()                           { items.clear(); }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        Inventories.writeNbt(nbt, items, lookup);
        nbt.putInt("State", state); nbt.putInt("Progress", progress); nbt.putInt("LastSlot", lastCreatedSlot);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        state = nbt.getInt("State"); progress = nbt.getInt("Progress"); lastCreatedSlot = nbt.getInt("LastSlot");
        if (state == STATE_PROCESSING) { state = STATE_IDLE; progress = 0; }
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private static String quoted(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","") + "\"";
    }

    // ── Result record ─────────────────────────────────────────────────────────

    record CreationResult(
            String name, String description, String spritePrompt,
            String rarity, String itemType, List<String> effects,
            String special, String mobType, String error
    ) {
        static CreationResult error(String msg) {
            return new CreationResult(null,null,null,null,"use_item",List.of(),null,null,msg);
        }
    }
}
