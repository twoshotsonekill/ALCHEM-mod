package com.alchemod.creator;

import com.alchemod.AlchemodInit;
import com.alchemod.item.GeneratedItem;
import com.alchemod.item.OddityItem;
import com.alchemod.mixin.SimpleRegistryAccessor;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class DynamicItemRegistry {

    public static final int POOL_SIZE = 64;

    /** Legacy NBT-driven item retained for existing worlds; new creation requires runtime injection. */
    public static OddityItem ODDITY_ITEM = null;

    private static final List<DynamicItem> POOL = new ArrayList<>();
    private static final Map<Integer, CreatedItemMeta> META = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_RUNTIME_ID = new AtomicInteger();
    private static final Object RUNTIME_REGISTRY_LOCK = new Object();

    private static int nextSlot = 0;

    private DynamicItemRegistry() {
    }

    /** Registers the legacy {@link OddityItem}. Called from {@link AlchemodInit}. */
    public static void registerOddity(OddityItem item) {
        ODDITY_ITEM = item;
    }

    /**
     * Registers the legacy 64-slot {@link DynamicItem} pool. These exist only
     * for backward compatibility with items already in player inventories or
     * world storage. New creations require {@link #tryRegisterRuntimeItem}.
     */
    public static void register() {
        for (int index = 0; index < POOL_SIZE; index++) {
            Identifier id = Identifier.of(AlchemodInit.MOD_ID, "dynamic_item_" + index);
            DynamicItem item = new DynamicItem(
                    new Item.Settings()
                            .maxCount(64)
                            .registryKey(RegistryKey.of(RegistryKeys.ITEM, id)),
                    index);
            Registry.register(Registries.ITEM, id, item);
            POOL.add(item);
        }

        AlchemodInit.LOG.info("[Creator] Registered {} legacy dynamic item slots.", POOL_SIZE);
    }

    /** Claims the next available legacy slot. Returns {@code null} when exhausted. */
    public static synchronized DynamicItem claimSlot() {
        if (nextSlot >= POOL_SIZE) {
            AlchemodInit.LOG.warn("[Creator] Legacy dynamic item pool exhausted.");
            return null;
        }
        return POOL.get(nextSlot++);
    }

    public static void updateSlotMeta(int slot, CreatedItemMeta meta) {
        META.put(slot, meta);
        DynamicItem item = getSlot(slot);
        if (item != null) {
            item.setMeta(meta);
        }
    }

    public static DynamicItem getSlot(int index) {
        return index >= 0 && index < POOL.size() ? POOL.get(index) : null;
    }

    public static CreatedItemMeta getMeta(int slot) {
        return META.get(slot);
    }

    public static RuntimeItemResult tryRegisterRuntimeItem(CreatedItemMeta meta) {
        if (meta == null) {
            return RuntimeItemResult.failure("missing metadata");
        }

        String safeName = sanitiseId(meta.name());
        int sequence = NEXT_RUNTIME_ID.getAndIncrement();
        Identifier id = Identifier.of(
                AlchemodInit.MOD_ID,
                "generated/" + safeName + "_" + Integer.toUnsignedString(meta.slot(), 16) + "_" + sequence);

        try {
            GeneratedItem item = new GeneratedItem(
                    new Item.Settings()
                            .maxCount(maxCountFor(meta.itemType()))
                            .registryKey(RegistryKey.of(RegistryKeys.ITEM, id)),
                    id.toString(),
                    meta.itemType());
            registerRuntimeItemUnsafe(id, item);
            AlchemodInit.LOG.warn("[Creator] Runtime item injection succeeded for {}. This is unsafe and may not survive registry reloads.", id);
            return RuntimeItemResult.success(item, id);
        } catch (Throwable t) {
            AlchemodInit.LOG.warn("[Creator] Runtime item injection failed for {}: {}.",
                    id, t.getMessage());
            return RuntimeItemResult.failure(t.getMessage());
        }
    }

    // ── Metadata record ───────────────────────────────────────────────────────

    public record CreatedItemMeta(
            String name,
            String description,
            int slot,
            String rarity,
            String itemType,
            List<String> effects,
            String special,
            String mobType,
            String script
    ) {
        public int startingCharges() {
            return startingChargesForRarity(rarity());
        }

        public static int startingChargesForRarity(String rarity) {
            return switch (normalise(rarity)) {
                case "uncommon" -> 5;
                case "rare"     -> 6;
                case "epic"     -> 7;
                case "legendary" -> 10;
                default         -> 3;
            };
        }

        public boolean hasScript() {
            return script != null && !script.isBlank();
        }

        // ── Label helpers (both instance and static versions) ─────────────────

        public String rarityLabel() {
            return staticRarityLabel(rarity());
        }

        public static String staticRarityLabel(String rarity) {
            return switch (normalise(rarity)) {
                case "uncommon"  -> "§aUncommon";
                case "rare"      -> "§bRare";
                case "epic"      -> "§dEpic";
                case "legendary" -> "§6§lLegendary";
                default          -> "§7Common";
            };
        }

        public String itemTypeLabel() {
            return staticItemTypeLabel(itemType());
        }

        public static String staticItemTypeLabel(String itemType) {
            if (itemType == null || itemType.isBlank()) return "§7Use Item";
            return switch (normalise(itemType)) {
                case "artifact"  -> "§7Coded Artifact";
                case "tool"      -> "§7Generated Tool";
                case "potion"    -> "§7Generated Potion";
                case "weapon"    -> "§7Generated Weapon";
                case "wand"      -> "§7Generated Wand";
                case "charm"     -> "§7Generated Charm";
                case "scroll"    -> "§7Generated Scroll";
                case "bow"       -> "§7Magical Bow";
                case "spawn_item" -> "§7Spawn Item";
                case "spawn_egg" -> "§7Spawn Egg";
                case "food"      -> "§7Consumable";
                case "sword"     -> "§7Melee Weapon";
                case "totem"     -> "§7Passive Totem";
                case "throwable" -> "§7Throwable";
                case "block"     -> "§7Generated Block";
                default          -> "§7Use Item";
            };
        }

        public String specialLabel() {
            return staticSpecialLabel(special());
        }

        public static String staticSpecialLabel(String special) {
            if (special == null || special.isBlank()) return null;
            return switch (normalise(special)) {
                case "ignite"    -> "§cIgnite";
                case "knockback" -> "§7Knockback";
                case "heal_aura" -> "§aHeal Aura";
                case "launch"    -> "§eLaunch";
                case "freeze"    -> "§bFreeze";
                case "drain"     -> "§5Drain";
                case "phase"     -> "§dPhase";
                case "lightning" -> "§eLightning";
                case "void_step" -> "§8Void Step";
                default          -> "§7" + special;
            };
        }

        public static String effectLabel(String effect) {
            return switch (normalise(effect)) {
                case "speed"           -> "§9Speed";
                case "strength"        -> "§4Strength";
                case "regeneration"    -> "§cRegeneration";
                case "resistance"      -> "§7Resistance";
                case "fire_resistance" -> "§6Fire Resistance";
                case "night_vision"    -> "§bNight Vision";
                case "absorption"      -> "§eAbsorption";
                case "luck"            -> "§aLuck";
                case "haste"           -> "§6Haste";
                case "jump_boost"      -> "§aJump Boost";
                case "slow_falling"    -> "§fSlow Falling";
                case "water_breathing" -> "§3Water Breathing";
                default                -> "§7" + effect;
            };
        }

        private static String normalise(String value) {
            return value == null ? "" : value.toLowerCase().replace("minecraft:", "").trim();
        }
    }

    public record RuntimeItemResult(Item item, Identifier id, String error) {
        public static RuntimeItemResult success(Item item, Identifier id) {
            return new RuntimeItemResult(item, id, null);
        }

        public static RuntimeItemResult failure(String error) {
            return new RuntimeItemResult(null, null, error == null ? "unknown" : error);
        }

        public boolean succeeded() {
            return item != null && id != null;
        }
    }

    private static int maxCountFor(String itemType) {
        return switch (normaliseType(itemType)) {
            case "potion", "wand", "charm", "scroll", "artifact", "spawn_item", "spawn_egg", "totem" -> 1;
            case "throwable", "food" -> 16;
            case "block" -> 8;
            default -> 1;
        };
    }

    @SuppressWarnings("unchecked")
    private static void registerRuntimeItemUnsafe(Identifier id, Item item) {
        synchronized (RUNTIME_REGISTRY_LOCK) {
            if (!(Registries.ITEM instanceof SimpleRegistry<?> rawRegistry)) {
                throw new IllegalStateException("Item registry is not a SimpleRegistry");
            }
            if (!(rawRegistry instanceof SimpleRegistryAccessor accessor)) {
                throw new IllegalStateException("Item registry accessor mixin is unavailable");
            }

            SimpleRegistry<Item> itemRegistry = (SimpleRegistry<Item>) rawRegistry;
            boolean wasFrozen = accessor.alchemod$isFrozen();
            boolean registered = false;

            try {
                if (wasFrozen) {
                    accessor.alchemod$setFrozen(false);
                }
                Registry.register(Registries.ITEM, id, item);
                registered = true;
                if (wasFrozen) {
                    itemRegistry.freeze();
                }
            } catch (Throwable t) {
                if (wasFrozen && !registered) {
                    accessor.alchemod$setFrozen(true);
                }
                throw t;
            }
        }
    }

    private static String sanitiseId(String name) {
        String lower = name == null ? "item" : name.toLowerCase(Locale.ROOT);
        String cleaned = lower.replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+", "").replaceAll("_+$", "");
        if (cleaned.isBlank()) {
            return "item";
        }
        return cleaned.length() <= 32 ? cleaned : cleaned.substring(0, 32);
    }

    private static String normaliseType(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("minecraft:", "").trim();
    }
}
