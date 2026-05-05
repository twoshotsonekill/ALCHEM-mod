# Structural Problems in Alchem-Mod

## Overview

The codebase has significant code duplication and architectural coupling that violates DRY (Don't Repeat Yourself) principles. All BlockEntity implementations share boilerplate code that should be extracted into a base class.

---

## Critical Issues

### Issue 1: BlockEntity State Machine Duplication

**Location**: `BuilderBlockEntity`, `CreatorBlockEntity`, `ForgeBlockEntity`, `InfuserBlockEntity`, `TransmuterBlockEntity`

**Problem**:
Every BlockEntity duplicates this exact pattern:
```java
public static final int STATE_IDLE = 0;
public static final int STATE_PROCESSING = 1;
public static final int STATE_READY = 2;
public static final int STATE_ERROR = 3;

private int state = STATE_IDLE;
private int progress = 0;
private boolean aiPending = false;

private final PropertyDelegate delegate = new PropertyDelegate() {
    @Override
    public int get(int index) {
        return switch (index) {
            case 0 -> state;
            case 1 -> progress;
            default -> 0;
        };
    }
    @Override
    public void set(int index, int value) {
        switch (index) {
            case 0 -> state = value;
            case 1 -> progress = value;
            default -> {}
        }
    }
    @Override public int size() { return 2; }
};
```

**Impact**:
- 5 implementations of the same pattern = 50+ lines duplicated per file
- Bug fix in state machine must be applied 5 times
- Inconsistent behavior if one implementation diverges
- Hard to add new features (e.g., shared cooldown logic)

**Solution**:
Create abstract base class `BaseAIBlockEntity<T>`:
```java
public abstract class BaseAIBlockEntity extends BlockEntity 
        implements NamedScreenHandlerFactory, Inventory {
    
    protected int state = STATE_IDLE;
    protected int progress = 0;
    protected boolean aiPending = false;
    
    public static final int STATE_IDLE = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_ERROR = 3;
    
    protected final PropertyDelegate delegate = new PropertyDelegate() {
        // ... implementation once
    };
    
    protected abstract void serverTick(World world, BlockPos pos);
}
```

**Files to Modify**:
- Create: `src/main/java/com/alchemod/block/BaseAIBlockEntity.java`
- Update: `BuilderBlockEntity`, `CreatorBlockEntity`, `ForgeBlockEntity`, `InfuserBlockEntity`, `TransmuterBlockEntity`

---

### Issue 2: JSON Parsing Utility Duplication

**Location**: `CreatorBlockEntity.java` (lines 300+), `ForgeBlockEntity.java` (lines 190+), `TransmuterBlockEntity.java` (lines 170+)

**Problem**:
All three files redefine these methods identically:
```java
private static String stripCodeFence(String content)
private static String extractFirstJsonObject(String content)
private static String getString(JsonObject obj, String key, String fallback)
private static String getNullable(JsonObject obj, String key)
private static String normalise(String value)
private List<String> getStringList(JsonObject obj, String key)
```

**Impact**:
- 80+ lines of duplicated JSON parsing code
- Bug in `stripCodeFence()` or `extractFirstJsonObject()` must be fixed 3 times
- Inconsistent error handling across blocks
- Hard to improve JSON parsing logic

**Solution**:
Create utility class `JsonParsingUtils`:
```java
public final class JsonParsingUtils {
    private JsonParsingUtils() {}
    
    public static String stripCodeFence(String content) { ... }
    public static String extractFirstJsonObject(String content) { ... }
    public static String getString(JsonObject obj, String key, String fallback) { ... }
    // ... etc
}
```

**Files to Create/Modify**:
- Create: `src/main/java/com/alchemod/util/JsonParsingUtils.java`
- Update: `CreatorBlockEntity`, `ForgeBlockEntity`, `TransmuterBlockEntity` (replace local methods with calls to utility)

---

### Issue 3: Result Record Duplication

**Location**: `CreatorBlockEntity`, `ForgeBlockEntity`, `TransmuterBlockEntity`

**Problem**:
Each block defines its own result record with identical error handling:

```java
// CreatorBlockEntity
public record CreationResult(
    String name, String description, ..., String error_msg
) {
    public static CreationResult error(String msg) {
        return new CreationResult(...null..., msg);
    }
}

// ForgeBlockEntity  
public record ForgeResult(
    String itemId, String name, ..., String error
) {
    public static ForgeResult error(String msg) {
        return new ForgeResult(...null..., msg);
    }
}

// TransmuterBlockEntity
public record TransmuterResult(
    String outputItem, ..., String error
) {
    public static TransmuterResult fallback() { ... }
    public static TransmuterResult error(String msg) { ... }
}
```

**Impact**:
- Same pattern implemented 3 times with different names
- Inconsistent error handling (some use `error()`, some use `fallback()`)
- Hard to add common error handling features
- Violates single responsibility principle

**Solution**:
Create generic `Result<T>` class:
```java
public class Result<T> {
    private final T data;
    private final String error;
    
    public Result(T data) { 
        this.data = data; 
        this.error = null; 
    }
    
    private Result(String error) { 
        this.data = null; 
        this.error = error; 
    }
    
    public static <T> Result<T> success(T data) { 
        return new Result<>(data); 
    }
    
    public static <T> Result<T> error(String msg) { 
        return new Result<>(msg); 
    }
    
    public boolean isError() { return error != null; }
    public T get() { return data; }
    public String getError() { return error; }
}
```

**Files to Create/Modify**:
- Create: `src/main/java/com/alchemod/util/Result.java`
- Update: `CreatorBlockEntity`, `ForgeBlockEntity`, `TransmuterBlockEntity` (replace custom result records)

---

### Issue 4: Script Sandbox API Fragmentation

**Location**: `com.alchemod.script.*`

**Problem**:
ItemScriptEngine receives APIs as separate class instances:
```java
// Current fragmented approach
ItemScriptEngine.execute(script, player, world, stack);

// Inside ItemScriptEngine, individual APIs are instantiated:
- WorldApi worldApi = new WorldApi(world)
- PlayerApi playerApi = new PlayerApi(player)
- EntityApi entityApi = new EntityApi(world)
```

But each API class is instantiated independently without a unified context.

**Impact**:
- APIs don't share state (e.g., random seed for reproducibility)
- Hard to add cross-API features (e.g., logging context)
- Difficult to test with mock context
- API classes are tightly coupled to specific parameters

**Solution**:
Create unified `ScriptSandboxContext`:
```java
public class ScriptSandboxContext {
    private final World world;
    private final PlayerEntity player;
    private final ItemStack itemStack;
    private final Random random;
    
    public WorldApi getWorldApi() { ... }
    public PlayerApi getPlayerApi() { ... }
    public EntityApi getEntityApi() { ... }
    
    public void log(String message) { ... }
}
```

**Files to Create/Modify**:
- Create: `src/main/java/com/alchemod/script/ScriptSandboxContext.java`
- Update: `ItemScriptEngine.java`, `WorldApi`, `PlayerApi`, `EntityApi` to use context

---

## Summary

| Issue | Type | Duplication | Impact | Effort |
|-------|------|-------------|--------|--------|
| BlockEntity Base Class | CRITICAL | 50+ lines × 5 files | Hard to maintain, inconsistent behavior | 4 hours |
| JSON Utils | CRITICAL | 80+ lines × 3 files | Bug fixes must be tripled | 2 hours |
| Result Records | CRITICAL | 20+ lines × 3 files | Inconsistent error handling | 1 hour |
| Script Context | MAJOR | Implied in 4 files | Hard to extend or test | 3 hours |

**Total Duplication**: ~400 lines of code that could be shared

---

## Recommended Refactoring Order

1. **Create `Result<T>` utility** (1 hour)
   - Low risk, directly replaceable in 3 locations
   - No dependencies on other refactorings
   
2. **Create `JsonParsingUtils`** (2 hours)
   - Extract methods from 3 files
   - Verify tests pass after extraction
   
3. **Create `BaseAIBlockEntity`** (4 hours)
   - Largest refactoring, affects 5 files
   - Requires careful inheritance hierarchy
   - Must test state machine with all 5 entity types
   
4. **Create `ScriptSandboxContext`** (3 hours)
   - Optional but recommended for maintainability
   - Improves testability and extensibility

**Total Estimated Time**: 10 hours

---

## Long-Term Benefits

- ✅ 400+ fewer lines to maintain
- ✅ Bug fixes applied once instead of 3-5 times
- ✅ Easier to add new block types (inherit from base class)
- ✅ Easier to add new script APIs (use context pattern)
- ✅ Better testability (single implementation to test)
- ✅ Improved code readability

