# ALCHEM-mod Architectural Refactor Plan

## Overview

This document outlines the critical architectural issues in ALCHEM-mod and the refactoring strategy to address them. The issues are prioritized by impact and complexity.

---

## Issue 1: Dynamic Item Persistence Broken Across Restarts (CRITICAL)

### Current Problem

The dynamic item system uses a **slot-based identity model** that breaks on server restart:

1. `DynamicItemRegistry` registers 64 pre-allocated items (`dynamic_item_0` through `dynamic_item_63`)
2. `nextSlot` is an in-memory counter that increments each time an item is created
3. Metadata is stored in `META`, a `ConcurrentHashMap` that is **never persisted**
4. When the server restarts, `nextSlot` resets to 0
5. The next crafted item claims slot 0, **overwriting** whatever was there before
6. Players holding `dynamic_item_3` now have the wrong item or no metadata at all
7. After 64 crafts, `claimSlot()` returns `null` and players get nothing

### Root Cause

Item identity is tied to **which of 64 registry slots it landed in**, not to **what the item actually is**. The metadata is ephemeral and slot-based, not persistent and data-driven.

### Solution: NBT-Driven Item Identity

**Replace the 64-slot model with a single `OddityItem` class that stores all identity in NBT components:**

1. **Register one item class** (`OddityItem` or `DynamicItem`) instead of 64 variants
2. **Store all metadata in NBT components** on the `ItemStack`:
   - `creator_name` → item display name
   - `creator_description` → lore/flavor text
   - `creator_rarity` → rarity level
   - `creator_effects` → list of effects
   - `creator_special` → special ability
   - `creator_script` → behavior script
   - `creator_sprite_commands` → sprite drawing commands
   - `creator_charges` → remaining charges
3. **Item identity comes from its data**, not from registry slot
4. **No exhaustion problem**: unlimited items can be created
5. **Persistence is automatic**: NBT is saved with the world

### Implementation Steps

1. Create `OddityItem` class that reads all metadata from NBT
2. Remove the 64-slot pre-registration loop
3. Update `DynamicItemRegistry` to simply register one item and provide helper methods
4. Update `CreatorBlockEntity` to write all metadata to NBT instead of calling `claimSlot()`
5. Update `DynamicItem.use()` to read from NBT first (already partially done)
6. Update `ItemAbilityEvents` to read `creator_special` from NBT instead of registry slot
7. Update `RuntimeTextureManager` to key textures by a hash of the NBT data instead of slot

### Files to Modify

- `DynamicItemRegistry.java` – Simplify to single-item registration
- `DynamicItem.java` → `OddityItem.java` – Rename and refactor to be NBT-driven
- `CreatorBlockEntity.java` – Write NBT instead of calling `claimSlot()`
- `ItemAbilityEvents.java` – Read NBT instead of registry slot
- `RuntimeTextureManager.java` – Use NBT hash instead of slot for texture keys
- `CreatorScreenHandler.java` – Remove `lastCreatedSlot` syncing (no longer needed)

---

## Issue 2: ForgeBlockEntity Duplicates OpenRouterClient (QUICK WIN)

### Current Problem

`ForgeBlockEntity` reimplements AI integration instead of reusing `OpenRouterClient`:

1. **Inline `HttpClient` creation** (lines 159–161) – duplicates what `OpenRouterClient` already does
2. **Manual JSON building** (lines 150–156) – duplicates `OpenRouterClient.ChatRequest`
3. **Regex-based response parsing** (lines 186–197) – duplicates `OpenRouterClient` parsing
4. **Hardcoded model string** `"openai/gpt-4o-mini"` (line 151) – the only remaining hardcoded model in the codebase
5. **No config integration** – ignores `AlchemodInit.CONFIG`

### Solution: Use OpenRouterClient

Replace the inline HTTP logic with a call to `OpenRouterClient.chat()`:

```java
OpenRouterClient.ChatResult result = OpenRouterClient.chat(
    AlchemodInit.OPENROUTER_KEY,
    new OpenRouterClient.ChatRequest(
        AlchemodInit.CONFIG.forgeModel(),  // NEW: add to config
        30,  // max_tokens
        10,  // timeout seconds
        systemPrompt,
        userPrompt
    )
);

if (result.isError()) {
    return "ERROR:" + result.error();
}

return parseItemId(result.content());
```

### Implementation Steps

1. Add `forgeModel()` to `AlchemodConfig` with default `"openai/gpt-4o-mini"`
2. Add `forgeTimeoutSeconds()` to `AlchemodConfig` with default `10`
3. Replace `queryOpenRouter()` method to use `OpenRouterClient.chat()`
4. Remove inline `HttpClient` and manual JSON building
5. Simplify `parseItemId()` to work with the cleaned response string

### Files to Modify

- `AlchemodConfig.java` – Add `forgeModel()` and `forgeTimeoutSeconds()`
- `ForgeBlockEntity.java` – Replace `queryOpenRouter()` to use `OpenRouterClient`

---

## Issue 3: Block Placement Happens Synchronously (PERFORMANCE)

