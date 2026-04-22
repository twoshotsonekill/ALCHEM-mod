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
Creates new items with rarity and special powers.

**Rarity Levels:**
- Common (gray)
- Uncommon (green)
- Rare (aqua)
- Epic (light purple)
- Legendary (gold)

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