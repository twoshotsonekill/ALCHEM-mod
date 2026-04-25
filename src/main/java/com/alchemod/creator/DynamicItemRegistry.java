package com.alchemod.creator;

import com.alchemod.AlchemodInit;
import com.alchemod.item.OddityItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicItemRegistry {

    public static final int POOL_SIZE = 64;

    /** The single NBT-driven item used for all new creations. */
    public static OddityItem ODDITY_ITEM = null;

    private static final List<DynamicItem> POOL = new ArrayList<>();
    private static final Map<Integer, CreatedItemMeta> META = new ConcurrentHashMap<>();

    private static int nextSlot = 0;

    private DynamicItemRegistry() {
    }

    /** Registers the single {@link OddityItem}. Called from {@link AlchemodInit}. */
    public static void registerOddity(OddityItem item) {
        ODDITY_ITEM = item;
    }

    /**
     * Registers the legacy 64-slot {@link DynamicItem} pool.  These exist for
     * backward compatibility with items already in player inventories or world
     * storage.  New creations go to {@link #ODDITY_ITEM} instead.
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
                case "bow"       -> "§7Magical Bow";
                case "spawn_egg" -> "§7Spawn Egg";
                case "food"      -> "§7Consumable";
                case "sword"     -> "§7Melee Weapon";
                case "totem"     -> "§7Passive Totem";
                case "throwable" -> "§7Throwable";
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
}