### Current Problem

In `BuilderBlockEntity.executeBuildResponse()`, after the AI responds:

1. `placeRelativeBlock()` is called in a **tight loop** (lines 170–171)
2. Can place up to **24,576 blocks in a single tick**
3. This causes a **multi-second server thread freeze**
4. Players experience lag spikes and potential timeout kicks

### Solution: Tick-Based Placement Queue

Implement a placement queue that drains a few hundred blocks per tick:

```java
private Queue<PlacementTask> placementQueue = new ConcurrentLinkedQueue<>();

// In executeBuildResponse, instead of placing directly:
for (PlacementTask task : placements) {
    placementQueue.offer(task);
}

// In serverTick:
int placed = 0;
while (!placementQueue.isEmpty() && placed < 256) {
    PlacementTask task = placementQueue.poll();
    placeRelativeBlock(task.world, task.origin, task.x, task.y, task.z, task.blockId);
    placed++;
}
```

### Implementation Steps

1. Create `PlacementTask` record to hold placement data
2. Add `placementQueue` field to `BuilderBlockEntity`
3. Modify `BuilderRuntime.execute()` to return placements instead of executing immediately
4. Collect placements in `executeBuildResponse()` and queue them
5. Add placement draining logic to `serverTick()`
6. Update progress tracking to reflect queued placements

### Files to Modify

- `BuilderBlockEntity.java` – Add queue and draining logic
- `BuilderRuntime.java` – Return placements instead of executing immediately

---

## Issue 4: Missing Per-Player Cooldown (POLISH)

### Current Problem

Players can submit a new prompt the moment the previous build finishes, leading to:

1. **Rapid API cost accumulation**
2. **Potential abuse or accidental spam**
3. **No rate limiting**

### Solution: Simple Cooldown

Add a 30-second cooldown stored in the block entity:

```java
private long lastPromptTime = 0;
private static final long COOLDOWN_MS = 30_000;

public boolean canSubmitPrompt() {
    return System.currentTimeMillis() - lastPromptTime >= COOLDOWN_MS;
}

public void recordPromptSubmission() {
    lastPromptTime = System.currentTimeMillis();
}
```

### Files to Modify

- `BuilderBlockEntity.java` – Add cooldown tracking and validation

---

## Issue 5: STATE_COMPLETE Never Resets (POLISH)

### Current Problem

`BuilderBlockEntity.STATE_COMPLETE` persists indefinitely:

1. Block sits in "complete" state forever
2. Not obvious to player that block is ready for another prompt
3. Player must close and reopen screen to see state change

### Solution: Auto-Reset State

Reset to `STATE_IDLE` after a few seconds or on screen close:

```java
private long completedTime = 0;
private static final long COMPLETE_DISPLAY_MS = 3000;

// In serverTick:
if (state == STATE_COMPLETE) {
    if (System.currentTimeMillis() - completedTime > COMPLETE_DISPLAY_MS) {
        state = STATE_IDLE;
        progress = 0;
        markDirty();
    }
}
```

### Files to Modify

- `BuilderBlockEntity.java` – Add auto-reset logic

---

## Issue 6: Aggressive Memory Settings (ENVIRONMENT)

### Current Problem

`gradle.properties` sets `-Xmx4G` (4 GB heap), which is aggressive for dev machines:

1. Can cause OOM crashes on memory-constrained systems
2. The crash log `hs_err_pid26468.log` shows JVM trying to allocate 248 MB with only 854 MB free

### Solution: Reduce Default Heap

Change `-Xmx4G` to `-Xmx2G` or `-Xmx1G` for dev environments:

```properties
org.gradle.jvmargs=-Xmx2G
```

### Files to Modify

- `gradle.properties` – Reduce heap size

---

## Refactoring Priority

1. **Issue 1 (Dynamic Item Persistence)** – CRITICAL, enables all other features to work correctly
2. **Issue 2 (ForgeBlockEntity Cleanup)** – QUICK WIN, improves code quality and maintainability
3. **Issue 3 (Block Placement Threading)** – IMPORTANT, prevents server freezes at scale
4. **Issue 4 (Per-Player Cooldown)** – NICE TO HAVE, prevents API cost abuse
5. **Issue 5 (STATE_COMPLETE Reset)** – NICE TO HAVE, improves UX
6. **Issue 6 (Memory Settings)** – ENVIRONMENT, helps dev experience

---

## Testing Strategy

After each refactor:

1. **Unit tests** for new NBT-driven item system
2. **Integration tests** for item creation and persistence across restarts
3. **Performance tests** for block placement queue (measure tick time)
4. **Regression tests** for existing functionality (forge, creator, effects)

---

## Expected Outcomes

- ✅ Items persist correctly across server restarts
- ✅ No more item exhaustion after 64 crafts
- ✅ Unified AI client code (no duplication)
- ✅ No server freezes during large builds
- ✅ API cost protection via cooldowns
- ✅ Better UX with state auto-reset
- ✅ Improved dev experience with reasonable memory defaults
