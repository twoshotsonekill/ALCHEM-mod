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
Creates voxel structures from AI prompts - can write its own code for unlimited creativity.

## Build Creator Features

### Code Generation Mode
The Build Creator can generate its own voxel-placement code, giving it complete creative freedom.

**Available Runtime Functions:**

```javascript
// Core primitives
block(x, y, z, type)           // Place single block at coords
box(x1,y1,z1, x2,y2,z2, type) // Filled rectangular prism
line(x1,y1,z1, x2,y2,z2, type) // Line of blocks

// Advanced shapes
sphere(xc, yc, zc, r, type)   // Hollow sphere
cylinder(xc,yc,zc, r, h, type) // Vertical cylinder
cone(xc,yc,zc, r, h, type)     // Pointed cone
pyramid(xc,yc,zc, r, h, type) // Square pyramid
torus(xc,yc,zc, r1, r2, type) // Ring shape
dome(xc,yc,zc, r, type)       // Half-sphere

// Transformations
rotateX(x, y, z, angle)       // Rotate around X axis
rotateY(x, y, z, angle)       // Rotate around Y axis  
rotateZ(x, y, z, angle)       // Rotate around Z axis
scale(x, y, z, factor)       // Scale coordinates

// Utility
noise(x, y, z, seed)         // Perlin/simplex noise
random(x, y, z, seed)         // Random value
smooth(x, y, z, radius)      // Smooth operation

// Materials available
stone, cobblestone, oak_planks, bricks, stone_bricks
grass_block, dirt, sand, oak_log, oak_leaves, water
white_wool, black_wool, red_wool, blue_wool, green_wool
yellow_wool, orange_wool, purple_wool, brown_wool, gray_wool
glass, glowstone, iron_block, gold_block, diamond_block
emerald_block, obsidian, netherite, quartz, terracotta
```

### Prompt Types

**1. Simple Text Prompt**
```
"Create a castle with towers"
```
AI generates voxel structure automatically.

**2. Code Generation**
```
"Write code to create a spiral staircase using stone_bricks and gold_block"
```
AI writes and executes JavaScript code.

**3. Parametric Build**
```
"Create a torus of radius 20 with emerald blocks"
```
AI generates geometry programmatically.

**4. Procedural Generation**
```
"Create a mountain with noise-based terrain using stone and grass"
```
Uses noise functions for natural shapes.

**5. Creative Freedom Mode**
```
"Create anything you think is impressive"
```
AI decides what to build entirely on its own.

### Example Generated Code

The AI might generate something like:

```javascript
// Castle with spiral towers
function buildCastle() {
    // Main keep
    box(10, 0, 10, 30, 40, 30, stone_bricks);
    
    // Four corner towers
    for (let i = 0; i < 4; i++) {
        let ox = i % 2 === 0 ? 5 : 25;
        let oz = Math.floor(i / 2) * 20 + 5;
        buildTower(ox, 0, oz, 8, 30);
    }
    
    // Spiral staircase inside
    for (let y = 0; y < 30; y++) {
        let angle = y * 15 * Math.PI / 180;
        let r = 5;
        block(
            Math.floor(20 + Math.cos(angle) * r),
            y,
            Math.floor(20 + Math.sin(angle) * r),
            stone
        );
    }
    
    // Moat
    box(-5, 0, -5, 45, 2, 45, water);
    bridge(0, 3, 18, 30, 20, oak_planks);
}

// Procedural mountains using noise
function buildMountains() {
    for (let x = 0; x < 128; x++) {
        for (let z = 0; z < 128; z++) {
            let h = noise(x * 0.1, z * 0.1, 1234) * 20;
            for (let y = 0; y < h; y++) {
                let type = y > h - 3 ? stone_bricks : stone;
                block(x, y, z, type);
            }
            if (h > 15) block(x, h, z, oak_leaves);
        }
    }
}

buildCastle();
buildMountains();
```

### Prompt Engineering

Users can give the AI prompts like:

- **Descriptive**: "Build a dragon with spread wings"
- **Technical**: "Create a parametric spiral of radius 10 using gold and black wool alternating"
- **Artistic**: "Make something that evokes wonder and mystery"
- **competitive**: "Create something better than [describe competitor's build]"
- **Experimental**: "Try something you've never done before"

### Custom System Prompts

Users can set custom AI instructions:

```
System: "You are a master voxel architect. Create detailed,
intricate structures with true 3D articulation. Avoid flat
surfaces - everything should have depth and dimension.
Prefer complex shapes over simple boxes."
```

### Build Constraints

- Minimum: 500 blocks
- Maximum: 16,777,216 blocks (full grid)
- Target: 10,000 to 3,000,000+ for competitive builds
- Grid: x, y, z in range [0, 255]
- Y is vertical (height), Y=0 is ground level

## Implementation Notes

- Uses OpenRouter API for AI completions
- Parses voxel JSON output
- Renders using Fabric block API
- Places structure at player location or defined coordinates
- Code executes in sandboxed environment

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