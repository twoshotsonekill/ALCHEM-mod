package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.ai.OpenRouterClient;
import com.alchemod.ai.SpriteToolClient;
import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.screen.CreatorScreenHandler;
import com.alchemod.util.JsonParsingUtils;
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
import net.minecraft.item.Item;
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
            "use_item", "artifact", "tool", "potion", "weapon", "sword", "bow", "wand",
            "charm", "scroll", "throwable", "spawn_item", "spawn_egg", "food", "totem", "block");

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
        @Override public int get(int index) {
            return switch (index) { case 0 -> state; case 1 -> progress; case 2 -> lastCreatedSlot; default -> 0; };
        }
        @Override public void set(int index, int value) {
            switch (index) { case 0 -> state = value; case 1 -> progress = value; case 2 -> lastCreatedSlot = value; }
        }
        @Override public int size() { return 3; }
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

    public void setBehaviorCodeEnabled(boolean enabled) { markDirty(); }
    public boolean isBehaviorCodeEnabled() { return true; }

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

        AlchemodInit.LOG.info("[Creator] Inventing from {} + {}", itemA, itemB);

        CompletableFuture.supplyAsync(() -> queryOpenRouter(itemA, itemB))
                .thenAccept(result -> {
                    if (world.getServer() != null)
                        world.getServer().execute(() -> applyResult(result, inputIdA, inputIdB));
                });
    }

    // ── AI request ────────────────────────────────────────────────────────────

    private CreationResult queryOpenRouter(String itemA, String itemB) {
        CreatorFusionRules.Fusion fusion = CreatorFusionRules.match(itemA, itemB);
        OpenRouterClient.ChatResult result = OpenRouterClient.chat(
                AlchemodInit.OPENROUTER_KEY,
                new OpenRouterClient.ChatRequest(
                        AlchemodInit.CONFIG.creatorModel(),
                        AlchemodInit.CONFIG.creatorMaxTokensScripted(),
                        AlchemodInit.CONFIG.creatorTimeoutSeconds(),
                        buildSystemPrompt(),
                        buildUserPrompt(itemA, itemB, fusion)));

        if (result.isError()) {
            AlchemodInit.LOG.error("[Creator] API error: {}", result.error());
            if (fusion != null) {
                AlchemodInit.LOG.warn("[Creator] API failed; using local semantic fusion recipe for {} + {}.", itemA, itemB);
                return creationFromFusion(fusion, null);
            }
            return CreationResult.error(result.error());
        }

        CreationResult metadata = parseCreationResult(
                result.rawBody() != null ? result.rawBody() : result.content());
        if (metadata.error() != null) {
            if (fusion != null) {
                AlchemodInit.LOG.warn("[Creator] AI parse failed; using local semantic fusion recipe for {} + {}.", itemA, itemB);
                return creationFromFusion(fusion, null);
            }
            return metadata;
        }

        boolean semanticFusionApplied = false;
        if (fusion != null) {
            semanticFusionApplied = true;
            String spriteCommands = metadata.spriteCommands();
            metadata = creationFromFusion(fusion, spriteCommands);
            AlchemodInit.LOG.info("[Creator] Applied semantic fusion recipe: {} + {} -> {}.",
                    itemA, itemB, metadata.name());
        }

        // Use inline sprite_commands when present; fall back to SpriteToolClient.
        String spriteCommands = metadata.spriteCommands();
        if ((spriteCommands == null || spriteCommands.isBlank()) && !semanticFusionApplied) {
            AlchemodInit.LOG.info("[Creator] No inline sprite_commands for '{}', trying SpriteToolClient.", metadata.name());
            try {
                spriteCommands = SpriteToolClient.generateSprite(
                        AlchemodInit.OPENROUTER_KEY,
                        AlchemodInit.CONFIG.creatorModel(),
                        metadata.name(), metadata.description(),
                        metadata.rarity(), metadata.itemType(),
                        AlchemodInit.CONFIG.creatorTimeoutSeconds());
                AlchemodInit.LOG.info("[Creator] SpriteToolClient {}.",
                        spriteCommands != null ? "succeeded" : "returned nothing — glyph fallback");
            } catch (Exception e) {
                AlchemodInit.LOG.warn("[Creator] SpriteToolClient failed: {}", e.getMessage());
            }
        } else {
            AlchemodInit.LOG.info("[Creator] Inline sprite_commands present for '{}'.", metadata.name());
        }

        return new CreationResult(
                metadata.name(), metadata.description(), metadata.spritePrompt(),
                metadata.rarity(), metadata.itemType(), metadata.effects(),
                metadata.special(), metadata.mobType(), metadata.behaviorScript(),
                spriteCommands, null);
    }

    private String buildUserPrompt(String itemA, String itemB, CreatorFusionRules.Fusion fusion) {
        String prompt = "Create a new magical item by combining: " + itemA + " + " + itemB + "\n"
                + "Use obvious ingredient logic before random whimsy: carry the function of each input into the output.\n"
                + "Examples: TNT + Bow should become Explosive Bow with an explosion-on-shot ability; Dirt + Iron should become Landmine Block with a trap/detonation ability.\n"
                + "The item must have a concrete active special ability implemented in behavior_script.";
        if (fusion != null) {
            prompt += "\nThis pair has a clear recipe target: make " + fusion.name()
                    + " as " + fusion.itemType() + " with special=" + fusion.special() + ".";
        }
        return prompt;
    }

    private CreationResult creationFromFusion(CreatorFusionRules.Fusion fusion, String spriteCommands) {
        return new CreationResult(
                fusion.name(),
                fusion.description(),
                fusion.spritePrompt(),
                fusion.rarity(),
                fusion.itemType(),
                fusion.effects(),
                fusion.special(),
                fusion.mobType(),
                fusion.behaviorScript(),
                spriteCommands,
                null);
    }

    // ── System prompt ─────────────────────────────────────────────────────────

    private String buildSystemPrompt() {
        return
            "You are a creative Minecraft alchemist inventing singular, mechanically specific items.\n"
          + "Return ONLY a single valid JSON object — no markdown, no preamble, no trailing text.\n"
          + "\n"
          + "Required fields:\n"
          + "{\n"
          + "  \"name\": \"Item Name\",\n"
          + "  \"description\": \"One sentence flavour text.\",\n"
          + "  \"sprite_prompt\": \"8-15 word pixel art description of a concrete object\",\n"
          + "  \"rarity\": \"common|uncommon|rare|epic|legendary\",\n"
          + "  \"item_type\": \"tool|potion|weapon|wand|charm|scroll|throwable|spawn_item|spawn_egg|artifact|use_item|bow|food|sword|totem|block\",\n"
          + "  \"effects\": [\"speed\"],\n"
          + "  \"special\": \"ignite\",\n"
          + "  \"mob_type\": null,\n"
          + "  \"behavior_script\": \"function onUse(player, world) { ... }\",\n"
          + "  \"sprite_commands\": [ ... ]\n"
          + "}\n"
          + "\n"
          + "=== ITEM DESIGN ===\n"
          + "Valid effects: speed, strength, regeneration, resistance, fire_resistance, night_vision, absorption, luck, haste, jump_boost, slow_falling, water_breathing\n"
          + "Valid specials: ignite, knockback, heal_aura, launch, freeze, drain, phase, lightning, void_step\n"
          + "Valid mob_type (spawn_item or spawn_egg only): zombie, skeleton, creeper, spider, enderman, blaze, witch, phantom, slime, magma_cube, hoglin, strider, cow, pig, sheep, chicken, bat, wolf, rabbit, fox, bee, axolotl, parrot\n"
          + "mob_type must be null unless item_type is spawn_item or spawn_egg. special must be null if no active ability.\n"
          + "common/uncommon: 0-2 effects; rare: 1-2; epic: 2-3; legendary: 3-4.\n"
          + "rare/epic/legendary should usually have a special.\n"
          + "Avoid generic glowing gems, Mysterious Relics, Arcane Orbs, and copy-paste adjectives.\n"
          + "Prefer concrete odd props: junk gadgets, cursed snacks, unstable bows, cracked masks, sealed jars, suspicious bombs, puppets, bent keys, horns, receipts, bells, labels, tags, fake relics.\n"
          + "The name should have a specific noun plus a material, flaw, owner, promise, or failure mode.\n"
          + "Tie behavior to the object identity. A jar leaks, a mask lies, a key tugs, a snack bites back, a bell calls, a tag marks.\n"
          + "When the ingredients imply an obvious fantasy item, use that directly: TNT + Bow -> Explosive Bow; Dirt + Iron -> Landmine Block.\n"
          + "Block-style outputs are allowed with item_type=block; their scripts should place or affect blocks with world.setBlock and still do something special on use.\n"
          + "\n"
          + "behavior_script defines: function onUse(player, world) { ... }\n"
          + "API: player.heal/damage/addEffect/removeEffect/addVelocity/teleport/setOnFire/extinguish/sendMessage/getX/getY/getZ/getHealth/getLookDir/isSneaking/isInWater\n"
          + "     world.createExplosion/spawnLightning/spawnMob/playSound/setBlock/isDay/isRaining/getTime\n"
          + "     nearbyEntities(radius) array with .damage/.heal/.knockbackFrom/.setVelocity/.addEffect/.setOnFire/.extinguish/.getHealth/.getX/.getY/.getZ/.getType/.isPlayer/.isAlive\n"
          + "Keep scripts under 20 lines. Always call player.sendMessage with unique flavour feedback, not generic text.\n"
          + "\n"
          + "=== SPRITE DESIGN (sprite_commands) ===\n"
          + "Paint a 16x16 Minecraft-style pixel art icon. Bold silhouette, 3-6 flat colours, no gradients.\n"
          + "Canvas: x and y run 0-15, origin top-left. 10-35 commands total.\n"
          + "\n"
          + "Op types:\n"
          + "  fill   Fields: r,g,b                      Paints entire canvas. r=g=b=0 plus \"a\":0 = transparent clear.\n"
          + "  rect   Fields: x1,y1,x2,y2,r,g,b          Filled rectangle (inclusive coords).\n"
          + "  pixel  Fields: x,y,r,g,b                  Single pixel.\n"
          + "  circle Fields: cx,cy,radius(1-8),r,g,b    Filled circle.\n"
          + "\n"
          + "Layering order (always follow this):\n"
          + "  1. One background fill: transparent ({\"op\":\"fill\",\"r\":0,\"g\":0,\"b\":0,\"a\":0})\n"
          + "     OR solid dark ({\"op\":\"fill\",\"r\":14,\"g\":10,\"b\":20}) when item has a frame/border.\n"
          + "  2. Main silhouette: 2-4 rects or circles.\n"
          + "  3. Secondary detail: 1-3 rects/pixels for grip, gem, label, eye, stopper.\n"
          + "  4. Highlight pixels: 2-5 single bright pixels for sheen and pop.\n"
          + "\n"
          + "Rarity accent colours (use for brightest highlights):\n"
          + "  common=grey(160,160,160)  uncommon=green(80,190,90)  rare=cyan(60,170,220)\n"
          + "  epic=purple(190,80,220)   legendary=gold(240,180,30)\n"
          + "\n"
          + "=== CONCRETE SPRITE EXAMPLES ===\n"
          + "\n"
          + "SWORD / DAGGER — tall vertical shape:\n"
          + "  {\"op\":\"fill\",\"r\":0,\"g\":0,\"b\":0,\"a\":0}\n"
          + "  {\"op\":\"rect\",\"x1\":7,\"y1\":1,\"x2\":9,\"y2\":11,\"r\":190,\"g\":190,\"b\":200}   <- blade\n"
          + "  {\"op\":\"rect\",\"x1\":4,\"y1\":9,\"x2\":12,\"y2\":10,\"r\":140,\"g\":100,\"b\":50}   <- guard\n"
          + "  {\"op\":\"rect\",\"x1\":7,\"y1\":11,\"x2\":9,\"y2\":14,\"r\":90,\"g\":55,\"b\":25}    <- grip\n"
          + "  {\"op\":\"pixel\",\"x\":8,\"y\":1,\"r\":255,\"g\":255,\"b\":255}                    <- tip shine\n"
          + "  {\"op\":\"pixel\",\"x\":7,\"y\":5,\"r\":220,\"g\":220,\"b\":230}                    <- blade sheen\n"
          + "\n"
          + "BOW — arc traced with rects and pixels:\n"
          + "  {\"op\":\"fill\",\"r\":0,\"g\":0,\"b\":0,\"a\":0}\n"
          + "  {\"op\":\"rect\",\"x1\":5,\"y1\":2,\"x2\":6,\"y2\":3,\"r\":140,\"g\":90,\"b\":40}    <- upper limb\n"
          + "  {\"op\":\"rect\",\"x1\":4,\"y1\":4,\"x2\":5,\"y2\":9,\"r\":140,\"g\":90,\"b\":40}    <- limb middle\n"
          + "  {\"op\":\"rect\",\"x1\":5,\"y1\":11,\"x2\":6,\"y2\":13,\"r\":140,\"g\":90,\"b\":40}  <- lower limb\n"
          + "  {\"op\":\"pixel\",\"x\":9,\"y\":2,\"r\":220,\"g\":220,\"b\":200}                   <- string top\n"
          + "  {\"op\":\"pixel\",\"x\":9,\"y\":7,\"r\":220,\"g\":220,\"b\":200}                   <- string mid\n"
          + "  {\"op\":\"pixel\",\"x\":9,\"y\":12,\"r\":220,\"g\":220,\"b\":200}                  <- string bot\n"
          + "  {\"op\":\"pixel\",\"x\":8,\"y\":7,\"r\":255,\"g\":255,\"b\":200}                   <- nock\n"
          + "\n"
          + "POTION / BOTTLE:\n"
          + "  {\"op\":\"fill\",\"r\":0,\"g\":0,\"b\":0,\"a\":0}\n"
          + "  {\"op\":\"circle\",\"cx\":8,\"cy\":10,\"radius\":4,\"r\":200,\"g\":60,\"b\":60}      <- liquid body\n"
          + "  {\"op\":\"rect\",\"x1\":7,\"y1\":5,\"x2\":9,\"y2\":7,\"r\":150,\"g\":210,\"b\":200}  <- neck\n"
          + "  {\"op\":\"rect\",\"x1\":6,\"y1\":4,\"x2\":10,\"y2\":5,\"r\":160,\"g\":120,\"b\":60}  <- cork\n"
          + "  {\"op\":\"pixel\",\"x\":6,\"y\":9,\"r\":255,\"g\":255,\"b\":255}                   <- body shine\n"
          + "  {\"op\":\"pixel\",\"x\":10,\"y\":12,\"r\":255,\"g\":160,\"b\":160}                  <- lower shine\n"
          + "\n"
          + "FOOD / APPLE — round:\n"
          + "  {\"op\":\"fill\",\"r\":0,\"g\":0,\"b\":0,\"a\":0}\n"
          + "  {\"op\":\"circle\",\"cx\":8,\"cy\":9,\"radius\":5,\"r\":200,\"g\":50,\"b\":50}       <- body\n"
          + "  {\"op\":\"rect\",\"x1\":8,\"y1\":3,\"x2\":9,\"y2\":5,\"r\":100,\"g\":70,\"b\":30}    <- stem\n"
          + "  {\"op\":\"rect\",\"x1\":5,\"y1\":4,\"x2\":7,\"y2\":5,\"r\":60,\"g\":160,\"b\":60}    <- leaf\n"
          + "  {\"op\":\"pixel\",\"x\":6,\"y\":7,\"r\":255,\"g\":200,\"b\":200}                   <- shine\n"
          + "\n"
          + "SPAWN EGG — two-tone split circle:\n"
          + "  {\"op\":\"fill\",\"r\":14,\"g\":10,\"b\":20}\n"
          + "  {\"op\":\"rect\",\"x1\":3,\"y1\":3,\"x2\":13,\"y2\":8,\"r\":200,\"g\":60,\"b\":60}   <- top half (colour A)\n"
          + "  {\"op\":\"rect\",\"x1\":3,\"y1\":8,\"x2\":13,\"y2\":13,\"r\":60,\"g\":180,\"b\":80}  <- bottom half (colour B)\n"
          + "  {\"op\":\"circle\",\"cx\":8,\"cy\":7,\"radius\":2,\"r\":255,\"g\":255,\"b\":255}      <- white spot\n"
          + "  {\"op\":\"pixel\",\"x\":6,\"y\":5,\"r\":255,\"g\":255,\"b\":255}                   <- shine\n"
          + "\n"
          + "THROWABLE / BOMB — round with fuse:\n"
          + "  {\"op\":\"fill\",\"r\":0,\"g\":0,\"b\":0,\"a\":0}\n"
          + "  {\"op\":\"circle\",\"cx\":8,\"cy\":9,\"radius\":5,\"r\":55,\"g\":55,\"b\":60}        <- dark iron body\n"
          + "  {\"op\":\"rect\",\"x1\":8,\"y1\":3,\"x2\":9,\"y2\":5,\"r\":160,\"g\":130,\"b\":50}   <- fuse\n"
          + "  {\"op\":\"pixel\",\"x\":8,\"y\":3,\"r\":255,\"g\":220,\"b\":80}                    <- fuse spark\n"
          + "  {\"op\":\"pixel\",\"x\":6,\"y\":7,\"r\":180,\"g\":180,\"b\":190}                   <- body shine\n"
          + "  {\"op\":\"pixel\",\"x\":10,\"y\":11,\"r\":80,\"g\":80,\"b\":90}                    <- shadow\n"
          + "\n"
          + "TOTEM — tall face on dark bg:\n"
          + "  {\"op\":\"fill\",\"r\":14,\"g\":10,\"b\":20}\n"
          + "  {\"op\":\"rect\",\"x1\":4,\"y1\":2,\"x2\":12,\"y2\":14,\"r\":220,\"g\":185,\"b\":80}  <- gold body\n"
          + "  {\"op\":\"rect\",\"x1\":5,\"y1\":5,\"x2\":7,\"y2\":7,\"r\":30,\"g\":20,\"b\":10}     <- left eye\n"
          + "  {\"op\":\"rect\",\"x1\":9,\"y1\":5,\"x2\":11,\"y2\":7,\"r\":30,\"g\":20,\"b\":10}    <- right eye\n"
          + "  {\"op\":\"rect\",\"x1\":5,\"y1\":10,\"x2\":11,\"y2\":11,\"r\":30,\"g\":20,\"b\":10}  <- mouth\n"
          + "  {\"op\":\"pixel\",\"x\":8,\"y\":3,\"r\":255,\"g\":240,\"b\":150}                   <- top shine\n"
          + "\n"
          + "KEY:\n"
          + "  {\"op\":\"fill\",\"r\":0,\"g\":0,\"b\":0,\"a\":0}\n"
          + "  {\"op\":\"circle\",\"cx\":5,\"cy\":5,\"radius\":3,\"r\":200,\"g\":170,\"b\":50}      <- bow ring\n"
          + "  {\"op\":\"circle\",\"cx\":5,\"cy\":5,\"radius\":1,\"r\":0,\"g\":0,\"b\":0}           <- hole (use bg)\n"
          + "  {\"op\":\"rect\",\"x1\":7,\"y1\":5,\"x2\":13,\"y2\":6,\"r\":190,\"g\":160,\"b\":45}  <- shaft\n"
          + "  {\"op\":\"rect\",\"x1\":10,\"y1\":6,\"x2\":11,\"y2\":8,\"r\":190,\"g\":160,\"b\":45} <- tooth 1\n"
          + "  {\"op\":\"rect\",\"x1\":12,\"y1\":6,\"x2\":13,\"y2\":7,\"r\":190,\"g\":160,\"b\":45} <- tooth 2\n"
          + "  {\"op\":\"pixel\",\"x\":4,\"y\":4,\"r\":255,\"g\":240,\"b\":140}                   <- shine\n"
          + "\n"
          + "ORB / GEM:\n"
          + "  {\"op\":\"fill\",\"r\":0,\"g\":0,\"b\":0,\"a\":0}\n"
          + "  {\"op\":\"circle\",\"cx\":8,\"cy\":8,\"radius\":6,\"r\":80,\"g\":40,\"b\":180}       <- body\n"
          + "  {\"op\":\"rect\",\"x1\":5,\"y1\":5,\"x2\":11,\"y2\":7,\"r\":120,\"g\":80,\"b\":220}  <- facet\n"
          + "  {\"op\":\"pixel\",\"x\":6,\"y\":6,\"r\":255,\"g\":255,\"b\":255}                   <- shine 1\n"
          + "  {\"op\":\"pixel\",\"x\":10,\"y\":10,\"r\":140,\"g\":100,\"b\":230}                  <- shine 2\n"
          + "\n"
          + "USE_ITEM — match name/description creatively:\n"
          + "  Jar → circle body + flat rect lid.\n"
          + "  Mask → wide short rect + pixel eyes.\n"
          + "  Horn → diagonal tapering rects (x1 shrinks each row).\n"
          + "  Coin → circle + 2 interior pixel markings.\n"
          + "  If in doubt, choose a concrete prop silhouette like key, jar, mask, tag, folded note, bell, or cracked tool.\n";
    }

    // ── Parse metadata response ───────────────────────────────────────────────

    private CreationResult parseCreationResult(String apiBody) {
        try {
            String content = OpenRouterClient.extractContent(apiBody);
            String jsonBody = JsonParsingUtils.extractFirstJsonObject(
                JsonParsingUtils.stripCodeFence(content != null ? content : apiBody));
            if (jsonBody == null) return CreationResult.error("Could not parse AI response");

            JsonObject object = JsonParser.parseString(jsonBody).getAsJsonObject();
            String name         = JsonParsingUtils.getString(object, "name",         "Unnamed Contraption");
            String description  = JsonParsingUtils.getString(object, "description",  "An enigmatic object of unknown power.");
            String rarity       = JsonParsingUtils.normalise(JsonParsingUtils.getString(object, "rarity",    "common"));
            String itemType     = JsonParsingUtils.normalise(JsonParsingUtils.getString(object, "item_type", "use_item"));
            String spritePrompt = JsonParsingUtils.getString(object, "sprite_prompt", fallbackSpritePrompt(name, itemType));
            List<String> effects = sanitiseEffects(JsonParsingUtils.getStringList(object, "effects"));
            String special      = sanitiseSpecial(JsonParsingUtils.getNullableString(object, "special"));
            String mobType      = sanitiseMobType(itemType, JsonParsingUtils.getNullableString(object, "mob_type"));
            String behaviorScript = JsonParsingUtils.getNullableString(object, "behavior_script");

            // Inline sprite_commands — wrap in {"commands":[...]} for SpriteCommandRenderer
            String inlineSpriteCommands = null;
            JsonElement spriteEl = object.get("sprite_commands");
            if (spriteEl != null && spriteEl.isJsonArray()) {
                JsonArray arr = spriteEl.getAsJsonArray();
                if (!arr.isEmpty()) {
                    JsonObject wrapper = new JsonObject();
                    wrapper.add("commands", arr);
                    inlineSpriteCommands = wrapper.toString();
                }
            }

            if (!VALID_RARITIES.contains(rarity))   rarity   = "common";
            if (!VALID_ITEM_TYPES.contains(itemType)) itemType = "use_item";

            int maxEffects = switch (rarity) { case "epic" -> 3; case "legendary" -> 4; default -> 2; };
            int minEffects = switch (rarity) { case "rare" -> 1; case "epic" -> 2; case "legendary" -> 3; default -> 0; };

            if (effects.size() > maxEffects) effects = effects.subList(0, maxEffects);
            if (effects.size() < minEffects) {
                List<String> filler = new ArrayList<>(effects);
                for (String c : List.of("luck","speed","jump_boost","night_vision","strength","regeneration")) {
                    if (!filler.contains(c)) filler.add(c);
                    if (filler.size() >= minEffects) break;
                }
                effects = List.copyOf(filler);
            }

            if (special == null && Set.of("rare","epic","legendary").contains(rarity))
                special = fallbackSpecial(name, !effects.isEmpty() ? effects.get(0) : "luck", itemType);

            if (behaviorScript == null || behaviorScript.isBlank())
                behaviorScript = buildFallbackScript(name, rarity, itemType, effects, special, mobType);

            return new CreationResult(name, description, spritePrompt, rarity, itemType,
                    effects, special, mobType, behaviorScript, inlineSpriteCommands, null);

        } catch (JsonSyntaxException | IllegalStateException e) {
            AlchemodInit.LOG.warn("[Creator] Failed to parse AI JSON: {}", e.getMessage());
            return CreationResult.error("Invalid AI JSON");
        }
    }

    // ── Sanitisation ─────────────────────────────────────────────────────────

    private List<String> sanitiseEffects(List<String> inputEffects) {
        List<String> clean = new ArrayList<>();
        for (String e : inputEffects) { String n = JsonParsingUtils.normalise(e); if (VALID_EFFECTS.contains(n) && !clean.contains(n)) clean.add(n); }
        return clean;
    }

    private String sanitiseSpecial(String value) { if (value==null) return null; String n=JsonParsingUtils.normalise(value); return VALID_SPECIALS.contains(n)?n:null; }
    private String sanitiseMobType(String itemType, String value) {
        if (!"spawn_egg".equals(itemType) && !"spawn_item".equals(itemType)) return null;
        String n=JsonParsingUtils.normalise(value);
        return VALID_MOBS.contains(n)?n:"bat";
    }

    // ── Apply result ──────────────────────────────────────────────────────────

    private void applyResult(CreationResult result, String inputIdA, String inputIdB) {
        aiPending = false;

        if (result.error() != null) { state = STATE_ERROR; progress = 0; markDirty(); return; }

        int uid = (result.name().hashCode() * 31
                ^ result.spritePrompt().hashCode()
                ^ result.itemType().hashCode()
                ^ (int) (System.currentTimeMillis() >>> 10))
                & 0x3FFFFFFF | 0x40000000;
        DynamicItemRegistry.CreatedItemMeta meta = new DynamicItemRegistry.CreatedItemMeta(
                result.name(), result.description(), uid, result.rarity(), result.itemType(),
                result.effects(), result.special(), result.mobType(), result.behaviorScript());

        DynamicItemRegistry.RuntimeItemResult runtimeItem = DynamicItemRegistry.tryRegisterRuntimeItem(meta);
        if (!runtimeItem.succeeded()) {
            AlchemodInit.LOG.error("[Creator] Runtime item injection failed; creation aborted: {}",
                    runtimeItem.error());
            state = STATE_ERROR;
            progress = 0;
            markDirty();
            return;
        }
        Item targetItem = runtimeItem.item();
        String runtimeItemId = runtimeItem.id().toString();

        items.get(SLOT_A).decrement(1);
        items.get(SLOT_B).decrement(1);
        if (items.get(SLOT_A).isEmpty()) items.set(SLOT_A, ItemStack.EMPTY);
        if (items.get(SLOT_B).isEmpty()) items.set(SLOT_B, ItemStack.EMPTY);

        ItemStack output = new ItemStack(targetItem);
        NbtCompound tag = new NbtCompound();
        tag.putString("creator_name",      result.name());
        tag.putString("creator_desc",      result.description());
        tag.putString("creator_sprite",    result.spritePrompt());
        tag.putString("creator_rarity",    result.rarity());
        tag.putString("creator_item_type", result.itemType());
        tag.putString("creator_effects",   String.join(",", result.effects()));
        tag.putString("creator_special",   result.special()  != null ? result.special()  : "");
        tag.putString("creator_mob_type",  result.mobType()  != null ? result.mobType()  : "");
        tag.putInt   ("creator_slot",      uid);
        tag.putInt   ("charges",           DynamicItemRegistry.CreatedItemMeta.startingChargesForRarity(result.rarity()));
        tag.putString("creator_input_a",   inputIdA);
        tag.putString("creator_input_b",   inputIdB);
        tag.putString("creator_runtime_item", runtimeItemId);
        tag.putString("creator_injection_mode", "runtime_registry");
        if (result.behaviorScript() != null && !result.behaviorScript().isBlank())
            tag.putString("creator_script", result.behaviorScript());
        if (result.spriteCommands() != null && !result.spriteCommands().isBlank())
            tag.putString("creator_sprite_commands", result.spriteCommands());
        output.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));

        items.set(SLOT_OUTPUT, output);
        lastCreatedSlot = uid;
        progress = MAX_PROGRESS;
        state = STATE_READY;

        AlchemodInit.LOG.info("[Creator] Created '{}' (uid=0x{}, sprite={}, rarity={}, type={}, runtime={})",
                result.name(), Integer.toHexString(uid),
                result.spriteCommands() != null ? "inline" : "none",
                result.rarity(), result.itemType(), runtimeItemId);
        markDirty();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void onOutputTaken() { state = STATE_IDLE; progress = 0; markDirty(); }
    public PropertyDelegate getDelegate()      { return delegate; }
    public int getMaxProgress()                { return MAX_PROGRESS; }
    public DefaultedList<ItemStack> getItems() { return items; }

    @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
        return new CreatorScreenHandler(syncId, inventory, this, delegate, getPos());
    }
    @Override public Text getDisplayName() { return Text.translatable("block.alchemod.item_creator"); }

    @Override public int size()  { return 3; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { ItemStack r=Inventories.splitStack(items,slot,amount); if(!r.isEmpty()) markDirty(); return r; }
    @Override public ItemStack removeStack(int slot) { return Inventories.removeStack(items, slot); }
    @Override public void setStack(int slot, ItemStack stack) { items.set(slot, stack); markDirty(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return Inventory.canPlayerUse(this, player); }
    @Override public void clear() { items.clear(); }

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
        state           = nbt.getInt("State");
        progress        = nbt.getInt("Progress");
        lastCreatedSlot = nbt.getInt("LastSlot");
        if (state == STATE_PROCESSING) { state = STATE_IDLE; progress = 0; }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null)
            world.updateListeners(pos, getCachedState(), getCachedState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
    }

    // ── Fallback helpers ──────────────────────────────────────────────────────

    private static String fallbackSpritePrompt(String name, String itemType) {
        int seed = stableSeed(name, itemType, "sprite");
        String material = pick(new String[] {
                "iron-bound", "wax-sealed", "cracked glass", "stitched leather",
                "burnt copper", "painted bone", "folded paper", "bottle-green"
        }, seed);
        String noun = switch (itemType) {
            case "bow" -> pick(new String[] {"shortbow", "crooked bow", "stringed charm"}, seed >> 3);
            case "weapon", "sword" -> pick(new String[] {"notched blade", "hook knife", "thin spear"}, seed >> 3);
            case "tool" -> pick(new String[] {"hand tool", "bent hammer", "small pick"}, seed >> 3);
            case "potion", "food" -> pick(new String[] {"sealed bottle", "bitten snack", "tiny flask"}, seed >> 3);
            case "wand" -> pick(new String[] {"forked wand", "candle stick", "marked rod"}, seed >> 3);
            case "charm", "totem" -> pick(new String[] {"hanging tag", "tiny mask", "square idol"}, seed >> 3);
            case "scroll" -> pick(new String[] {"folded note", "bound receipt", "rolled map"}, seed >> 3);
            case "throwable" -> pick(new String[] {"fused bomb", "glass pellet", "wrapped stone"}, seed >> 3);
            case "spawn_item", "spawn_egg" -> pick(new String[] {"speckled egg", "warm shell", "marked capsule"}, seed >> 3);
            case "block" -> pick(new String[] {"wired block", "trap cube", "pressure tile", "buried charge"}, seed >> 3);
            default -> pick(new String[] {"jar", "key", "bell", "mask", "label", "coin"}, seed >> 3);
        };
        return "16x16 pixel art " + material + " " + noun + " for " + name;
    }

    private static String fallbackSpecial(String name, String effect, String itemType) {
        int seed = stableSeed(name, effect, itemType, "special");
        String effectChoice = switch (effect) {
            case "strength"        -> "knockback";
            case "speed"           -> "void_step";
            case "regeneration"    -> "heal_aura";
            case "jump_boost"      -> "launch";
            case "fire_resistance" -> "ignite";
            case "night_vision"    -> "phase";
            case "water_breathing" -> "freeze";
            case "haste"           -> "lightning";
            default                -> "drain";
        };
        if ((seed & 3) == 0) return effectChoice;

        String[] choices = switch (itemType) {
            case "bow", "weapon", "sword" -> new String[] {"ignite", "knockback", "lightning", "drain"};
            case "throwable" -> new String[] {"freeze", "ignite", "launch", "lightning"};
            case "scroll" -> new String[] {"phase", "lightning", "void_step", "heal_aura"};
            case "spawn_egg", "spawn_item" -> new String[] {"phase", "knockback", "heal_aura", "void_step"};
            case "wand", "artifact", "use_item" -> new String[] {"drain", "phase", "void_step", "lightning", "launch"};
            case "charm", "potion", "food", "totem" -> new String[] {"heal_aura", "phase", "freeze", "void_step"};
            case "tool" -> new String[] {"knockback", "launch", "freeze", "drain"};
            case "block" -> new String[] {"ignite", "knockback", "freeze", "launch"};
            default -> new String[] {"drain", "phase", "launch", "freeze", "void_step"};
        };
        return pick(choices, seed >> 2);
    }

    private static String buildFallbackScript(String name, String rarity, String itemType,
            List<String> effects, String special, String mobType) {
        int seed = stableSeed(name, rarity, itemType, String.join(",", effects),
                special != null ? special : "", mobType != null ? mobType : "");
        int flavor = Math.floorMod(seed, 5);
        int dur = switch (rarity) { case "uncommon"->240; case "rare"->320; case "epic"->420; case "legendary"->520; default->180; };
        int amp = switch (rarity) { case "rare"->1; case "epic","legendary"->2; default->0; };
        boolean strong = Set.of("epic","legendary").contains(rarity);
        StringBuilder b = new StringBuilder("function onUse(player, world) {\n");
        b.append("  var look = player.getLookDir();\n");
        for (String eff : effects)
            b.append("  player.addEffect('").append(eff).append("', ").append(dur).append(", ").append(amp).append(");\n");

        appendTypeFallback(b, itemType, rarity, mobType, dur, amp, strong, flavor);
        if (special != null) appendSpecialFallback(b, special, flavor);

        b.append("  player.sendMessage('").append(escape(fallbackUseMessage(name, itemType, special, flavor))).append("');\n");
        b.append("}\n");
        return b.toString();
    }

    private static void appendTypeFallback(StringBuilder b, String itemType, String rarity, String mobType,
            int dur, int amp, boolean strong, int flavor) {
        switch (itemType) {
            case "bow", "weapon", "sword" -> {
                switch (flavor) {
                    case 0 -> b.append("  world.createExplosion(player.getX()+look[0]*7, player.getY()+1.2+look[1]*4, player.getZ()+look[2]*7, ")
                            .append(strong ? "3.0" : "2.0").append(", false);\n")
                            .append("  world.playSound('entity.firework_rocket.launch', 0.9, 0.8);\n");
                    case 1 -> b.append("  var t = nearbyEntities(5); for(var i=0;i<t.length;i++){t[i].knockbackFrom(2.4);}\n")
                            .append("  world.playSound('entity.player.attack.sweep', 0.8, 0.75);\n");
                    case 2 -> b.append("  var t = nearbyEntities(4); for(var i=0;i<t.length;i++){t[i].setOnFire(4);}\n")
                            .append("  player.addEffect('strength', ").append(dur / 2).append(", ").append(Math.max(0, amp)).append(");\n");
                    case 3 -> b.append("  world.spawnLightning(player.getX()+look[0]*6, player.getY()+look[1]*3, player.getZ()+look[2]*6);\n")
                            .append("  world.playSound('item.trident.thunder', 0.7, 1.4);\n");
                    default -> b.append("  player.addVelocity(look[0]*0.7, 0.35, look[2]*0.7);\n")
                            .append("  world.playSound('entity.arrow.shoot', 0.9, 1.6);\n");
                }
            }
            case "throwable" -> {
                switch (flavor) {
                    case 0 -> b.append("  world.createExplosion(player.getX()+look[0]*5, player.getY()+1+look[1]*3, player.getZ()+look[2]*5, 1.6, false);\n")
                            .append("  world.playSound('entity.snowball.throw', 0.9, 0.9);\n");
                    case 1 -> b.append("  var t = nearbyEntities(6); for(var i=0;i<t.length;i++){t[i].addEffect('slowness',160,1);}\n")
                            .append("  world.playSound('block.glass.break', 0.7, 1.5);\n");
                    case 2 -> b.append("  world.spawnLightning(player.getX()+look[0]*5, player.getY()+look[1]*2, player.getZ()+look[2]*5);\n")
                            .append("  world.playSound('entity.firework_rocket.blast', 0.8, 1.2);\n");
                    case 3 -> b.append("  var t = nearbyEntities(5); for(var i=0;i<t.length;i++){t[i].damage(3); t[i].setOnFire(3);}\n")
                            .append("  world.playSound('entity.blaze.shoot', 0.6, 1.7);\n");
                    default -> b.append("  player.addVelocity(look[0]*-0.25, 0.35, look[2]*-0.25);\n")
                            .append("  world.playSound('entity.egg.throw', 0.8, 0.7);\n");
                }
            }
            case "scroll" -> {
                switch (flavor) {
                    case 0 -> b.append("  world.spawnLightning(player.getX()+look[0]*8, player.getY()+look[1]*4, player.getZ()+look[2]*8);\n");
                    case 1 -> b.append("  player.addEffect('invisibility', 120, 0);\n  player.addEffect('night_vision', 220, 0);\n");
                    case 2 -> b.append("  player.heal(").append(strong ? "7" : "4").append(");\n  player.addEffect('regeneration', 120, 0);\n");
                    case 3 -> b.append("  var t = nearbyEntities(7); for(var i=0;i<t.length;i++){t[i].addEffect('slowness',180,2);}\n");
                    default -> b.append("  player.addVelocity(0, 0.9, 0);\n  player.addEffect('slow_falling', 180, 0);\n");
                }
                b.append("  world.playSound('item.book.page_turn', 0.8, ").append(flavor == 0 ? "1.3" : "0.9").append(");\n");
            }
            case "wand", "artifact", "use_item" -> {
                switch (flavor) {
                    case 0 -> b.append("  player.addVelocity(look[0]*0.45, 0.25, look[2]*0.45);\n");
                    case 1 -> b.append("  var t = nearbyEntities(6); for(var i=0;i<t.length;i++){t[i].damage(2);}\n  player.heal(2);\n");
                    case 2 -> b.append("  player.addEffect('luck', ").append(dur).append(", 0);\n");
                    case 3 -> b.append("  var t = nearbyEntities(5); for(var i=0;i<t.length;i++){t[i].knockbackFrom(1.8);}\n");
                    default -> b.append("  player.addEffect('slow_falling', 160, 0);\n  player.addVelocity(0, 0.45, 0);\n");
                }
                b.append("  world.playSound('block.amethyst_block.chime', 0.8, ").append(1.0 + flavor * 0.08).append(");\n");
            }
            case "tool" -> b.append("  player.addEffect('haste', ").append(dur).append(", ").append(Math.max(1, amp)).append(");\n")
                    .append(flavor % 2 == 0
                            ? "  world.playSound('block.anvil.use', 0.6, 1.4);\n"
                            : "  world.playSound('block.grindstone.use', 0.7, 0.9);\n");
            case "potion" -> b.append("  player.heal(").append(strong ? "8" : "4").append(");\n")
                    .append(flavor % 2 == 0
                            ? "  player.addEffect('absorption', 180, 0);\n"
                            : "  player.addEffect('luck', 240, 0);\n")
                    .append("  world.playSound('entity.generic.drink', 0.7, ").append(flavor % 2 == 0 ? "1.2" : "0.75").append(");\n");
            case "charm" -> b.append("  player.addEffect('resistance', ").append(dur).append(", ").append(Math.max(0, amp)).append(");\n")
                    .append(flavor % 2 == 0
                            ? "  player.heal(2);\n"
                            : "  player.addEffect('slow_falling', 180, 0);\n")
                    .append("  world.playSound('block.enchantment_table.use', 0.7, 1.1);\n");
            case "spawn_egg", "spawn_item" -> {
                if (mobType != null && !mobType.isBlank()) {
                    b.append("  world.spawnMob('").append(escape(mobType)).append("', player.getX()+look[0]*2, player.getY(), player.getZ()+look[2]*2);\n")
                            .append(flavor % 2 == 0
                                    ? "  world.playSound('entity.evoker.prepare_summon', 0.8, 1.1);\n"
                                    : "  world.playSound('block.scaffolding.break', 0.8, 0.8);\n");
                }
            }
            case "food" -> b.append("  player.heal(").append(strong ? "6" : "3").append(");\n")
                    .append(flavor % 2 == 0
                            ? "  player.addEffect('speed', 120, 0);\n"
                            : "  player.addEffect('luck', 180, 0);\n")
                    .append("  world.playSound('entity.generic.eat', 0.7, 1.4);\n");
            case "totem" -> b.append("  player.addEffect('absorption', 240, ").append(strong ? "1" : "0").append(");\n")
                    .append(flavor % 2 == 0
                            ? "  player.heal(4);\n"
                            : "  player.addEffect('resistance', 160, 0);\n")
                    .append("  world.playSound('item.totem.use', 0.7, 1.1);\n");
            case "block" -> {
                String placed = flavor % 2 == 0 ? "minecraft:iron_block" : "minecraft:stone_pressure_plate";
                b.append("  var x = player.getX()+look[0]*2;\n")
                        .append("  var y = player.getY()-1;\n")
                        .append("  var z = player.getZ()+look[2]*2;\n")
                        .append("  world.setBlock(x, y, z, '").append(placed).append("');\n")
                        .append(flavor == 0
                                ? "  world.createExplosion(x, y+1, z, 1.5, false);\n"
                                : "  var t = nearbyEntities(4); for(var i=0;i<t.length;i++){t[i].addEffect('slowness',100,1);}\n")
                        .append("  world.playSound('block.stone.place', 0.8, 0.8);\n");
            }
            default -> b.append("  world.playSound('block.note_block.chime', 0.7, 1.0);\n");
        }
    }

    private static void appendSpecialFallback(StringBuilder b, String special, int flavor) {
        switch (special) {
            case "ignite" -> b.append("  var s = nearbyEntities(").append(flavor % 2 == 0 ? "6" : "4")
                    .append("); for(var i=0;i<s.length;i++){s[i].setOnFire(").append(4 + flavor).append(");}\n");
            case "knockback" -> b.append("  var s = nearbyEntities(6); for(var i=0;i<s.length;i++){s[i].knockbackFrom(")
                    .append(flavor % 2 == 0 ? "3" : "1.8").append(");}\n");
            case "heal_aura" -> b.append("  player.heal(").append(4 + flavor).append(");\n");
            case "launch" -> b.append("  player.addVelocity(0, ").append(flavor % 2 == 0 ? "1.2" : "0.75").append(", 0);\n");
            case "freeze" -> b.append("  var s = nearbyEntities(7); for(var i=0;i<s.length;i++){s[i].addEffect('slowness',")
                    .append(140 + flavor * 20).append(",2);}\n");
            case "drain" -> b.append("  var s = nearbyEntities(7); for(var i=0;i<s.length;i++){s[i].damage(")
                    .append(3 + flavor).append(");}\n  player.heal(4);\n");
            case "phase" -> b.append("  player.addEffect('invisibility', ").append(120 + flavor * 20).append(", 0);\n");
            case "lightning" -> b.append("  var s = nearbyEntities(8); for(var i=0;i<s.length;i++){world.spawnLightning(s[i].getX(),s[i].getY(),s[i].getZ());}\n");
            case "void_step" -> b.append("  player.addEffect('slow_falling', 200, 0);\n  player.addVelocity(0, 0.8, 0);\n");
        }
    }

    private static String fallbackUseMessage(String name, String itemType, String special, int flavor) {
        int seed = stableSeed(name, itemType, special != null ? special : "", Integer.toString(flavor));
        String verb = switch (itemType) {
            case "potion" -> pick(new String[] {"fizzes through the cork", "turns warm in your hand", "leaves a metallic aftertaste"}, seed);
            case "food" -> pick(new String[] {"crumbles, then bites back", "tastes briefly impossible", "snaps like dry sugar"}, seed);
            case "scroll" -> pick(new String[] {"rewrites one line of itself", "folds into a sharper shape", "whispers from the crease"}, seed);
            case "wand" -> pick(new String[] {"clicks like a loose tooth", "draws a crooked spark", "points somewhere unhelpful"}, seed);
            case "throwable" -> pick(new String[] {"ticks twice and commits", "bursts out of its wrapping", "spits a bright little warning"}, seed);
            case "spawn_egg", "spawn_item" -> pick(new String[] {"taps from the inside", "rolls toward the nearest shadow", "answers with a tiny heartbeat"}, seed);
            case "charm", "totem" -> pick(new String[] {"tightens its cord", "winks with painted eyes", "pulls the air into a knot"}, seed);
            case "bow", "weapon", "sword" -> pick(new String[] {"rings down your arm", "leaves a hot notch in the air", "hums like it remembers"}, seed);
            case "tool" -> pick(new String[] {"measures the world badly", "shaves a spark off the air", "finds a hidden angle"}, seed);
            case "block" -> pick(new String[] {"locks into the ground", "arms itself with a heavy click", "marks the floor as dangerous"}, seed);
            default -> pick(new String[] {"does one very specific wrong thing", "makes the room feel labelled", "clicks and refuses to explain"}, seed);
        };
        return name + " " + verb + ".";
    }

    private static String pick(String[] options, int seed) {
        return options[Math.floorMod(seed, options.length)];
    }

    private static int stableSeed(String... parts) {
        int hash = 0x811C9DC5;
        for (String part : parts) {
            String value = part == null ? "" : part;
            for (int i = 0; i < value.length(); i++) {
                hash ^= value.charAt(i);
                hash *= 0x01000193;
            }
            hash ^= '|';
            hash *= 0x01000193;
        }
        return hash;
    }

    private static String escape(String v) { return v.replace("\\","\\\\").replace("'","\\'"); }

    // ── CreationResult record ─────────────────────────────────────────────────

    record CreationResult(
            String name, String description, String spritePrompt,
            String rarity, String itemType, List<String> effects,
            String special, String mobType, String behaviorScript,
            String spriteCommands, String error
    ) {
        static CreationResult error(String message) {
            return new CreationResult(null,null,null,null,"use_item",List.of(),null,null,null,null,message);
        }
    }
}
