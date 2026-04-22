# Alchemod Improvement Plan

## Priority 1: Fix Input Slot Bug
**Issue:** Items can be taken out of input slots during creation without completing the process.

**Solution:**
- Lock input slots when recipe is being processed
- Add `canTakeStack` check to return false during crafting
- Add property delegate state tracking (IDLE, PROCESSING, COMPLETE)
- Only allow output extraction when processing finished

**Files to modify:**
- `CreatorBlockEntity.java` - Add state tracking
- `CreatorScreenHandler.java` - Lock input slots during process

---

## Priority 7: Image Generation for Item Sprites

**Item Creator should generate sprites from:**
1. **Block inputs** - Colors from placed blocks
2. **AI Image Generation** - Send text description to AI
3. **Reference Image** - Upload/copy from existing texture

**AI Image Pipeline:**
```
User text prompt → OpenRouter AI → Image response → Parse to sprite → Save as item texture
```

**Image Generation Features:**
- Support stable diffusion endpoints
- Parse AI image to 16x16 pixel art
- Color quantization to game palette
- Dithering for smooth gradients
- Custom model support

**Implementation:**
- Add `ImageGenerator.java` utility
- Uses OpenRouter API for image generation
- Converts AI image → Minecraft sprite format
- Links texture to DynamicItem

---

## Priority 2: Improve Item Creator Texture Generation

**Current Issue:** Textures are static/colored placeholders.

**Solution:**
- Generate texture **based on input blocks/items placed in the creator**
- Analyze colors of input items
- Mix/bend colors to create unique sprite
- Include patterns from input materials
- Add rarity border overlay
- Add power effect symbols

**Texture Generation from Inputs:**
1. Player places input items (Up to 9 items in grid)
2. Extract color palette from each input item's texture  
3. Blend/gradient colors together
4. Add geometric pattern based on item form
5. Apply rarity border color
6. Add power indicator symbol

**Sprite Generation Logic:**
- Input: Items in 3x3 grid → Color analysis
- Process: Color blending algorithm
- Output: Unique 16x16 sprite
- Border: rarity color (gray/green/aqua/purple/gold)
- Center: power symbol

**Example:**
- Input: diamond + redstone → Red sparkle pattern
- Input: emerald + glass → Green crystal pattern
- Input: gold + blaze → Gold flame pattern

**Files to modify:**
- `RuntimeTextureManager.java` - New texture generation from inputs
- `CreatorBlockEntity.java` - Trigger texture generation on create

---

## Priority 3: Improve Build Creator Voxel Block Sprite

**Current Issue:** Basic block texture.

**Solution:**
- Multi-layer block model (3D appearance)
- Animated voxel grid pattern
- Particle effects showing "processing"
- Different states: idle, generating, complete

**Visual States:**
- Idle: dim glow, static grid
- Generating: pulsing lights, animated voxels
- Complete: bright flash, particles

**Files to modify:**
- `assets/alchemod/models/block/build_creator.json` - Multi-part model
- `assets/alchemod/textures/block/build_creator.png` - Better texture
- `BuilderBlockEntity.java` - Update block state

---

## Priority 4: Item Combiner NBT Data Input

**Current Issue:** Cannot input custom names, enchantments, etc.

**Solution:**
- Add NBT editor UI panel
- Allow custom display name (TextComponent)
- Allow lore (multiple lines)
- Allow stored enchantments
- Allow custom attributes
- Allow potion effects on items
- Allow repair cost customization

**NBT Fields to Support:**
```
{
  display: {
    Name: "custom name",
    Lore: ["line1", "line2"],
    color: hex color
  },
  Enchantments: [{id: "sharpness", level: 5}],
  AttributeModifiers: [...],
  CustomPotionEffects: [...],
  HideFlags: bitmask
}
```

**Files to modify:**
- `ForgeBlockEntity.java` - Process NBT on output
- `ForgeScreen.java` - Add NBT editor UI
- New: `NbtEditorWidget.java` - Custom UI component

---

## Priority 5: Overall Item Creator Improvements

### 5a. Better Special Abilities

