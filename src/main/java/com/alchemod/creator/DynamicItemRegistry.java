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

public class DynamicItemRegistry {

    public static final int POOL_SIZE = 64;
    private static final List<DynamicItem>             POOL = new ArrayList<>();
    private static final Map<Integer, CreatedItemMeta> META = new ConcurrentHashMap<>();
    private static int nextSlot = 0;

    public static final String[][] RARITY_COLORS = {
            {"§f", "§8", "gray"},        // common
            {"§a", "§2", "green"},      // uncommon
            {"§b", "§3", "cyan"},       // rare
            {"§d", "§5", "purple"},      // epic
            {"§e", "§6", "gold"}        // legendary
    };

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
        return POOL.get(nextSlot++);
    }

    public static void updateSlotMeta(int slot, CreatedItemMeta meta) {
        META.put(slot, meta);
        DynamicItem item = POOL.get(slot);
        if (item != null) item.setMeta(meta);
    }

    public static DynamicItem     getSlot(int index) { return index >= 0 && index < POOL.size() ? POOL.get(index) : null; }
    public static CreatedItemMeta getMeta(int slot)   { return META.get(slot); }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full metadata for a created item.
     *
     * @param itemType  How the item is activated. One of:
     *                  use_item | bow | spawn_egg | food | sword | totem | throwable
     * @param mobType   Vanilla entity ID string for spawn_egg type only (e.g. "bat").
     *                  Empty string / null for all other types.
     * @param effects   Vanilla effect IDs applied on use (to player or target, by type).
     * @param special   Named special ability (ignite, knockback, etc.). May be null.
     * @param rarity    common | uncommon | rare | epic | legendary
     */
    public record CreatedItemMeta(
            String       name,
            String       description,
            int          slot,
            String       rarity,
            String       itemType,
            List<String> effects,
            String       special,
            String       mobType
    ) {
        public static final int DEFAULT_CHARGES_FALLBACK = 3;

        public String rarityColour() {
            return switch (rarity().toLowerCase()) {
                case "uncommon"  -> "§a";
                case "rare"      -> "§b";
                case "epic"      -> "§d";
                case "legendary" -> "§6";
                default          -> "§f";
            };
        }

        public String rarityLabel() {
            return switch (rarity().toLowerCase()) {
                case "uncommon"  -> "§aUncommon";
                case "rare"      -> "§bRare";
                case "epic"      -> "§dEpic";
                case "legendary" -> "§6§lLegendary";
                default          -> "§7Common";
            };
        }

        public int startingCharges() {
            return switch (rarity().toLowerCase()) {
                case "uncommon"  -> 5;
                case "rare"      -> 6;
                case "epic"      -> 7;
                case "legendary" -> 10;
                default          -> 3;
            };
        }

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
                default                -> "§7" + effect;
            };
        }

        public String itemTypeLabel() { return staticItemTypeLabel(itemType()); }

        public String specialLabel() { return staticSpecialLabel(special()); }

        // ── Static equivalents used by DynamicItem.RuntimeMeta ────────────────

        public static String staticItemTypeLabel(String t) {
            if (t == null) return "§7✦ Use Item";
            return switch (t.toLowerCase()) {
                case "bow"       -> "§7🏹 Magical Bow";
                case "spawn_egg" -> "§7🥚 Spawn Egg";
                case "food"      -> "§7🍎 Consumable";
                case "sword"     -> "§7⚔ Melee Weapon";
                case "totem"     -> "§7🔮 Passive Totem";
                case "throwable" -> "§7💥 Throwable";
                default          -> "§7✦ Use Item";
            };
        }

        public static String staticSpecialLabel(String special) {
            if (special == null || special.isBlank()) return null;
            return switch (special.toLowerCase()) {
                case "ignite"    -> "§c🔥 Ignite";
                case "knockback" -> "§7💥 Knockback";
                case "heal_aura" -> "§a❤ Heal Aura";
                case "launch"    -> "§e🚀 Launch";
                case "freeze"    -> "§b❄ Freeze";
                case "drain"     -> "§5⚡ Drain";
                case "phase"     -> "§d👁 Phase";
                case "lightning" -> "§e⚡ Lightning";
                case "void_step" -> "§8✦ Void Step";
                default          -> "§7" + special;
            };
        }
    }
}
