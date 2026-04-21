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
 * we assign it a name and texture at runtime without touching the registry.
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

    /** Claim the next available slot. Returns null if pool exhausted. */
    public static synchronized DynamicItem claimSlot(CreatedItemMeta meta) {
        if (nextSlot >= POOL_SIZE) {
            AlchemodInit.LOG.warn("[Creator] Dynamic item pool exhausted!");
            return null;
        }
        int slot = nextSlot++;
        META.put(slot, meta);
        DynamicItem item = POOL.get(slot);
        item.setMeta(meta);
        AlchemodInit.LOG.info("[Creator] Claimed slot {} for '{}'", slot, meta.name());
        return item;
    }

    public static DynamicItem getSlot(int index) {
        return index >= 0 && index < POOL.size() ? POOL.get(index) : null;
    }

    public static CreatedItemMeta getMeta(int slot) {
        return META.get(slot);
    }

    /** Simple record holding everything we know about a created item. */
    public record CreatedItemMeta(String name, String description, int slot) {}
}
