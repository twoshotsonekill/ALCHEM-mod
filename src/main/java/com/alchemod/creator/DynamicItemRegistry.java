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
 * Manages the pre-allocated pool of {@link DynamicItem} instances.
 *
 * <h3>Design note</h3>
 * Minecraft requires every item type to be registered at startup, so we
 * pre-register {@link #POOL_SIZE} items ({@code dynamic_item_0} …
 * {@code dynamic_item_63}).  At runtime these are just vessels — all gameplay
 * identity (name, effects, script, rarity, …) lives in NBT components on the
 * {@link net.minecraft.item.ItemStack} and survives server restarts untouched.
 *
 * <p>The in-memory {@code META} map is kept as a same-session convenience so that
 * the texture manager can access the sprite prompt immediately after creation,
 * before the player picks up the item.  It must never be treated as the source of
 * truth for gameplay.
 */
public final class DynamicItemRegistry {

    public static final int POOL_SIZE = 64;

    private static final List<DynamicItem> POOL = new ArrayList<>();

    /**
     * Same-session cache only.  Not persisted.  Gameplay reads from ItemStack NBT.
     */
    private static final Map<Integer, CreatedItemMeta> META = new ConcurrentHashMap<>();

    private static int nextSlot = 0;

    private DynamicItemRegistry() {
    }

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
        AlchemodInit.LOG.info("[Creator] Registered {} dynamic item slots.", POOL_SIZE);
    }

    /**
     * Claims the next available pool slot.  When the pool is exhausted in a
     * single session (unlikely but possible after 64 creations without restart)
     * this wraps around to slot 0 rather than returning null, so players always
     * receive an item.  The old slot-0 item in the world is unaffected because it
     * reads its identity from its own NBT.
     */
    public static synchronized DynamicItem claimSlot() {
        if (nextSlot >= POOL_SIZE) {
            AlchemodInit.LOG.warn(
                    "[Creator] Dynamic item pool exhausted ({} items created this session). "
                    + "Wrapping around to slot 0. Existing items are unaffected — they read "
                    + "identity from their own NBT.", POOL_SIZE);
            nextSlot = 0;
        }
        return POOL.get(nextSlot++);
    }

    /** Updates the same-session metadata cache.  Does NOT affect saved items. */
    public static void updateSlotMeta(int slot, CreatedItemMeta meta) {
        META.put(slot, meta);
        // setMeta is now a no-op on DynamicItem, kept for API compatibility.
        DynamicItem item = getSlot(slot);
        if (item != null) {
            item.setMeta(meta);
        }
    }

    public static DynamicItem getSlot(int index) {
        return index >= 0 && index < POOL.size() ? POOL.get(index) : null;
    }

    /** Same-session cache lookup.  May return null after restart — use ItemStack NBT instead. */
    public static CreatedItemMeta getMeta(int slot) {
        return META.get(slot);
    }

    // ── CreatedItemMeta ───────────────────────────────────────────────────────

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
            return switch (normalise(rarity())) {
                case "uncommon"  -> 5;
                case "rare"      -> 6;
                case "epic"      -> 7;
                case "legendary" -> 10;
                default          -> 3;
            };
        }

        public boolean hasScript() {
            return script != null && !script.isBlank();
        }

        public String rarityLabel() {
            return staticRarityLabel(rarity());
        }

        public String itemTypeLabel() {
            return staticItemTypeLabel(itemType());
        }

        public String specialLabel() {
            return staticSpecialLabel(special());
        }

        // ── Static helpers (safe to call without a meta instance) ─────────────

        public static String staticRarityLabel(String rarity) {
            return switch (normalise(rarity)) {
                case "uncommon"  -> "§aUncommon";
                case "rare"      -> "§bRare";
                case "epic"      -> "§dEpic";
                case "legendary" -> "§6§lLegendary";
                default          -> "§7Common";
            };
        }

        public static String effectLabel(String effect) {
            return switch (normalise(effect)) {
                case "speed"            -> "§9Speed";
                case "strength"         -> "§4Strength";
                case "regeneration"     -> "§cRegeneration";
                case "resistance"       -> "§7Resistance";
                case "fire_resistance"  -> "§6Fire Resistance";
                case "night_vision"     -> "§bNight Vision";
                case "absorption"       -> "§eAbsorption";
                case "luck"             -> "§aLuck";
                case "haste"            -> "§6Haste";
                case "jump_boost"       -> "§aJump Boost";
                case "slow_falling"     -> "§fSlow Falling";
                case "water_breathing"  -> "§3Water Breathing";
                default                 -> "§7" + effect;
            };
        }

        public static String staticItemTypeLabel(String itemType) {
            if (itemType == null || itemType.isBlank()) return "§7Use Item";
            return switch (normalise(itemType)) {
                case "bow"        -> "§7Magical Bow";
                case "spawn_egg"  -> "§7Spawn Egg";
                case "food"       -> "§7Consumable";
                case "sword"      -> "§7Melee Weapon";
                case "totem"      -> "§7Passive Totem";
                case "throwable"  -> "§7Throwable";
                default           -> "§7Use Item";
            };
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

        private static String normalise(String value) {
            return value == null ? "" : value.toLowerCase().replace("minecraft:", "").trim();
        }
    }
}