**Current Powers:** ignite, knockback, heal_aura, launch, freeze, drain, phase, lightning, void_step

**Enhanced Powers (with actual effects):**

| Power | Effect | Implementation |
|-------|-------|---------------|
| ignite | Fire aspect II on hit | EventCallback on damage |
| knockback | Knockback II | Modify velocity on hit |
| heal_aura | Drains enemies, heals user | Area effect around player |
| launch | Vertical velocity boost | Add velocity |
| freeze | Slowness IV | Apply status effect |
| drain | Vampire leech | Damage/heal cycle |
| phase | Invisibility + Speed | Status effects |
| lightning | Strike nearby | Spawn entity |
| void_step | Creative flight | Disable gravity |
|泡影 | Shadow step teleport | Teleport forward |
| barrier | Damage reflection | Modify damage source |
| surge | Speed II + Jump II | Combined effects |

### 5b. Rarity System Improvements

- Common: 1 power, gray name
- Uncommon: 2 powers, green name
- Rare: 3 powers + glow, aqua name
- Epic: 4 powers + particles, purple name
- Legendary: 5 powers + aura, gold name + glow effect

### 5c. Item Form Special Behaviors

Each form gets unique handling:

| Form | Behavior |
|------|---------|
| amulet | Auto-equip to necklace slot |
| dagger | Quick attack bonus |
| crown | Passive aura |
| orb | Orbit around player |
| staff | Extended reach |
| tome | Book opening |
| shield | Blocking bonus |

### 5d. Visual Improvements

- Custom item models per form
- Animated textures
- Particle effects on use
- Glow layers for legendary
- Sound effects per form

## Priority 6: Two Build Creator Variants

**Variant A: Block-Based (Current BuilderBlock)**
- Input: Player places real Minecraft blocks in 3x3 grid
- Combines blocks into structure
- Best for exact building from materials
- More hands-on, visual feedback
- 9 input slots (like crafting table)

**Variant B: Text-Based (Current BuilderBlock with toggle)**
- Input: Text prompt describes what to build
- AI generates voxel code
- Best for complex/creative builds
- Toggle mode in GUI: "Block Mode" / "Text Mode"

**Screen Layouts:**
- **Block Input GUI:** 3x3 grid + preview + "Generate" button
- **Text Input GUI:** Text field + prompt dropdowns + "Generate" button

**Implementation:**
- Keep existing `BuilderBlock` with mode toggle in GUI

---

## Implementation Order

1. Fix input slot bug (Priority 1)
2. Add NBT support to Forge (Priority 4)
3. Update Item Creator textures (Priority 2)
4. Enhance special abilities (Priority 5a-5d)
5. Update Build Creator sprite (Priority 3)

---

## Files to Create/Modify

### New Files:
- `src/main/java/com/alchemod/util/NbtEditorWidget.java`
- `src/main/java/com/alchemod/mixin/ItemStackMixin.java`

### Modify:
- `CreatorBlockEntity.java`
- `CreatorScreenHandler.java`
- `ForgeBlockEntity.java`
- `ForgeScreen.java`
- `DynamicItem.java`
- `DynamicItemRegistry.java`
- `RuntimeTextureManager.java`
- `assets/alchemod/models/block/build_creator.json`

---

## Files from (10).zip to Integrate

The zip file was missing some files. Need to add Text Builder implementation:

**Current (already works):**
- `BuilderBlock.java` + `BuilderBlockEntity.java` - Block input version

**Need to add for Text version:**
1. Create `PromptBlock.java` - Text input block (like BuilderBlock but with text field)
2. Create `PromptBlockEntity.java` - Sends prompts to AI
3. Create `PromptScreen.java` - GUI with text input field
4. Create network payloads for text → AI response

**Alternative: Make existing BuilderBlock have two modes**
- Mode A: Block input (current)
- Mode B: Text input (toggle in GUI)

---

## Notes

- All NBT modifications must be server-side for multiplayer compatibility
- Use `ItemStack.writeNbt()` and `readNbt()` for serialization
- Test each rarity level outputs correctly
- Verify enchantments persist across world restart