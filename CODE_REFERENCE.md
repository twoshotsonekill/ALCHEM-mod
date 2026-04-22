# Alchemod Code Reference

**Generated with Claude Opus 4**

## Important API Details

### ForgeBlockEntity - Alchemical Forge
Combines two input items using AI to produce a vanilla Minecraft item.

**AI Model:** `openai/gpt-4o-mini`
**API Endpoint:** `https://openrouter.ai/api/v1/chat/completions`
**System Prompt:** Creates thematic combinations of Minecraft items

### CreatorBlockEntity - Item Creator
Creates new dynamic items with AI-generated names, effects, and powers.

**AI Model:** `openai/gpt-4o-mini`
**API Endpoint:** `https://openrouter.ai/api/v1/chat/completions`
**Dynamic Items:** 64 slots (dynamic_item_0 through dynamic_item_63)

**Item Types:**
- `use_item` - instant right-click ability
- `bow` - hold to charge, fire projectile
- `spawn_egg` - summons magical creature
- `food` - hold right-click to eat
- `sword` - sweep nearby enemies
- `totem` - hold in off-hand for passive effects
- `throwable` - throw at target for area effects

**Rarities:** common, uncommon, rare, epic, legendary

**Effects:** speed, strength, regeneration, resistance, fire_resistance, night_vision, absorption, luck, haste, jump_boost, slow_falling, water_breathing

**Specials:** ignite, knockback, heal_aura, launch, freeze, drain, phase, lightning, void_step

---

## Slot Indices

| Block | SLOT_A | SLOT_B | SLOT_OUTPUT |
|-------|--------|--------|-------------|
| ForgeBlockEntity | 0 | 1 | 2 |
| CreatorBlockEntity | 0 | 1 | 2 |

## State Values

| State | Value |
|-------|-------|
| STATE_IDLE | 0 |
| STATE_PROCESSING | 1 |
| STATE_READY | 2 |
| STATE_ERROR | 3 |

---

## Forge AI Prompt
```
You are a Minecraft alchemist oracle. 
Given two Minecraft items, respond with EXACTLY ONE vanilla Minecraft item ID 
that thematically represents their combination. 
Format: namespace:item_name — example: minecraft:blaze_rod. 
No explanation. No punctuation. Just the item ID.
```

---

## Creator AI Prompt (Full System)
```
You are a creative Minecraft alchemist inventing wildly imaginative NEW items.
Given two input items, invent a magical synthesis that could be any kind of item.

=== ITEM TYPES ===
Choose item_type based purely on what the combination SUGGESTS. Be creative!

  use_item   – instant right-click ability (potions, wands, orbs)
  bow        – hold to charge, fire a magical projectile
  spawn_egg  – summons a magical creature when right-clicked on ground
  food       – hold right-click to eat and gain powerful effects
  sword      – right-click to sweep nearby enemies with magical damage
  totem      – hold in off-hand for persistent passive effects
  throwable  – throw at a target point for area explosion/effects

=== RARITY GUIDE ===
  common    – mundane pairing, one weak effect
  uncommon  – mildly interesting, two effects
  rare      – creative/thematic match, good effects + special ability
  epic      – powerful/lore-rich combination, multiple effects + strong special
  legendary – world-altering or iconic combination, maximum effects + overpowered special

=== VALID EFFECTS ===
speed, strength, regeneration, resistance, fire_resistance, night_vision,
absorption, luck, haste, jump_boost, slow_falling, water_breathing

Effects count by rarity: common=1, uncommon=2, rare=2, epic=3, legendary=4

=== VALID SPECIALS ===
ignite, knockback, heal_aura, launch, freeze, drain, phase, lightning, void_step
Required for rare/epic/legendary. Set to null for common. Optional for uncommon.

=== VALID MOBS (spawn_egg only) ===
zombie, skeleton, creeper, spider, enderman, blaze, witch,
phantom, slime, magma_cube, bat, cow, pig, sheep, chicken,
wolf, rabbit, fox, bee, axolotl, parrot

=== OUTPUT FORMAT ===
Respond with ONLY valid JSON, no markdown, no explanation:
{"name":"Item Name","description":"One sentence flavour.","sprite_prompt":"pixel art description 8-15 words","rarity":"rare","item_type":"bow","effects":["strength","haste"],"special":"ignite","mob_type":null}
```

---

## Item Creation Response Format
```json
{
  "name": "Blazing Blade",
  "description": "A sword wreathed in eternal flame.",
  "sprite_prompt": "glowing red blade with flame particles",
  "rarity": "epic",
  "item_type": "sword",
  "effects": ["strength", "haste"],
  "special": "ignite",
  "mob_type": null
}
```

---

## Custom NBT Tags on Created Items
```
creator_name      - Display name
creator_desc     - Description
creator_sprite   - Sprite prompt
creator_rarity   - Rarity level
creator_item_type - Item type
creator_effects  - Comma-separated effects list
creator_special  - Special ability
creator_mob_type - Mob type (spawn_egg only)
creator_slot     - Dynamic item slot index
charges          - Starting charges
```

---

## Texture Resources
- Block textures: `src/main/resources/assets/alchemod/textures/block/`
- Item textures: `src/main/resources/assets/alchemod/textures/item/`
- Dynamic items: `dynamic_item_0.png` through `dynamic_item_63.png`

---

## Build Command
```bash
./gradlew build
```

Output: `build/libs/alchem-mod-1.0.0.jar`

---

## GitHub
Repo: `https://github.com/twoshotsonekill/ALCHEM-mod`

---

## Known Fixes Applied

### Input Slot Lock (v3)
- `CreatorScreenHandler` uses `InputSlot` class
- `canInsert()` returns `!isProcessing()` to lock during crafting
- Prevents taking items from input slots while AI processes

### QuickMove Output Fix
- Both screen handlers detect output slot in `quickMove()`
- Calls `onOutputTaken()` callback when output taken
- Prevents stuck STATE_READY state