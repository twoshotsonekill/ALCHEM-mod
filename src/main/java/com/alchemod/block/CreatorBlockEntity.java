package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.ai.OpenRouterClient;
import com.alchemod.ai.SpriteToolClient;
import com.alchemod.creator.DynamicItem;
import com.alchemod.creator.DynamicItemRegistry;
import com.alchemod.item.OddityItem;
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
        OpenRouterClient.ChatResult result = OpenRouterClient.chat(
                AlchemodInit.OPENROUTER_KEY,
                new OpenRouterClient.ChatRequest(
                        AlchemodInit.CONFIG.creatorModel(),
                        AlchemodInit.CONFIG.creatorMaxTokensScripted(),
                        AlchemodInit.CONFIG.creatorTimeoutSeconds(),
                        buildSystemPrompt(),
                        "Create a new magical item by combining: " + itemA + " + " + itemB));

        if (result.isError()) {
            AlchemodInit.LOG.error("[Creator] API error: {}", result.error());
            return CreationResult.error(result.error());
        }

        CreationResult metadata = parseCreationResult(
                result.rawBody() != null ? result.rawBody() : result.content());
        if (metadata.error() != null) return metadata;

        // Use inline sprite_commands when present; fall back to SpriteToolClient.
        String spriteCommands = metadata.spriteCommands();
        if (spriteCommands == null || spriteCommands.isBlank()) {
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

    // ── System prompt ─────────────────────────────────────────────────────────

    private String buildSystemPrompt() {
        return
            "You are a creative Minecraft alchemist inventing imaginative new items.\n"
          + "Return ONLY a single valid JSON object — no markdown, no preamble, no trailing text.\n"
          + "\n"
          + "Required fields:\n"
          + "{\n"
          + "  \"name\": \"Item Name\",\n"
          + "  \"description\": \"One sentence flavour text.\",\n"
          + "  \"sprite_prompt\": \"8-15 word pixel art description of a concrete object\",\n"
          + "  \"rarity\": \"common|uncommon|rare|epic|legendary\",\n"
          + "  \"item_type\": \"use_item|bow|spawn_egg|food|sword|totem|throwable\",\n"
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
          + "Valid mob_type (spawn_egg only): zombie, skeleton, creeper, spider, enderman, blaze, witch, phantom, slime, magma_cube, hoglin, strider, cow, pig, sheep, chicken, bat, wolf, rabbit, fox, bee, axolotl, parrot\n"
          + "mob_type must be null unless item_type is spawn_egg. special must be null if no active ability.\n"
          + "common/uncommon: 0-2 effects; rare: 1-2; epic: 2-3; legendary: 3-4.\n"
          + "rare/epic/legendary should usually have a special.\n"
          + "Avoid generic glowing gems. Prefer weird stuff: junk gadgets, cursed snacks, unstable bows, masks, jars, bombs, puppets, keys, horns, fake relics.\n"
          + "\n"
          + "behavior_script defines: function onUse(player, world) { ... }\n"
          + "API: player.heal/damage/addEffect/removeEffect/addVelocity/teleport/setOnFire/extinguish/sendMessage/getX/getY/getZ/getHealth/getLookDir/isSneaking/isInWater\n"
          + "     world.createExplosion/spawnLightning/spawnMob/playSound/setBlock/isDay/isRaining/getTime\n"
          + "     nearbyEntities(radius) array with .damage/.heal/.knockbackFrom/.setVelocity/.addEffect/.setOnFire/.extinguish/.getHealth/.getX/.getY/.getZ/.getType/.isPlayer/.isAlive\n"
          + "Keep scripts under 20 lines. Always call player.sendMessage.\n"
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
          + "  If in doubt, use the ORB/GEM template with rarity accent colour.\n";
    }

    // ── Parse metadata response ───────────────────────────────────────────────

    private CreationResult parseCreationResult(String apiBody) {
        try {
            String content = OpenRouterClient.extractContent(apiBody);
            String jsonBody = JsonParsingUtils.extractFirstJsonObject(
                JsonParsingUtils.stripCodeFence(content != null ? content : apiBody));
            if (jsonBody == null) return CreationResult.error("Could not parse AI response");

            JsonObject object = JsonParser.parseString(jsonBody).getAsJsonObject();
            String name         = JsonParsingUtils.getString(object, "name",         "Mysterious Relic");
            String description  = JsonParsingUtils.getString(object, "description",  "An enigmatic object of unknown power.");
            String spritePrompt = JsonParsingUtils.getString(object, "sprite_prompt", name + " magical glowing artifact");
            String rarity       = JsonParsingUtils.normalise(JsonParsingUtils.getString(object, "rarity",    "common"));
            String itemType     = JsonParsingUtils.normalise(JsonParsingUtils.getString(object, "item_type", "use_item"));
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
                special = fallbackSpecial(!effects.isEmpty() ? effects.get(0) : "luck", itemType);

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
    private String sanitiseMobType(String itemType, String value) { if (!"spawn_egg".equals(itemType)) return null; String n=JsonParsingUtils.normalise(value); return VALID_MOBS.contains(n)?n:"bat"; }

    // ── Apply result ──────────────────────────────────────────────────────────

    private void applyResult(CreationResult result, String inputIdA, String inputIdB) {
        aiPending = false;

        if (result.error() != null) { state = STATE_ERROR; progress = 0; markDirty(); return; }

        Item targetItem = DynamicItemRegistry.ODDITY_ITEM;
        int uid;

        if (targetItem != null) {
            uid = (result.name().hashCode() * 31
                    ^ result.spritePrompt().hashCode()
                    ^ (int) (System.currentTimeMillis() >>> 10))
                    & 0x3FFFFFFF | 0x40000000;
        } else {
            DynamicItem dynamicItem = DynamicItemRegistry.claimSlot();
            if (dynamicItem == null) {
                AlchemodInit.LOG.error("[Creator] Both OddityItem and legacy slot pool are unavailable.");
                state = STATE_ERROR; markDirty(); return;
            }
            uid = dynamicItem.getSlotIndex();
            targetItem = dynamicItem;
            DynamicItemRegistry.updateSlotMeta(uid, new DynamicItemRegistry.CreatedItemMeta(
                    result.name(), result.description(), uid, result.rarity(), result.itemType(),
                    result.effects(), result.special(), result.mobType(), result.behaviorScript()));
        }

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
        if (result.behaviorScript() != null && !result.behaviorScript().isBlank())
            tag.putString("creator_script", result.behaviorScript());
        if (result.spriteCommands() != null && !result.spriteCommands().isBlank())
            tag.putString("creator_sprite_commands", result.spriteCommands());
        output.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));

        items.set(SLOT_OUTPUT, output);
        lastCreatedSlot = uid;
        progress = MAX_PROGRESS;
        state = STATE_READY;

        AlchemodInit.LOG.info("[Creator] Created '{}' (uid=0x{}, sprite={}, rarity={}, type={})",
                result.name(), Integer.toHexString(uid),
                result.spriteCommands() != null ? "inline" : "none",
                result.rarity(), result.itemType());
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

    private static String fallbackSpecial(String effect, String itemType) {
        if ("bow".equals(itemType))       return "ignite";
        if ("throwable".equals(itemType)) return "lightning";
        if ("spawn_egg".equals(itemType)) return "phase";
        return switch (effect) {
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
    }

    private static String buildFallbackScript(String name, String rarity, String itemType,
            List<String> effects, String special, String mobType) {
        int dur = switch (rarity) { case "uncommon"->240; case "rare"->320; case "epic"->420; case "legendary"->520; default->180; };
        int amp = switch (rarity) { case "rare"->1; case "epic","legendary"->2; default->0; };
        StringBuilder b = new StringBuilder("function onUse(player, world) {\n");
        for (String eff : effects)
            b.append("  player.addEffect('").append(eff).append("', ").append(dur).append(", ").append(amp).append(");\n");

        switch (itemType) {
            case "bow" -> b.append("  var look = player.getLookDir();\n")
                    .append("  world.createExplosion(player.getX()+look[0]*7, player.getY()+1.2+look[1]*4, player.getZ()+look[2]*7, ")
                    .append(Set.of("epic","legendary").contains(rarity) ? "3.0" : "2.0").append(", false);\n")
                    .append("  world.playSound('entity.firework_rocket.launch', 0.9, 0.8);\n");
            case "throwable" -> b.append("  var look = player.getLookDir();\n")
                    .append("  world.createExplosion(player.getX()+look[0]*5, player.getY()+1+look[1]*3, player.getZ()+look[2]*5, 1.6, false);\n")
                    .append("  world.playSound('entity.snowball.throw', 0.9, 0.9);\n");
            case "spawn_egg" -> { if (mobType!=null&&!mobType.isBlank())
                    b.append("  var look = player.getLookDir();\n")
                            .append("  world.spawnMob('").append(escape(mobType)).append("', player.getX()+look[0]*2, player.getY(), player.getZ()+look[2]*2);\n")
                            .append("  world.playSound('entity.evoker.prepare_summon', 0.8, 1.1);\n"); }
            case "food"  -> b.append("  player.heal(").append(Set.of("epic","legendary").contains(rarity)?"6":"3").append(");\n")
                    .append("  world.playSound('entity.generic.eat', 0.7, 1.4);\n");
            case "totem" -> b.append("  player.addEffect('absorption', 240, ").append(Set.of("epic","legendary").contains(rarity)?"1":"0").append(");\n")
                    .append("  world.playSound('item.totem.use', 0.7, 1.1);\n");
        }

        if (special != null) switch (special) {
            case "ignite"    -> b.append("  var t = nearbyEntities(6); for(var i=0;i<t.length;i++){t[i].setOnFire(6);}\n");
            case "knockback" -> b.append("  var t = nearbyEntities(6); for(var i=0;i<t.length;i++){t[i].knockbackFrom(3);}\n");
            case "heal_aura" -> b.append("  player.heal(6);\n");
            case "launch"    -> b.append("  player.addVelocity(0, 1.2, 0);\n");
            case "freeze"    -> b.append("  var t = nearbyEntities(7); for(var i=0;i<t.length;i++){t[i].addEffect('slowness',180,2);}\n");
            case "drain"     -> b.append("  var t = nearbyEntities(7); for(var i=0;i<t.length;i++){t[i].damage(5);}\n  player.heal(4);\n");
            case "phase"     -> b.append("  player.addEffect('invisibility', 160, 0);\n");
            case "lightning" -> b.append("  var t = nearbyEntities(8); for(var i=0;i<t.length;i++){world.spawnLightning(t[i].getX(),t[i].getY(),t[i].getZ());}\n");
            case "void_step" -> b.append("  player.addEffect('slow_falling', 200, 0);\n  player.addVelocity(0, 0.8, 0);\n");
        }

        if ("common".equals(rarity) && effects.isEmpty() && special==null && !"spawn_egg".equals(itemType))
            b.append("  player.sendMessage('").append(escape(name)).append(" squeaks and does very little.');\n");
        b.append("  player.sendMessage('").append(escape(name)).append(" does something strange.');\n");
        b.append("  world.playSound('block.beacon.activate', 0.7, 1.2);\n}\n");
        return b.toString();
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
