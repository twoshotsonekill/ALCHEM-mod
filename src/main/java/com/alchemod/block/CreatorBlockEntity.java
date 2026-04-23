package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.ai.OpenRouterClient;
import com.alchemod.creator.DynamicItem;
import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.screen.CreatorScreenHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class CreatorBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, Inventory {

    public static final int SLOT_A = 0;
    public static final int SLOT_B = 1;
    public static final int SLOT_OUTPUT = 2;

    public static final int STATE_IDLE = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_ERROR = 3;

    private static final int MAX_PROGRESS = 100;

    private static final Set<String> VALID_EFFECTS = Set.of(
            "speed", "strength", "regeneration", "resistance",
            "fire_resistance", "night_vision", "absorption", "luck",
            "haste", "jump_boost", "slow_falling", "water_breathing");

    private static final Set<String> VALID_SPECIALS = Set.of(
            "ignite", "knockback", "heal_aura", "launch",
            "freeze", "drain", "phase", "lightning", "void_step");

    private static final Set<String> VALID_RARITIES = Set.of(
            "common", "uncommon", "rare", "epic", "legendary");

    private static final Set<String> VALID_ITEM_TYPES = Set.of(
            "use_item", "bow", "spawn_egg", "food", "sword", "totem", "throwable");

    private static final Set<String> VALID_MOBS = Set.of(
            "zombie", "skeleton", "creeper", "spider", "enderman", "blaze", "witch",
            "phantom", "slime", "magma_cube", "hoglin", "strider",
            "cow", "pig", "sheep", "chicken", "bat", "wolf",
            "rabbit", "fox", "bee", "axolotl", "parrot");

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(3, ItemStack.EMPTY);

    private int state = STATE_IDLE;
    private int progress = 0;
    private boolean aiPending = false;
    private int lastCreatedSlot = -1;

    private final PropertyDelegate delegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> state;
                case 1 -> progress;
                case 2 -> lastCreatedSlot;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> state = value;
                case 1 -> progress = value;
                case 2 -> lastCreatedSlot = value;
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return 3;
        }
    };

    public CreatorBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemodInit.CREATOR_BE_TYPE, pos, state);
    }

    public void serverTick(World world, BlockPos pos) {
        if (state == STATE_IDLE
                && !items.get(SLOT_A).isEmpty()
                && !items.get(SLOT_B).isEmpty()
                && items.get(SLOT_OUTPUT).isEmpty()
                && !aiPending) {
            startCreation(world);
        }

        if (state == STATE_PROCESSING) {
            progress = Math.min(progress + 1, MAX_PROGRESS - 1);
            markDirty();
        }
    }

    public void setBehaviorCodeEnabled(boolean enabled) {
        markDirty();
    }

    public boolean isBehaviorCodeEnabled() {
        return true;
    }

    private void startCreation(World world) {
        aiPending = true;
        state = STATE_PROCESSING;
        progress = 0;
        markDirty();

        ItemStack inputA = items.get(SLOT_A);
        ItemStack inputB = items.get(SLOT_B);
        String itemA = inputA.getName().getString() + " (" + Registries.ITEM.getId(inputA.getItem()) + ")";
        String itemB = inputB.getName().getString() + " (" + Registries.ITEM.getId(inputB.getItem()) + ")";
        String inputIdA = Registries.ITEM.getId(inputA.getItem()).toString();
        String inputIdB = Registries.ITEM.getId(inputB.getItem()).toString();
        boolean allowBehaviorCode = true;

        AlchemodInit.LOG.info("[Creator] Inventing from {} + {} (scripted={})", itemA, itemB, allowBehaviorCode);

        CompletableFuture.supplyAsync(() -> queryOpenRouter(itemA, itemB, allowBehaviorCode))
                .thenAccept(result -> {
                    if (world.getServer() != null) {
                        world.getServer().execute(() -> applyResult(result, inputIdA, inputIdB));
                    }
                });
    }

    private CreationResult queryOpenRouter(String itemA, String itemB, boolean allowBehaviorCode) {
        String system = buildSystemPrompt(allowBehaviorCode);
        String user = "Create a new magical item by combining: " + itemA + " + " + itemB;
        OpenRouterClient.ChatResult result = OpenRouterClient.chat(
                AlchemodInit.OPENROUTER_KEY,
                new OpenRouterClient.ChatRequest(
                        AlchemodInit.CONFIG.creatorModel(),
                        allowBehaviorCode
                                ? AlchemodInit.CONFIG.creatorMaxTokensScripted()
                                : AlchemodInit.CONFIG.creatorMaxTokensPlain(),
                        AlchemodInit.CONFIG.creatorTimeoutSeconds(),
                        system,
                        user));

        if (result.isError()) {
            AlchemodInit.LOG.error("[Creator] API error: {}", result.error());
            return CreationResult.error(result.error());
        }

        return parseCreationResult(result.rawBody() != null ? result.rawBody() : result.content(), allowBehaviorCode);
    }

    private String buildSystemPrompt(boolean allowBehaviorCode) {
        String basePrompt = """
You are a creative Minecraft alchemist inventing imaginative new items.
Return ONLY valid JSON with these fields:
{
  "name": "Item Name",
  "description": "One sentence flavour text.",
  "sprite_prompt": "8-15 word pixel art prompt",
  "rarity": "common|uncommon|rare|epic|legendary",
  "item_type": "use_item|bow|spawn_egg|food|sword|totem|throwable",
  "effects": ["speed"],
  "special": "ignite|null",
  "mob_type": "bat|null",
  "behavior_script": null
}

Rules:
- Valid effects only: speed, strength, regeneration, resistance, fire_resistance, night_vision, absorption, luck, haste, jump_boost, slow_falling, water_breathing
- Valid specials only: ignite, knockback, heal_aura, launch, freeze, drain, phase, lightning, void_step
- Valid spawn mobs only: zombie, skeleton, creeper, spider, enderman, blaze, witch, phantom, slime, magma_cube, hoglin, strider, cow, pig, sheep, chicken, bat, wolf, rabbit, fox, bee, axolotl, parrot
- Avoid defaulting to gems, crystals, glowing shards, or generic relics unless the ingredients clearly imply them.
- Favor distinct silhouettes and weird object identities: prank tools, cursed snacks, unstable bows, masks, keys, horns, trophies, jars, scraps, fake relics, junk gadgets, novelty trash, bombs, puppets, talismans, and odd little machines.
- Common items may be goofy, awkward, decorative, mildly useful, or intentionally disappointing. Some results should be funny or strange instead of powerful.
- Higher-rarity items should feel bold and surprising, including explosive bow-style weapons, chaotic throwables, dramatic spawn eggs, cursed totems, or dangerous one-off artifacts.
- sprite_prompt must describe a concrete object shape and materials, not a generic magical artifact.
- common and uncommon items may have 0-2 effects, rare may have 1-2, epic gets 2-3, legendary gets 3-4
- rare, epic, and legendary should usually have a special
- if item_type is not spawn_egg, mob_type must be null
- behavior_script must be null when scripting is disabled
""";

        if (!allowBehaviorCode) {
            return basePrompt + """

Scripting is disabled for this creation.
Set "behavior_script" to null and focus on metadata only.
""";
        }

        return basePrompt + """

Scripting is enabled for this creation.
Generate a sandboxed JavaScript function in "behavior_script" that defines what happens on right click.

The script must define:
function onUse(player, world) { ... }

Available API:
- player.heal(amount)
- player.damage(amount)
- player.addEffect(name, ticks, level)
- player.removeEffect(name)
- player.addVelocity(x, y, z)
- player.teleport(x, y, z)
- player.setOnFire(seconds)
- player.extinguish()
- player.sendMessage("Text")
- player.getX(), player.getY(), player.getZ(), player.getHealth(), player.getLookDir(), player.isSneaking(), player.isInWater()
- world.createExplosion(x, y, z, power, fire)
- world.spawnLightning(x, y, z)
- world.spawnMob(entityId, x, y, z)
- world.playSound(soundId, volume, pitch)
- world.setBlock(x, y, z, blockId)
- world.isDay(), world.isRaining(), world.getTime()
- nearbyEntities(radius) returning entities with damage, heal, knockbackFrom, setVelocity, addEffect, setOnFire, extinguish, getHealth, getX, getY, getZ, getType, isPlayer, isAlive
- log(message)

Script rules:
- return a JSON escaped single string value
- make the script unique to the ingredients
- keep it under 20 lines
- use player.sendMessage for feedback
- stay thematic with rarity, effects, special, and item_type
- novelty or mostly useless items should still do something distinctive, even if it is small, awkward, cosmetic, or funny
- bow items may simulate explosive or dramatic shots on use; throwable items may burst; spawn eggs may summon the chosen mob
""";
    }

    private CreationResult parseCreationResult(String apiBody, boolean allowBehaviorCode) {
        try {
            String content = OpenRouterClient.extractContent(apiBody);
            String jsonBody = extractFirstJsonObject(stripCodeFence(content != null ? content : apiBody));
            if (jsonBody == null) {
                return CreationResult.error("Could not parse AI response");
            }

            JsonObject object = JsonParser.parseString(jsonBody).getAsJsonObject();
            String name = getString(object, "name", "Mysterious Relic");
            String description = getString(object, "description", "An enigmatic object of unknown power.");
            String spritePrompt = getString(object, "sprite_prompt", name + " magical glowing artifact");
            String rarity = normalise(getString(object, "rarity", "common"));
            String itemType = normalise(getString(object, "item_type", "use_item"));
            List<String> effects = sanitiseEffects(getStringList(object, "effects"));
            String special = sanitiseSpecial(getNullableString(object, "special"));
            String mobType = sanitiseMobType(itemType, getNullableString(object, "mob_type"));
            String behaviorScript = allowBehaviorCode ? getNullableString(object, "behavior_script") : null;

            if (!VALID_RARITIES.contains(rarity)) {
                rarity = "common";
            }

            if (!VALID_ITEM_TYPES.contains(itemType)) {
                itemType = "use_item";
            }

            int maxEffects = switch (rarity) {
                case "common", "uncommon", "rare" -> 2;
                case "epic" -> 3;
                case "legendary" -> 4;
                default -> 2;
            };

            int minEffects = switch (rarity) {
                case "rare" -> 1;
                case "epic" -> 2;
                case "legendary" -> 3;
                default -> 0;
            };

            if (effects.size() > maxEffects) {
                effects = effects.subList(0, maxEffects);
            }

            if (effects.size() < minEffects) {
                List<String> filler = new ArrayList<>(effects);
                for (String candidate : List.of("luck", "speed", "jump_boost", "night_vision", "strength", "regeneration")) {
                    if (!filler.contains(candidate)) {
                        filler.add(candidate);
                    }
                    if (filler.size() >= minEffects) {
                        break;
                    }
                }
                effects = List.copyOf(filler);
            }

            if (special == null && Set.of("rare", "epic", "legendary").contains(rarity)) {
                special = fallbackSpecial(!effects.isEmpty() ? effects.get(0) : "luck", itemType);
            }

            if (allowBehaviorCode && (behaviorScript == null || behaviorScript.isBlank())) {
                behaviorScript = buildFallbackScript(name, rarity, itemType, effects, special, mobType);
            }

            return new CreationResult(
                    name,
                    description,
                    spritePrompt,
                    rarity,
                    itemType,
                    effects,
                    special,
                    mobType,
                    behaviorScript,
                    null);
        } catch (JsonSyntaxException | IllegalStateException e) {
            AlchemodInit.LOG.warn("[Creator] Failed to parse AI JSON: {}", e.getMessage());
            return CreationResult.error("Invalid AI JSON");
        }
    }

    private List<String> sanitiseEffects(List<String> inputEffects) {
        List<String> cleanEffects = new ArrayList<>();
        for (String effect : inputEffects) {
            String normalised = normalise(effect);
            if (VALID_EFFECTS.contains(normalised) && !cleanEffects.contains(normalised)) {
                cleanEffects.add(normalised);
            }
        }
        return cleanEffects;
    }

    private String sanitiseSpecial(String value) {
        if (value == null) {
            return null;
        }

        String normalised = normalise(value);
        return VALID_SPECIALS.contains(normalised) ? normalised : null;
    }

    private String sanitiseMobType(String itemType, String value) {
        if (!"spawn_egg".equals(itemType)) {
            return null;
        }

        String normalised = normalise(value);
        return VALID_MOBS.contains(normalised) ? normalised : "bat";
    }

    private void applyResult(CreationResult result, String inputIdA, String inputIdB) {
        aiPending = false;

        if (result.error() != null) {
            state = STATE_ERROR;
            progress = 0;
            markDirty();
            return;
        }

        DynamicItem dynamicItem = DynamicItemRegistry.claimSlot();
        if (dynamicItem == null) {
            state = STATE_ERROR;
            markDirty();
            return;
        }

        int claimedSlot = dynamicItem.getSlotIndex();
        DynamicItemRegistry.CreatedItemMeta meta = new DynamicItemRegistry.CreatedItemMeta(
                result.name(),
                result.description(),
                claimedSlot,
                result.rarity(),
                result.itemType(),
                result.effects(),
                result.special(),
                result.mobType(),
                result.behaviorScript());
        DynamicItemRegistry.updateSlotMeta(claimedSlot, meta);

        items.get(SLOT_A).decrement(1);
        items.get(SLOT_B).decrement(1);
        if (items.get(SLOT_A).isEmpty()) {
            items.set(SLOT_A, ItemStack.EMPTY);
        }
        if (items.get(SLOT_B).isEmpty()) {
            items.set(SLOT_B, ItemStack.EMPTY);
        }

        ItemStack output = new ItemStack(dynamicItem);
        NbtCompound tag = new NbtCompound();
        tag.putString("creator_name", result.name());
        tag.putString("creator_desc", result.description());
        tag.putString("creator_sprite", result.spritePrompt());
        tag.putString("creator_rarity", result.rarity());
        tag.putString("creator_item_type", result.itemType());
        tag.putString("creator_effects", String.join(",", result.effects()));
        tag.putString("creator_special", result.special() != null ? result.special() : "");
        tag.putString("creator_mob_type", result.mobType() != null ? result.mobType() : "");
        tag.putInt("creator_slot", claimedSlot);
        tag.putInt("charges", meta.startingCharges());
        tag.putString("creator_input_a", inputIdA);
        tag.putString("creator_input_b", inputIdB);
        if (result.behaviorScript() != null && !result.behaviorScript().isBlank()) {
            tag.putString("creator_script", result.behaviorScript());
        }
        output.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));

        items.set(SLOT_OUTPUT, output);
        lastCreatedSlot = claimedSlot;
        progress = MAX_PROGRESS;
        state = STATE_READY;
        markDirty();
    }

    public void onOutputTaken() {
        state = STATE_IDLE;
        progress = 0;
        markDirty();
    }

    public PropertyDelegate getDelegate() {
        return delegate;
    }

    public int getMaxProgress() {
        return MAX_PROGRESS;
    }

    public DefaultedList<ItemStack> getItems() {
        return items;
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
        return new CreatorScreenHandler(syncId, inventory, this, delegate, getPos());
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.alchemod.item_creator");
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        items.clear();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        Inventories.writeNbt(nbt, items, lookup);
        nbt.putInt("State", state);
        nbt.putInt("Progress", progress);
        nbt.putInt("LastSlot", lastCreatedSlot);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        Inventories.readNbt(nbt, items, lookup);
        state = nbt.getInt("State");
        progress = nbt.getInt("Progress");
        lastCreatedSlot = nbt.getInt("LastSlot");
        if (state == STATE_PROCESSING) {
            state = STATE_IDLE;
            progress = 0;
        }
    }

    private static String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstLine = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLine < 0 || lastFence <= firstLine) {
            return trimmed.replace("```", "").trim();
        }

        return trimmed.substring(firstLine + 1, lastFence).trim();
    }

    private static String extractFirstJsonObject(String content) {
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }

            if (current == '\\') {
                escaped = true;
                continue;
            }

            if (current == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (current == '{') {
                if (depth == 0) {
                    start = index;
                }
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    return content.substring(start, index + 1);
                }
            }
        }

        return null;
    }

    private static String getString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString().trim() : fallback;
    }

    private static String getNullableString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        String value = element.getAsString().trim();
        return value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private static List<String> getStringList(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item != null && !item.isJsonNull()) {
                values.add(item.getAsString());
            }
        }
        return values;
    }

    private static String normalise(String value) {
        return value == null ? "" : value.toLowerCase().replace("minecraft:", "").trim();
    }

    private static String fallbackSpecial(String effect, String itemType) {
        if ("bow".equals(itemType)) {
            return "ignite";
        }
        if ("throwable".equals(itemType)) {
            return "lightning";
        }
        if ("spawn_egg".equals(itemType)) {
            return "phase";
        }

        return switch (effect) {
            case "strength" -> "knockback";
            case "speed" -> "void_step";
            case "regeneration" -> "heal_aura";
            case "jump_boost" -> "launch";
            case "fire_resistance" -> "ignite";
            case "night_vision" -> "phase";
            case "water_breathing" -> "freeze";
            case "haste" -> "lightning";
            default -> "drain";
        };
    }

    private static String buildFallbackScript(String name, String rarity, String itemType, List<String> effects, String special, String mobType) {
        int duration = switch (rarity) {
            case "uncommon" -> 240;
            case "rare" -> 320;
            case "epic" -> 420;
            case "legendary" -> 520;
            default -> 180;
        };
        int amplifier = switch (rarity) {
            case "rare" -> 1;
            case "epic", "legendary" -> 2;
            default -> 0;
        };

        StringBuilder builder = new StringBuilder();
        builder.append("function onUse(player, world) {\n");
        for (String effect : effects) {
            builder.append("  player.addEffect('")
                    .append(effect)
                    .append("', ")
                    .append(duration)
                    .append(", ")
                    .append(amplifier)
                    .append(");\n");
        }

        switch (itemType) {
            case "bow" -> builder.append("  var look = player.getLookDir();\n")
                    .append("  var tx = player.getX() + look[0] * 7;\n")
                    .append("  var ty = player.getY() + 1.2 + look[1] * 4;\n")
                    .append("  var tz = player.getZ() + look[2] * 7;\n")
                    .append("  world.createExplosion(tx, ty, tz, ")
                    .append(Set.of("epic", "legendary").contains(rarity) ? "3.0" : "2.0")
                    .append(", false);\n")
                    .append("  world.playSound('entity.firework_rocket.launch', 0.9, 0.8);\n");
            case "throwable" -> builder.append("  var look = player.getLookDir();\n")
                    .append("  var tx = player.getX() + look[0] * 5;\n")
                    .append("  var ty = player.getY() + 1.0 + look[1] * 3;\n")
                    .append("  var tz = player.getZ() + look[2] * 5;\n")
                    .append("  world.createExplosion(tx, ty, tz, 1.6, false);\n")
                    .append("  world.playSound('entity.snowball.throw', 0.9, 0.9);\n");
            case "spawn_egg" -> {
                if (mobType != null && !mobType.isBlank()) {
                    builder.append("  var look = player.getLookDir();\n")
                            .append("  world.spawnMob('")
                            .append(escapeForSingleQuotedScript(mobType))
                            .append("', player.getX() + look[0] * 2, player.getY(), player.getZ() + look[2] * 2);\n")
                            .append("  world.playSound('entity.evoker.prepare_summon', 0.8, 1.1);\n");
                }
            }
            case "food" -> builder.append("  player.heal(")
                    .append(Set.of("epic", "legendary").contains(rarity) ? "6" : "3")
                    .append(");\n")
                    .append("  world.playSound('entity.generic.eat', 0.7, 1.4);\n");
            case "totem" -> builder.append("  player.addEffect('absorption', 240, ")
                    .append(Set.of("epic", "legendary").contains(rarity) ? "1" : "0")
                    .append(");\n")
                    .append("  world.playSound('item.totem.use', 0.7, 1.1);\n");
            default -> {
            }
        }

        if (special != null) {
            switch (special) {
                case "ignite" -> builder.append("  var targets = nearbyEntities(6);\n")
                        .append("  for (var i = 0; i < targets.length; i++) { targets[i].setOnFire(6); }\n");
                case "knockback" -> builder.append("  var targets = nearbyEntities(6);\n")
                        .append("  for (var i = 0; i < targets.length; i++) { targets[i].knockbackFrom(3); }\n");
                case "heal_aura" -> builder.append("  player.heal(6);\n");
                case "launch" -> builder.append("  player.addVelocity(0, 1.2, 0);\n");
                case "freeze" -> builder.append("  var targets = nearbyEntities(7);\n")
                        .append("  for (var i = 0; i < targets.length; i++) { targets[i].addEffect('slowness', 180, 2); }\n");
                case "drain" -> builder.append("  var targets = nearbyEntities(7);\n")
                        .append("  for (var i = 0; i < targets.length; i++) { targets[i].damage(5); }\n")
                        .append("  player.heal(4);\n");
                case "phase" -> builder.append("  player.addEffect('invisibility', 160, 0);\n");
                case "lightning" -> builder.append("  var targets = nearbyEntities(8);\n")
                        .append("  for (var i = 0; i < targets.length; i++) { world.spawnLightning(targets[i].getX(), targets[i].getY(), targets[i].getZ()); }\n");
                case "void_step" -> builder.append("  player.addEffect('slow_falling', 200, 0);\n")
                        .append("  player.addVelocity(0, 0.8, 0);\n");
                default -> {
                }
            }
        }

        if ("common".equals(rarity) && effects.isEmpty() && special == null && !"spawn_egg".equals(itemType)) {
            builder.append("  world.playSound('entity.item.pickup', 0.6, 1.8);\n")
                    .append("  player.sendMessage('")
                    .append(escapeForSingleQuotedScript(name))
                    .append(" squeaks, rattles, and does very little.');\n");
        }

        builder.append("  player.sendMessage('")
                .append(escapeForSingleQuotedScript(name))
                .append(" does something strange.');\n");
        builder.append("  world.playSound('block.beacon.activate', 0.7, 1.2);\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static String escapeForSingleQuotedScript(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    record CreationResult(
            String name,
            String description,
            String spritePrompt,
            String rarity,
            String itemType,
            List<String> effects,
            String special,
            String mobType,
            String behaviorScript,
            String error
    ) {
        static CreationResult error(String message) {
            return new CreationResult(null, null, null, null, "use_item", List.of(), null, null, null, message);
        }
    }
}
