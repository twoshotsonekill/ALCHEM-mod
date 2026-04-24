package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.ai.OpenRouterClient;
import com.alchemod.screen.ForgeScreenHandler;
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
    private static final int MAX_PROGRESS = 80; // ~4 seconds at 20 tps

    private final DefaultedList<ItemStack> items =
            DefaultedList.ofSize(3, ItemStack.EMPTY);

    private int state = STATE_IDLE;
    private int progress = 0;
    private boolean aiPending = false;

    // NBT metadata for custom output
    private String customName = "";
    private String customLore = "";
    private int customColor = -1;
    private List<String> customEnchantments = new ArrayList<>();
    private int hideFlags = 0;

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

    // ── OpenRouter API call via centralized client ────────────────────────────
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

        // Use centralized OpenRouterClient instead of inline HTTP
        OpenRouterClient.ChatResult result = OpenRouterClient.chat(
                key,
                new OpenRouterClient.ChatRequest(
                        AlchemodInit.CONFIG.forgeModel(),
                        30,  // max_tokens
                        AlchemodInit.CONFIG.forgeTimeoutSeconds(),
                        system,
                        user));

        if (result.isError()) {
            AlchemodInit.LOG.error("[Alchemod] API error: {}", result.error());
            return "ERROR:" + result.error();
        }

        return parseItemId(result.content());
    }

    // Extract item ID from response content (e.g. "minecraft:blaze_rod")
    private String parseItemId(String content) {
        // Extract first namespace:id token from the response
        Pattern idMatch = Pattern.compile("([a-z][a-z0-9_]*:[a-z][a-z0-9_/]*)");
        Matcher m = idMatch.matcher(content);
        if (m.find()) {
            return m.group(1);
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
        ItemStack output = new ItemStack(item);
        applyNbtToStack(output);
        items.set(SLOT_OUTPUT, output);

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

    // NBT getter/setter for custom properties
    public String getCustomName() { return customName; }
    public void setCustomName(String name) {
        customName = sanitiseText(name);
        markDirty();
    }
    public String getCustomLore() { return customLore; }
    public void setCustomLore(String lore) {
        customLore = sanitiseText(lore);
        markDirty();
    }
    public int getCustomColor() { return customColor; }
    public void setCustomColor(int color) {
        customColor = sanitiseColor(color);
        markDirty();
    }
    public List<String> getCustomEnchantments() { return customEnchantments; }
    public void setCustomEnchantments(List<String> ench) {
        customEnchantments = sanitiseEnchantments(ench);
        markDirty();
    }
    public int getHideFlags() { return hideFlags; }
    public void setHideFlags(int flags) {
        hideFlags = Math.max(flags, 0);
        markDirty();
    }

    public void applyCustomData(String name, String lore, int color, List<String> enchantments, int flags) {
        customName = sanitiseText(name);
        customLore = sanitiseText(lore);
        customColor = sanitiseColor(color);
        customEnchantments = sanitiseEnchantments(enchantments);
        hideFlags = Math.max(flags, 0);
        markDirty();
    }

    private void applyNbtToStack(ItemStack stack) {
        NbtCompound tag = new NbtCompound();
        NbtCompound forgeTag = new NbtCompound();
        if (!customName.isBlank()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(customName));
            forgeTag.putString("display_name", customName);
        }
        if (!customLore.isBlank()) {
            List<Text> loreLines = parseLoreLines(customLore);
            if (!loreLines.isEmpty()) {
                stack.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
            }
            forgeTag.putString("display_lore", customLore);
        }
        if (customColor != -1) {
            stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(customColor, hideFlags == 0));
            forgeTag.putInt("display_color", customColor);
        }
        if (!customEnchantments.isEmpty()) {
            ItemEnchantmentsComponent enchantments = buildEnchantments();
            if (enchantments != null && !enchantments.isEmpty()) {
                if (hideFlags != 0) {
                    enchantments = enchantments.withShowInTooltip(false);
                }
                stack.set(DataComponentTypes.ENCHANTMENTS, enchantments);
            }
            forgeTag.putString("enchantments", String.join(",", customEnchantments));
        }
        if (hideFlags != 0) {
            stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
            forgeTag.putInt("hide_flags", hideFlags);
        }

        if (!forgeTag.isEmpty()) {
            tag.put("alchemod_forge", forgeTag);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
        }
    }

    // ── NBT ───────────────────────────────────────────────────────────────────
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        Inventories.writeNbt(nbt, items, lookup);
        nbt.putInt("State", state);
        nbt.putInt("Progress", progress);
        nbt.putString("CustomName", customName);
        nbt.putString("CustomLore", customLore);
        nbt.putInt("CustomColor", customColor);
        nbt.putString("CustomEnchantments", String.join(",", customEnchantments));
        nbt.putInt("HideFlags", hideFlags);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        state    = nbt.getInt("State");
        progress = nbt.getInt("Progress");
        customName = nbt.getString("CustomName");
        customLore = nbt.getString("CustomLore");
        customColor = nbt.getInt("CustomColor");
        String enchStr = nbt.getString("CustomEnchantments");
        if (!enchStr.isBlank()) {
            customEnchantments = new ArrayList<>(List.of(enchStr.split(",")));
        } else {
            customEnchantments = new ArrayList<>();
        }
        hideFlags = nbt.getInt("HideFlags");
        // If it was mid-process when the world saved, reset so it retries
        if (state == STATE_PROCESSING) { state = STATE_IDLE; progress = 0; }
    }

    // ── Util ──────────────────────────────────────────────────────────────────
    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    private ItemEnchantmentsComponent buildEnchantments() {
        if (world == null || customEnchantments.isEmpty()) {
            return null;
        }

        Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        boolean addedAny = false;

        for (String spec : customEnchantments) {
            ParsedEnchantment parsed = parseEnchantment(spec);
            if (parsed == null) {
                continue;
            }

            java.util.Optional<RegistryEntry.Reference<Enchantment>> entry = registry.getEntry(parsed.id());
            if (entry.isPresent()) {
                builder.add(entry.get(), parsed.level());
                addedAny = true;
            } else {
                AlchemodInit.LOG.warn("[Alchemod] Unknown enchantment '{}'", parsed.id());
            }
        }

        return addedAny ? builder.build() : null;
    }

    private static ParsedEnchantment parseEnchantment(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }

        String trimmed = spec.trim();
        int level = 1;
        String idPart = trimmed;

        int atIndex = trimmed.lastIndexOf('@');
        if (atIndex > 0 && atIndex < trimmed.length() - 1) {
            idPart = trimmed.substring(0, atIndex).trim();
            level = parsePositiveInt(trimmed.substring(atIndex + 1).trim(), 1);
        } else {
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2 && parts[parts.length - 1].chars().allMatch(Character::isDigit)) {
                level = parsePositiveInt(parts[parts.length - 1], 1);
                idPart = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1)).trim();
            }
        }

        Identifier id = Identifier.tryParse(idPart);
        if (id == null) {
            id = Identifier.tryParse("minecraft:" + idPart);
        }
        if (id == null) {
            return null;
        }

        return new ParsedEnchantment(id, Math.max(1, level));
    }

    private static List<Text> parseLoreLines(String lore) {
        List<Text> lines = new ArrayList<>();
        String[] splitLines = lore.replace("\\n", "|").split("\\|");
        for (String line : splitLines) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                lines.add(Text.literal(trimmed));
            }
        }
        return lines;
    }

    private static String sanitiseText(String value) {
        return value == null ? "" : value.trim();
    }

    private static int sanitiseColor(int value) {
        return value < 0 ? -1 : value & 0xFFFFFF;
    }

    private static List<String> sanitiseEnchantments(List<String> enchantments) {
        List<String> clean = new ArrayList<>();
        if (enchantments == null) {
            return clean;
        }

        for (String enchantment : enchantments) {
            if (enchantment != null) {
                String trimmed = enchantment.trim();
                if (!trimmed.isBlank()) {
                    clean.add(trimmed);
                }
            }
        }
        return clean;
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String quoted(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n").replace("\r", "") + "\"";
    }

    private record ParsedEnchantment(Identifier id, int level) {
    }
}
