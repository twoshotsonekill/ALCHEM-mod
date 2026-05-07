package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.ai.OpenRouterClient;
import com.alchemod.screen.ForgeScreenHandler;
import com.alchemod.util.JsonParsingUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
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
    private static final int MAX_PROGRESS = 80;

    private final DefaultedList<ItemStack> items =
            DefaultedList.ofSize(3, ItemStack.EMPTY);

    private int state    = STATE_IDLE;
    private int progress = 0;
    private boolean aiPending = false;

    // Last result info for GUI display
    private String lastResultName  = "";
    private String lastResultRarity = "";

    // Manual NBT overrides (kept for power-user payload from ForgeNbtPayload)
    private String customName         = "";
    private String customLore         = "";
    private int    customColor        = -1;
    private List<String> customEnchantments = new ArrayList<>();
    private int hideFlags = 0;

    private final PropertyDelegate delegate = new PropertyDelegate() {
        @Override public int  get(int i)       { return i == 0 ? state : progress; }
        @Override public void set(int i, int v){ if (i == 0) state = v; else progress = v; }
        @Override public int  size()           { return 2; }
    };

    public ForgeBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemodInit.FORGE_BE_TYPE, pos, state);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public void serverTick(World world, BlockPos pos) {
        if (state == STATE_IDLE
                && !items.get(SLOT_A).isEmpty()
                && !items.get(SLOT_B).isEmpty()
                && items.get(SLOT_OUTPUT).isEmpty()
                && !aiPending) {
            startCombination(world);
        }
        if (state == STATE_PROCESSING) {
            progress = Math.min(progress + 1, MAX_PROGRESS - 1);
            markDirty();
        }
    }

    // ── Kick off AI request ───────────────────────────────────────────────────

    private void startCombination(World world) {
        aiPending = true;
        state     = STATE_PROCESSING;
        progress  = 0;
        lastResultName   = "";
        lastResultRarity = "";
        markDirty();

        String itemA = items.get(SLOT_A).getName().getString()
                + " (" + Registries.ITEM.getId(items.get(SLOT_A).getItem()) + ")";
        String itemB = items.get(SLOT_B).getName().getString()
                + " (" + Registries.ITEM.getId(items.get(SLOT_B).getItem()) + ")";

        AlchemodInit.LOG.info("[Forge] Combining: {} + {}", itemA, itemB);

        CompletableFuture.supplyAsync(() -> queryOpenRouter(itemA, itemB))
                .thenAccept(result -> {
                    if (world.getServer() != null) {
                        world.getServer().execute(() -> applyResult(result));
                    }
                });
    }

    // ── AI request — returns JSON with item identity + optional enrichment ────

    private ForgeResult queryOpenRouter(String itemA, String itemB) {
        String key = AlchemodInit.OPENROUTER_KEY;
        if (key.isBlank()) {
            AlchemodInit.LOG.error("[Forge] OPENROUTER_API_KEY not set.");
            return ForgeResult.error("no_key");
        }

        String system = """
You are a Minecraft alchemist oracle. Given two input items, you transmute them into a result.
Respond with ONLY valid JSON — no markdown fences, no explanation.

Required field:
  "item_id": "namespace:item_name"   — a valid vanilla Minecraft item ID

Optional enrichment fields (omit any you don't want):
  "name":   "Custom display name"
  "lore":   "One atmospheric flavour line"
  "rarity": "common" | "uncommon" | "rare" | "epic" | "legendary"
  "enchantments": [{"id":"minecraft:sharpness","level":2}, ...]

Rules:
- Always include item_id. It must exist in vanilla Minecraft 1.21.
- Add enrichment only when it genuinely fits the combination's theme.
- Common, mundane results (sticks from wood + wood) need no enrichment.
- Rare, magical, or thematically interesting combos deserve a custom name,
  lore, and possibly enchantments.
- Enchantments must be valid for the output item type (no Sharpness on bows, etc.).
- Max 2 enchantments. Max level: whatever vanilla allows (e.g. Sharpness V).
- Rarity tiers: common (no colour), uncommon (green), rare (aqua), epic (purple), legendary (gold).
- Keep names evocative and short (≤4 words). Keep lore atmospheric (≤12 words).
""";

        String user = "Transmute: " + itemA + " + " + itemB;

        OpenRouterClient.ChatResult result = OpenRouterClient.chat(
                key,
                new OpenRouterClient.ChatRequest(
                        AlchemodInit.CONFIG.forgeModel(),
                        200,
                        AlchemodInit.CONFIG.forgeTimeoutSeconds(),
                        system,
                        user));

        if (result.isError()) {
            AlchemodInit.LOG.error("[Forge] API error: {}", result.error());
            return ForgeResult.error(result.error());
        }

        return parseForgeResult(result.content());
    }

    // ── Parse JSON response ───────────────────────────────────────────────────

    private ForgeResult parseForgeResult(String content) {
        try {
            String cleaned = JsonParsingUtils.stripCodeFence(content != null ? content.trim() : "");
            String jsonBody = JsonParsingUtils.extractFirstJsonObject(cleaned);
            if (jsonBody == null) {
                // Fallback: look for a bare item id
                return new ForgeResult(extractBareItemId(cleaned), null, null, null, List.of(), null);
            }

            JsonObject obj = JsonParser.parseString(jsonBody).getAsJsonObject();

            String itemId = JsonParsingUtils.getString(obj, "item_id", null);
            if (itemId == null) itemId = extractBareItemId(cleaned);
            if (itemId == null) itemId = "minecraft:nether_star";

            String name   = JsonParsingUtils.getNullableString(obj, "name");
            String lore   = JsonParsingUtils.getNullableString(obj, "lore");
            String rarity = normalise(JsonParsingUtils.getNullableString(obj, "rarity"));
            if (rarity != null && !List.of("common","uncommon","rare","epic","legendary").contains(rarity))
                rarity = null;

            List<EnchantSpec> enchants = new ArrayList<>();
            if (obj.has("enchantments") && obj.get("enchantments").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("enchantments")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject e = el.getAsJsonObject();
                    String id    = JsonParsingUtils.getString(e, "id", null);
                    int    level = e.has("level") ? e.get("level").getAsInt() : 1;
                    if (id != null) enchants.add(new EnchantSpec(id, Math.max(1, Math.min(level, 10))));
                }
            }

            return new ForgeResult(itemId, name, lore, rarity, enchants, null);

        } catch (JsonSyntaxException | IllegalStateException e) {
            AlchemodInit.LOG.warn("[Forge] JSON parse failed: {}", e.getMessage());
            return new ForgeResult("minecraft:nether_star", null, null, null, List.of(), null);
        }
    }

    private static String extractBareItemId(String content) {
        Pattern p = Pattern.compile("([a-z][a-z0-9_]*:[a-z][a-z0-9_/]*)");
        Matcher m = p.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    // ── Apply result on server thread ─────────────────────────────────────────

    private void applyResult(ForgeResult result) {
        aiPending = false;

        if (result.error() != null) {
            AlchemodInit.LOG.warn("[Forge] Error from AI: {}", result.error());
            state    = STATE_ERROR;
            progress = 0;
            markDirty();
            return;
        }

        Identifier id   = Identifier.tryParse(result.itemId());
        Item       item = id != null ? Registries.ITEM.get(id) : null;
        if (item == null || item == Items.AIR) {
            AlchemodInit.LOG.warn("[Forge] Unknown item '{}', falling back to nether_star", result.itemId());
            item = Items.NETHER_STAR;
        }

        // Consume inputs
        items.get(SLOT_A).decrement(1);
        items.get(SLOT_B).decrement(1);
        if (items.get(SLOT_A).isEmpty()) items.set(SLOT_A, ItemStack.EMPTY);
        if (items.get(SLOT_B).isEmpty()) items.set(SLOT_B, ItemStack.EMPTY);

        ItemStack output = new ItemStack(item);

        // Apply AI-generated enrichment
        applyAiEnrichment(output, result);

        // Also apply any manual overrides the player set via the NBT panel
        applyManualOverrides(output);

        items.set(SLOT_OUTPUT, output);
        lastResultName   = result.name()   != null ? result.name()   : "";
        lastResultRarity = result.rarity() != null ? result.rarity() : "";
        progress = MAX_PROGRESS;
        state    = STATE_READY;
        AlchemodInit.LOG.info("[Forge] Output: {} (name='{}', rarity='{}')",
                Registries.ITEM.getId(item), lastResultName, lastResultRarity);
        markDirty();
    }

    private void applyAiEnrichment(ItemStack stack, ForgeResult result) {
        NbtCompound forgeTag = new NbtCompound();

        if (result.name() != null && !result.name().isBlank()) {
            Text nameText = applyRarityFormatting(result.name(), result.rarity());
            stack.set(DataComponentTypes.CUSTOM_NAME, nameText);
            forgeTag.putString("forge_name",   result.name());
            forgeTag.putString("forge_rarity",  result.rarity() != null ? result.rarity() : "");
        }

        if (result.lore() != null && !result.lore().isBlank()) {
            stack.set(DataComponentTypes.LORE,
                    new LoreComponent(List.of(Text.literal("§7§o" + result.lore()))));
            forgeTag.putString("forge_lore", result.lore());
        }

        if (!result.enchantments().isEmpty() && world != null) {
            Registry<Enchantment> reg = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            ItemEnchantmentsComponent.Builder builder =
                    new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
            boolean added = false;
            for (EnchantSpec spec : result.enchantments()) {
                Identifier eid = Identifier.tryParse(spec.id());
                if (eid == null) eid = Identifier.tryParse("minecraft:" + spec.id());
                if (eid == null) continue;
                var entry = reg.getEntry(eid);
                if (entry.isPresent()) {
                    builder.add(entry.get(), spec.level());
                    added = true;
                    AlchemodInit.LOG.info("[Forge] Applied enchantment {}×{}", eid, spec.level());
                }
            }
            if (added) {
                stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
            }
        }

        if (!forgeTag.isEmpty()) {
            NbtCompound root = new NbtCompound();
            root.put("alchemod_forge", forgeTag);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        }
    }

    private static Text applyRarityFormatting(String name, String rarity) {
        String prefix = switch (rarity != null ? rarity : "") {
            case "uncommon"  -> "§a";
            case "rare"      -> "§b";
            case "epic"      -> "§d";
            case "legendary" -> "§6§l";
            default          -> "§f";
        };
        return Text.literal(prefix + name);
    }

    private void applyManualOverrides(ItemStack stack) {
        if (!customName.isBlank()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(customName));
        }
        if (!customLore.isBlank()) {
            List<Text> lines = parseLoreLines(customLore);
            if (!lines.isEmpty()) stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
        }
        if (customColor != -1) {
            stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(customColor, true));
        }
        if (!customEnchantments.isEmpty() && world != null) {
            Registry<Enchantment> reg = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            ItemEnchantmentsComponent.Builder builder =
                    new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
            boolean any = false;
            for (String spec : customEnchantments) {
                ParsedEnchantment pe = parseEnchantment(spec);
                if (pe == null) continue;
                var entry = reg.getEntry(pe.id());
                if (entry.isPresent()) { builder.add(entry.get(), pe.level()); any = true; }
            }
            if (any) stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        }
        if (hideFlags != 0) {
            stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        }
    }

    // ── onOutputTaken ─────────────────────────────────────────────────────────

    public void onOutputTaken() {
        state    = STATE_IDLE;
        progress = 0;
        markDirty();
    }

    // ── Accessors for GUI ─────────────────────────────────────────────────────

    public String getLastResultName()   { return lastResultName; }
    public String getLastResultRarity() { return lastResultRarity; }

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

    @Override public int size()  { return 3; }
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

    public PropertyDelegate getDelegate()       { return delegate; }
    public int getMaxProgress()                 { return MAX_PROGRESS; }
    public DefaultedList<ItemStack> getItems()  { return items; }

    // Manual-override setters (called from ForgeNbtPayload handler)
    public void applyCustomData(String name, String lore, int color,
            List<String> enchantments, int flags) {
        customName         = sanitiseText(name);
        customLore         = sanitiseText(lore);
        customColor        = sanitiseColor(color);
        customEnchantments = sanitiseEnchantments(enchantments);
        hideFlags          = Math.max(flags, 0);
        markDirty();
    }

    // ── NBT persistence ───────────────────────────────────────────────────────

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        Inventories.writeNbt(nbt, items, lookup);
        nbt.putInt("State",    state);
        nbt.putInt("Progress", progress);
        nbt.putString("LastName",   lastResultName);
        nbt.putString("LastRarity", lastResultRarity);
        // Manual overrides
        nbt.putString("CustomName",         customName);
        nbt.putString("CustomLore",         customLore);
        nbt.putInt   ("CustomColor",        customColor);
        nbt.putString("CustomEnchantments", String.join(",", customEnchantments));
        nbt.putInt   ("HideFlags",          hideFlags);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        state           = nbt.getInt("State");
        progress        = nbt.getInt("Progress");
        lastResultName  = nbt.getString("LastName");
        lastResultRarity= nbt.getString("LastRarity");
        customName      = nbt.getString("CustomName");
        customLore      = nbt.getString("CustomLore");
        customColor     = nbt.getInt("CustomColor");
        hideFlags       = nbt.getInt("HideFlags");
        String enchStr  = nbt.getString("CustomEnchantments");
        customEnchantments = enchStr.isBlank() ? new ArrayList<>()
                : new ArrayList<>(List.of(enchStr.split(",")));
        if (state == STATE_PROCESSING) { state = STATE_IDLE; progress = 0; }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null)
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }

    // ── Enchantment / lore helpers ────────────────────────────────────────────

    private static String normalise(String v) {
        return v == null ? null : v.toLowerCase().replace("minecraft:","").trim();
    }

    private static ParsedEnchantment parseEnchantment(String spec) {
        if (spec == null || spec.isBlank()) return null;
        String trimmed = spec.trim();
        int level = 1;
        String idPart = trimmed;
        int at = trimmed.lastIndexOf('@');
        if (at > 0) {
            idPart = trimmed.substring(0, at).trim();
            try { level = Integer.parseInt(trimmed.substring(at + 1).trim()); } catch (NumberFormatException ignored) {}
        } else {
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2 && parts[parts.length - 1].chars().allMatch(Character::isDigit)) {
                try { level = Integer.parseInt(parts[parts.length - 1]); } catch (NumberFormatException ignored) {}
                idPart = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1)).trim();
            }
        }
        Identifier id = Identifier.tryParse(idPart);
        if (id == null) id = Identifier.tryParse("minecraft:" + idPart);
        return id != null ? new ParsedEnchantment(id, Math.max(1, level)) : null;
    }

    private static List<Text> parseLoreLines(String lore) {
        List<Text> lines = new ArrayList<>();
        for (String line : lore.replace("\\n","|").split("\\|")) {
            String t = line.trim();
            if (!t.isBlank()) lines.add(Text.literal(t));
        }
        return lines;
    }

    private static String sanitiseText(String v) { return v == null ? "" : v.trim(); }
    private static int sanitiseColor(int v) { return v < 0 ? -1 : v & 0xFFFFFF; }
    private static List<String> sanitiseEnchantments(List<String> list) {
        List<String> out = new ArrayList<>();
        if (list == null) return out;
        for (String s : list) { if (s != null && !s.trim().isBlank()) out.add(s.trim()); }
        return out;
    }

    // ── Inner records ─────────────────────────────────────────────────────────

    private record ForgeResult(
            String itemId, String name, String lore, String rarity,
            List<EnchantSpec> enchantments, String error) {
        static ForgeResult error(String msg) {
            return new ForgeResult("minecraft:nether_star", null, null, null, List.of(), msg);
        }
    }

    private record EnchantSpec(String id, int level) {}
    private record ParsedEnchantment(Identifier id, int level) {}
}
