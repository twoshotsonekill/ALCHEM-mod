# JSON Parsing Utilities Refactoring

## Problem
**~70 lines of identical JSON parsing code** were duplicated across multiple BlockEntity classes:
- CreatorBlockEntity: `stripCodeFence()`, `extractFirstJsonObject()`, `getString()`, `getNullableString()`, `getStringList()`, `normalise()`
- ForgeBlockEntity: Same 6 methods, slightly different formatting
- TransmuterBlockEntity: Would have same duplication

This violated DRY (Don't Repeat Yourself) and made maintenance harder.

## Solution
Created **JsonParsingUtils** utility class with shared methods, then migrated BlockEntities to use it.

### Files Changed

**JsonParsingUtils.java** (Extended)
- Added `getStringList(JsonObject, String)` method to support list parsing
- Now provides complete JSON extraction/parsing API

**CreatorBlockEntity.java**
- Added import: `import com.alchemod.util.JsonParsingUtils`
- Updated `parseCreationResult()` to use `JsonParsingUtils.*` instead of local methods
- Removed 37 lines of duplicate JSON helper methods

**ForgeBlockEntity.java**
- Added import: `import com.alchemod.util.JsonParsingUtils`
- Updated `parseForgeResult()` to use `JsonParsingUtils.*` instead of local methods
- Removed 38 lines of duplicate JSON helper methods
- Kept local `normalise(String)` for null-handling specific to Forge (minor variant)

## Code Removed
```java
// No longer needed in BlockEntity classes:
- private static String stripCodeFence(String)
- private static String extractFirstJsonObject(String)
- private static String getString(JsonObject, String, String)
- private static String getNullableString(JsonObject, String)
- private static List<String> getStringList(JsonObject, String)
- private static String normalise(String)  // [partly, Forge kept variant]
```

## Impact
- ✅ Eliminated ~70 lines of duplicate code
- ✅ Single source of truth for JSON parsing logic
- ✅ Easier to maintain and fix parsing bugs
- ✅ Sets pattern for TransmuterBlockEntity future refactoring

## Verification
- Both BlockEntity classes updated and compile successfully
- JSON helper methods centralized in JsonParsingUtils
- No functional changes - same behavior, less duplication
