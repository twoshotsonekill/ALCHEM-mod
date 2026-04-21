package com.alchemod.creator;

import com.alchemod.AlchemodInit;
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

/**
 * Pre-registers a pool of "dynamic item" slots at startup.
 * Each slot starts as a blank item; when the Creator Forge produces one,
 * we assign it a name, description, power, and texture at runtime.
 *
 * Pool size = 64 unique created items per game session.
 */
public class DynamicItemRegistry {

    public static final int POOL_SIZE = 64;
    private static final List<DynamicItem> POOL = new ArrayList<>();

    // Maps slot index → metadata about the created item
    private static final Map<Integer, CreatedItemMeta> META = new ConcurrentHashMap<>();

    private static int nextSlot = 0;

    public static void register() {
        for (int i = 0; i < POOL_SIZE; i++) {
            Identifier id = Identifier.of(AlchemodInit.MOD_ID, "dynamic_item_" + i);
            DynamicItem item = new DynamicItem(
                    new Item.Settings()
                            .maxCount(64)
                            .registryKey(RegistryKey.of(RegistryKeys.ITEM, id)),
                    i);
            Registry.register(Registries.ITEM, id, item);
            POOL.add(item);
        }
        AlchemodInit.LOG.info("[Creator] Registered {} dynamic item slots.", POOL_SIZE);
    }

    /**
     * Claim the next available slot. Returns null if pool exhausted.
     * Call updateSlotMeta() afterwards to assign the final metadata.
     */
    public static synchronized DynamicItem claimSlot() {
        if (nextSlot >= POOL_SIZE) {
            AlchemodInit.LOG.warn("[Creator] Dynamic item pool exhausted!");
            return null;
        }
        int slot = nextSlot++;
        AlchemodInit.LOG.info("[Creator] Claimed slot {}", slot);
        return POOL.get(slot);
    }

    /** Set (or replace) the metadata for a slot and push it to the item instance. */
    public static void updateSlotMeta(int slot, CreatedItemMeta meta) {
        META.put(slot, meta);
        DynamicItem item = POOL.get(slot);
        if (item != null) item.setMeta(meta);
    }

    public static DynamicItem getSlot(int index) {
        return index >= 0 && index < POOL.size() ? POOL.get(index) : null;
    }

    public static CreatedItemMeta getMeta(int slot) {
        return META.get(slot);
    }

    /**
     * Metadata for a created item.
     *
     * @param name        Display name (e.g. "Ember Shard")
     * @param description Flavour text shown in tooltip
     * @param slot        Dynamic item slot index (0–63)
     * @param power       One of the supported vanilla effect IDs, e.g. "speed"
     */
    public record CreatedItemMeta(String name, String description, int slot, String power) {

        /** Human-readable label for the power, used in tooltip. */
        public String powerLabel() {
            return switch (power.toLowerCase().replace("minecraft:", "").trim()) {
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
                default -> "§7Unknown";
            };
        }
    }
}
