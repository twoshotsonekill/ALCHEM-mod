# Alchemod Project Goals

## 3 Custom Blocks for Minecraft 1.21.x

### 1. Alchemical Forge
Creates vanilla Minecraft items through combining inputs.

**Features:**
- 3x3 input grid
- 1 output slot
- Accepts any vanilla items
- Outputs result based on crafted recipes or experimental combinations
- GUI for selecting recipes

### 2. Item Creator
Creates new items with rarity, unique forms, and special powers.

**Rarity Levels:**
- Common (gray)
- Uncommon (green)
- Rare (aqua)
- Epic (light purple)
- Legendary (gold)

**Unique Item Forms (Personalized Designs):**

| Form | Description | Visual Style |
|------|------------|-------------|
| amulet | Neck-hanging charm | Pendant shape, decorative chain |
| badge | Pin/emblem | Flat polygon, intricate border |
| charm | Lucky trinket | Small ornate shape, ribbons |
| coin | Currency token | Circular, embossed symbol |
| crystal | Magical gem | Faceted geometry, glowing core |
| crown | Headwear | Royal spikes, jewels |
| dagger | Small blade | Sharp point, handle grip |
| emblem | Heraldic sign | Shield shape, unique sigil |
| essence | Magical vial | Bottle/flask, swirling contents |
| fragment | Shard piece | Jagged irregular edge |
| gemstone | Precious stone | Polished cut, prismatic |
| glyph | Rune tablet | Rectangular, carved symbols |
| idol | Statuette | Miniature figure, detailed |
| insignia | Rank marker | Star/shape, ornamental |
| key | Unlocker | Bow + shaft, teeth |
| lens | Viewing glass | Circular frame, glass center |
| mask | Face covering | Eye holes, expression |
| medal | Award token | Ribbon + medallion |
| orb | Sphere | Perfect sphere, glow |
| prism | Light refractor | Angular, rainbow edge |
| relic | Ancient artifact | Weathered, mysterious |
| rune | Magic symbol | Carved stone, glow lines |
| scepter | Royal rod | Rod + head, jewels |
| shard | Broken piece | Sharp fragments |
| sigil | Magic mark | Circular, lines |
| staff | Magic rod | Long shaft, head piece |
| stone | Power core | Rough gem, energy |
| tablet | Inscribed slab | Flat, carved text |
| talisman | Good luck charm | Ornate, blessed |
| tome | Magic book | Pages, cover design |
| totem | Spirit vessel | Carved figure |
| trophy | Achievement | Cup/shield shape |
| vial | Liquid container | Bottle, liquid swirl |
| wand | Magic stick | Rod, channeled end |

**Special Powers:**
- ignite (fire aspect)
- knockback
- heal_aura (drains enemies)
- launch (vertical boost)
- freeze (slow enemies)
- drain (vampire leech)
- phase (invisibility + speed)
- lightning (smite attack)
- void_step (creative flight)

**Power Combinations:**
- Powers can stack based on rarity
- Common: 1 power
- Uncommon: 2 powers
- Rare: 3 powers
- Epic: 4 powers
- Legendary: 5 powers + passive aura

### 3. Build Creator
Creates voxel structures from AI prompts.

**System Prompt Example:**
The Block receives a text prompt and generates 3D voxel structures using AI.

**Features:**
- Text input field for prompts
- AI-powered structure generation
- Renders to in-game block preview
- Places structure in world on confirmation

**Technical Implementation:**
- Uses OpenRouter API for AI completions
- Parses voxel JSON output
- Renders using block API
- Places structure at player location

**Block Types Available:**
- stone, cobblestone, oak_planks, bricks
- stone_bricks, grass_block, dirt
- oak_log, oak_leaves, water
- white_wool, black_wool, red_wool
- blue_wool, green_wool, yellow_wool
- orange_wool, purple_wool, brown_wool
- gray_wool, glass, glowstone
- iron_block, gold_block

**Build Constraints:**
- Minimum: 500 blocks
- Maximum: 16,777,216 blocks
- Y is vertical (height)
- X, Z are horizontal plane

## Implementation Notes

- All blocks extend Block class
- BlockEntityProvider for GUI interaction
- ScreenHandler for custom GUIs
- Server-side logic for world modifications
- Client-side rendering for screens

## Item Naming Convention

When creating items, the AI generates:
- **Name**: Personalized based on form + powers
- **Description**: Flavour text describing the item's origin
- **Lore**: Hidden story/flavor that appears in tooltips

Example outputs:
- "Void Tear of Phase" - a tear-shaped amulet with phase power
- "Astral Blade of Ignite" - a dagger with fire aspect
- "Eternal Crown of Legend" - legendary crown with all powers
- "Phoenix Sigil of Ignite + Launch" - double-powered emblem