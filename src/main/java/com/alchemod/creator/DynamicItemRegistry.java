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
 * we assign it name / description / rarity / effects / special at runtime.
 *
 * Pool size = 64 unique created items per game session.
 */
public class DynamicItemRegistry {

    public static final int POOL_SIZE = 64;
    private static final List<DynamicItem>              POOL = new ArrayList<>();
    private static final Map<Integer, CreatedItemMeta>  META = new ConcurrentHashMap<>();

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

    public static synchronized DynamicItem claimSlot() {
        if (nextSlot >= POOL_SIZE) {
            AlchemodInit.LOG.warn("[Creator] Dynamic item pool exhausted!");
            return null;
        }
        int slot = nextSlot++;
        AlchemodInit.LOG.info("[Creator] Claimed slot {}", slot);
        return POOL.get(slot);
    }

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

    // ─────────────────────────────────────────────────────────────────────────
    // Meta record
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full metadata for one created item.
     *
     * @param name         Display name  — e.g. "Ember Crown"
     * @param description  Flavour text  — one sentence
     * @param slot         Dynamic item slot index (0–63)
     * @param rarity       One of: common | uncommon | rare | epic | legendary
     * @param effects      Ordered list of vanilla effect IDs assigned by the AI.
     *                     Length scales with rarity (1 → 4).
     * @param special      Optional special-ability ID; may be null for low rarities.
     *                     One of: ignite | knockback | heal_aura | launch |
     *                             freeze | drain | phase | lightning | void_step
     */
    public record CreatedItemMeta(
            String       name,
            String       description,
            int          slot,
            String       rarity,
            List<String> effects,
            String       special
    ) {
        /** Charge count used when the item predates the rarity system. */
        public static final int DEFAULT_CHARGES_FALLBACK = 3;

        // ── Rarity helpers ────────────────────────────────────────────────────

        /** § colour code for the item name. */
        public String rarityColour() {
            return switch (rarity().toLowerCase()) {
                case "uncommon"  -> "§a"; // green
                case "rare"      -> "§b"; // aqua
                case "epic"      -> "§d"; // light purple
                case "legendary" -> "§6"; // gold
                default          -> "§f"; // white (common)
            };
        }

        /** Formatted rarity label for the tooltip. */
        public String rarityLabel() {
            return switch (rarity().toLowerCase()) {
                case "uncommon"  -> "§aUncommon";
                case "rare"      -> "§bRare";
                case "epic"      -> "§dEpic";
                case "legendary" -> "§6§lLegendary";
                default          -> "§7Common";
            };
        }

        /** Number of charges the item spawns with (scales with rarity). */
        public int startingCharges() {
            return switch (rarity().toLowerCase()) {
                case "uncommon"  -> 5;
                case "rare"      -> 6;
                case "epic"      -> 7;
                case "legendary" -> 10;
                default          -> 3; // common
            };
        }

        // ── Effect label helpers ───────────────────────────────────────────────

        /** Formatted label for a single effect ID. */
        public static String effectLabel(String effect) {
            return switch (effect.toLowerCase().replace("minecraft:", "").trim()) {
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
                default -> "§7" + effect;
            };
        }

        /** Formatted label for the special ability. */
        public String specialLabel() {
            if (special() == null || special().isBlank()) return null;
            return switch (special().toLowerCase()) {
                case "ignite"    -> "§c🔥 Ignite";
                case "knockback" -> "§7💥 Knockback";
                case "heal_aura" -> "§a❤ Heal Aura";
                case "launch"    -> "§e🚀 Launch";
                case "freeze"    -> "§b❄ Freeze";
                case "drain"     -> "§5⚡ Drain";
                case "phase"     -> "§d👁 Phase";
                case "lightning" -> "§e⚡ Lightning";
                case "void_step" -> "§8✦ Void Step";
                default -> "§7" + special();
            };
        }
    }
}
