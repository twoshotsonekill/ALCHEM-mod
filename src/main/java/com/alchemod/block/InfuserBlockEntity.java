package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.ai.OpenRouterClient;
import com.alchemod.screen.InfuserScreenHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class InfuserBlockEntity extends BlockEntity
        implements NamedScreenHandlerFactory, net.minecraft.inventory.Inventory {

    public static final int SLOT_BASE     = 0;
    public static final int SLOT_INGREDIENT_A = 1;
    public static final int SLOT_INGREDIENT_B = 2;
    public static final int SLOT_OUTPUT   = 3;

    public static final int STATE_IDLE       = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_READY      = 2;
    public static final int STATE_ERROR      = 3;

    private static final int MAX_PROGRESS = 100;

    private final DefaultedList<ItemStack> items =
            DefaultedList.ofSize(4, ItemStack.EMPTY);

    private int state    = STATE_IDLE;
    private int progress = 0;
    private boolean aiPending = false;

    private String lastPotionName = "";
    private String lastEffectName = "";
    private String lastRarity     = "";
    private int    lastDuration   = 0;
    private int    lastAmplifier  = 0;
    private String lastColor      = "#00FF00";

    private final PropertyDelegate delegate = new PropertyDelegate() {
        @Override public int  get(int i)       { return i == 0 ? state : progress; }
        @Override public void set(int i, int v){ if (i == 0) state = v; else progress = v; }
        @Override public int  size()           { return 2; }
    };

    public InfuserBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemodInit.INFUSER_BE_TYPE, pos, state);
    }

    public void serverTick(World world, BlockPos pos) {
        if (state == STATE_IDLE
                && !items.get(SLOT_BASE).isEmpty()
                && (!items.get(SLOT_INGREDIENT_A).isEmpty() || !items.get(SLOT_INGREDIENT_B).isEmpty())
                && items.get(SLOT_OUTPUT).isEmpty()
                && !aiPending) {
            startInfusion(world);
        }
        if (state == STATE_PROCESSING) {
            progress = Math.min(progress + 1, MAX_PROGRESS - 1);
            markDirty();
        }
    }

    private void startInfusion(World world) {
        aiPending = true;
        state     = STATE_PROCESSING;
        progress  = 0;
        lastPotionName = "";
        lastEffectName = "";
        lastRarity     = "";
        lastDuration   = 0;
        lastAmplifier  = 0;
        lastColor      = "#00FF00";
        markDirty();

        String baseItem = items.get(SLOT_BASE).getName().getString()
                + " (" + Registries.ITEM.getId(items.get(SLOT_BASE).getItem()) + ")";
        String ingA = items.get(SLOT_INGREDIENT_A).isEmpty() ? "none"
                : items.get(SLOT_INGREDIENT_A).getName().getString()
                    + " (" + Registries.ITEM.getId(items.get(SLOT_INGREDIENT_A).getItem()) + ")";
        String ingB = items.get(SLOT_INGREDIENT_B).isEmpty() ? "none"
                : items.get(SLOT_INGREDIENT_B).getName().getString()
                    + " (" + Registries.ITEM.getId(items.get(SLOT_INGREDIENT_B).getItem()) + ")";

        AlchemodInit.LOG.info("[Infuser] Infusing: {} + {} + {}", baseItem, ingA, ingB);

        CompletableFuture.supplyAsync(() -> queryOpenRouter(baseItem, ingA, ingB))
                .thenAccept(result -> {
                    if (world.getServer() != null) {
                        world.getServer().execute(() -> applyResult(result));
                    }
                });
    }

    private InfuserResult queryOpenRouter(String baseItem, String ingA, String ingB) {
        String key = AlchemodInit.OPENROUTER_KEY;
        if (key.isBlank()) {
            AlchemodInit.LOG.error("[Infuser] OPENROUTER_API_KEY not set.");
            return InfuserResult.error("no_key");
        }

        String system = """
                You are an AI potion brewer. Given a base potion and 0-2 ingredients,
                you create a custom potion effect with unique properties.
                Respond with ONLY valid JSON - no markdown fences, no explanation.

                Required field:
                  "potion_name": "Creative name for the resulting potion"

                Optional fields (omit any you don't want):
                  "effect_name": "Name of the status effect"
                  "duration": <seconds> (30-600)
                  "amplifier": <level> (0-5)
                  "primary_color": "hex code like #FF0000"
                  "lore": "One atmospheric flavour line (max 12 words)"
                  "rarity": "common" | "uncommon" | "rare" | "epic" | "legendary"

                Rules:
                - Potion name should be evocative and short (≤4 words)
                - Duration must be between 30 and 600 seconds
                - Amplifier must be between 0 and 5
                - Color must be a valid hex code
                - Rarity affects the potion's visual quality
                - Common results need no special effects
                - Rare/legendary results deserve custom names, lore, and effects
                """;

        String user = "Base: " + baseItem + " + Ingredient A: " + ingA + " + Ingredient B: " + ingB;

        OpenRouterClient.ChatResult result = OpenRouterClient.chat(
                key,
                new OpenRouterClient.ChatRequest(
                        AlchemodInit.CONFIG.infuserModel(),
                        300,
                        AlchemodInit.CONFIG.infuserTimeoutSeconds(),
                        system,
                        user));

        if (result.isError()) {
            AlchemodInit.LOG.error("[Infuser] API error: {}", result.error());
            return InfuserResult.error(result.error());
        }

        return parseInfuserResult(result.content());
    }

    private InfuserResult parseInfuserResult(String content) {
        try {
            String cleaned = stripCodeFence(content != null ? content.trim() : "");
            String jsonBody = extractFirstJsonObject(cleaned);
            if (jsonBody == null) {
                return InfuserResult.fallback();
            }

            JsonObject obj = JsonParser.parseString(jsonBody).getAsJsonObject();

            String potionName = getString(obj, "potion_name", "Mysterious Potion");
            String effectName = getNullable(obj, "effect_name");
            int duration = obj.has("duration") ? Math.max(30, Math.min(600, obj.get("duration").getAsInt())) : 200;
            int amplifier = obj.has("amplifier") ? Math.max(0, Math.min(5, obj.get("amplifier").getAsInt())) : 0;
            String color = getNullable(obj, "primary_color");
            if (color != null && !color.matches("#[0-9A-Fa-f]{6}")) color = "#00FF00";
            String lore = getNullable(obj, "lore");
            String rarity = normalise(getNullable(obj, "rarity"));
            if (rarity != null && !List.of("common","uncommon","rare","epic","legendary").contains(rarity))
                rarity = "common";

            return new InfuserResult(potionName, effectName, duration, amplifier,
                                     color != null ? color : "#00FF00", lore, rarity, null);

        } catch (JsonSyntaxException | IllegalStateException e) {
            AlchemodInit.LOG.warn("[Infuser] JSON parse failed: {}", e.getMessage());
            return InfuserResult.fallback();
        }
    }

    private void applyResult(InfuserResult result) {
        aiPending = false;

        if (result.error() != null) {
            AlchemodInit.LOG.warn("[Infuser] Error from AI: {}", result.error());
            state    = STATE_ERROR;
            progress = 0;
            markDirty();
            return;
        }

        items.get(SLOT_BASE).decrement(1);
        if (!items.get(SLOT_INGREDIENT_A).isEmpty()) items.get(SLOT_INGREDIENT_A).decrement(1);
        if (!items.get(SLOT_INGREDIENT_B).isEmpty()) items.get(SLOT_INGREDIENT_B).decrement(1);

        for (int i = 0; i < 3; i++) {
            if (items.get(i).isEmpty()) items.set(i, ItemStack.EMPTY);
        }

        ItemStack output = new ItemStack(Items.POTION);

        if (result.potionName() != null && !result.potionName().isBlank()) {
            String prefix = getRarityPrefix(result.rarity());
            output.set(DataComponentTypes.CUSTOM_NAME,
                       Text.literal(prefix + result.potionName()));
        }

        if (result.lore() != null && !result.lore().isBlank()) {
            output.set(DataComponentTypes.LORE,
                      new LoreComponent(List.of(Text.literal("§7§o" + result.lore()))));
        }

        if (result.effectName() != null && !result.effectName().isBlank() && world != null) {
            RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect =
                    com.alchemod.script.EffectResolver.resolve(result.effectName().trim());
            if (effect != null) {
                NbtCompound potionTag = new NbtCompound();
                potionTag.putString("infuser_effect", result.effectName());
                potionTag.putInt("infuser_duration", result.duration());
                potionTag.putInt("infuser_amplifier", result.amplifier());
                potionTag.putString("infuser_rarity", result.rarity() != null ? result.rarity() : "common");
                potionTag.putString("infuser_potion_name", result.potionName());

                NbtCompound root = new NbtCompound();
                root.put("alchemod_infuser", potionTag);
                output.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
            }
        }

        items.set(SLOT_OUTPUT, output);
        lastPotionName = result.potionName() != null ? result.potionName() : "";
        lastEffectName  = result.effectName() != null ? result.effectName() : "";
        lastRarity      = result.rarity() != null ? result.rarity() : "";
        lastDuration    = result.duration();
        lastAmplifier   = result.amplifier();
        lastColor       = result.color();
        progress = MAX_PROGRESS;
        state    = STATE_READY;

        AlchemodInit.LOG.info("[Infuser] Output: {} (effect={}, duration={}s, amp={})",
                lastPotionName, lastEffectName, lastDuration, lastAmplifier);
        markDirty();
    }

    private String getRarityPrefix(String rarity) {
        return switch (rarity != null ? rarity : "") {
            case "uncommon"  -> "§a";
            case "rare"      -> "§b";
            case "epic"      -> "§d";
            case "legendary" -> "§6§l";
            default          -> "§f";
        };
    }

    public String getLastPotionName() { return lastPotionName; }
    public String getLastEffectName() { return lastEffectName; }
    public String getLastRarity()     { return lastRarity; }
    public int    getLastDuration()   { return lastDuration; }
    public int    getLastAmplifier()  { return lastAmplifier; }
    public String getLastColor()      { return lastColor; }

    @Override
    public ScreenHandler createMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInv,
                                   net.minecraft.entity.player.PlayerEntity player) {
        return new InfuserScreenHandler(syncId, playerInv, this, delegate);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.alchemod.alchemical_infuser");
    }

    @Override public int size()  { return 4; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack r = net.minecraft.inventory.Inventories.splitStack(items, slot, amount);
        if (!r.isEmpty()) markDirty();
        return r;
    }
    @Override public ItemStack removeStack(int slot) {
        return net.minecraft.inventory.Inventories.removeStack(items, slot);
    }
    @Override public void setStack(int slot, ItemStack stack) { items.set(slot, stack); markDirty(); }
    @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        return net.minecraft.inventory.Inventory.canPlayerUse(this, player);
    }
    @Override public void clear() { items.clear(); }

    public PropertyDelegate getDelegate()       { return delegate; }
    public int getMaxProgress()                 { return MAX_PROGRESS; }
    public DefaultedList<ItemStack> getItems()  { return items; }

    public void onOutputTaken() {
        state    = STATE_IDLE;
        progress = 0;
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        net.minecraft.inventory.Inventories.writeNbt(nbt, items, lookup);
        nbt.putInt("State",    state);
        nbt.putInt("Progress", progress);
        nbt.putString("LastPotionName", lastPotionName);
        nbt.putString("LastEffectName",  lastEffectName);
        nbt.putString("LastRarity",      lastRarity);
        nbt.putInt   ("LastDuration",    lastDuration);
        nbt.putInt   ("LastAmplifier",   lastAmplifier);
        nbt.putString("LastColor",       lastColor);
    }

    @Override
    protected void readNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        net.minecraft.inventory.Inventories.readNbt(nbt, items, lookup);
        state           = nbt.getInt("State");
        progress        = nbt.getInt("Progress");
        lastPotionName  = nbt.getString("LastPotionName");
        lastEffectName  = nbt.getString("LastEffectName");
        lastRarity      = nbt.getString("LastRarity");
        lastDuration    = nbt.getInt("LastDuration");
        lastAmplifier   = nbt.getInt("LastAmplifier");
        lastColor       = nbt.getString("LastColor");
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

    private static String normalise(String v) {
        return v == null ? null : v.toLowerCase().replace("minecraft:","").trim();
    }

    private record InfuserResult(
            String potionName, String effectName, int duration, int amplifier,
            String color, String lore, String rarity, String error) {
        static InfuserResult error(String msg) {
            return new InfuserResult("Error", null, 0, 0, "#FF0000", null, null, msg);
        }
        static InfuserResult fallback() {
            return new InfuserResult("Mysterious Potion", "speed", 200, 0,
                                    "#00FF00", "A swirling mystery", "common", null);
        }
    }
}
