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

## Priority 2: Improve Item Creator Texture Generation

**Current Issue:** Textures are static/colored placeholders.

**Solution:**
- Generate unique sprite based on item properties
- Colors based on rarity: common=gray, uncommon=green, rare=aqua, epic=purple, legendary=gold
- Shape based on item form (amulet, dagger, crown, etc.)
- Add special effect glow overlays for powers
- Use `RuntimeTextureManager` to generate at creation time

**Sprite Generation Logic:**
- Base: unique geometric pattern per form
- Border: rarity color
- Center: power effect symbol
- Glow: pulsing animation for special abilities

**Files to modify:**
- `RuntimeTextureManager.java` - New texture generation
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

**NOTE: The zip did not contain separate files for two variants. Current BuilderBlock handles block inputs. Need to add text version.**

**Build Creator Text (New):**
- Input: Text prompts only
- Uses AI to generate voxel code
- Best for complex/creative builds
- Single block placed in world

**Build Creator Blocks (Current=BuilderBlock):**
- Input: 3x3 grid of blocks (like crafting)
- Player places blocks into input slots
- Combines blocks into structure
- Best for exact building
- More hands-on

**Implementation for Text Version:**
- Create `PromptBlock.java` - extends Block
- Create `PromptBlockEntity.java` - handles AI calls
- Create `PromptScreen.java` - simple text input GUI
- Uses existing networking from BuilderBlock

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

The zip file contains additional files that should be integrated:
- `BuilderBlockEntity.java` - Already present, improve functionality
- `PromptBlock.java` / `PromptBlockEntity.java` - Add as "Text Builder"
- `PromptBuilderScreen.java` - Text input GUI
- `BuilderPromptPayload.java` / `PromptPayload.java` - Network packets

**Implementation:**
1. Rename PromptBlock → BuildCreatorText
2. Keep existing BuilderBlock → BuildCreatorBlocks  
3. Both should have different GUIs

---

## Notes

- All NBT modifications must be server-side for multiplayer compatibility
- Use `ItemStack.writeNbt()` and `readNbt()` for serialization
- Test each rarity level outputs correctly
- Verify enchantments persist across world restart