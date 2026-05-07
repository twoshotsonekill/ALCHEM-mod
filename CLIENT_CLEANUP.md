# Client Code Cleanup - Structural Fix

## Problem
The codebase had **severe structural duplication**: client-side code existed in BOTH `src/main/java/` AND `src/client/java/`, causing:
- **Dedicated server crashes**: Client-only imports and `@Environment(EnvType.CLIENT)` annotations should not exist on the main classpath
- **Compilation failures**: Duplicate class definitions
- **Invalid handler registrations**: `AlchemodClient` tried to register non-existent screen handlers

## Files Fixed

### Removed from src/main/java (blanked with MOVED marker)
These files contained client-only code and have been moved to `src/client/java/`:
- `AlchemodClient.java` - ClientModInitializer with HandledScreens registration
- `BuilderScreen.java` - HandledScreen with ClientPlayNetworking
- `ForgeScreen.java` - GUI screen with client APIs  
- `CreatorScreen.java` - GUI screen with client APIs
- `SpriteCommandRenderer.java` - Client-side sprite painting logic
- `RuntimeTextureManager.java` - Client-side texture management (@Environment annotation)
- `DynamicModelProvider.java` - Client-side model provider (@Environment annotation)

**Status**: All marked with `/* MOVED: This file is now in src/client/java/... */` to prevent accidental reactivation.

### Updated in src/client/java
**AlchemodClient.java**
- **Before**: Tried to register INFUSER_HANDLER and TRANS_MUTER_HANDLER (which don't exist)
- **After**: Only registers handlers that are actually defined in AlchemodInit (FORGE, CREATOR, BUILDER)
- **Removed**: Misleading comment about "ItemRendererMixin" (mixin system not used; rendering handled by RuntimeTextureManager)

## Verification
The codebase now follows Fabric mod structure conventions:
- ✅ All client-side code lives exclusively in `src/client/java/`
- ✅ No client-only imports or annotations in `src/main/java/`
- ✅ AlchemodClient only registers existing handlers
- ✅ No misleading documentation referencing deleted code

## Impact
- Fixes dedicated server crashes caused by client code on server classpath
- Eliminates duplicate class definitions  
- Corrects screen handler registration to match actual implementation
- Improves codebase clarity and maintainability

## Files Needing Manual Cleanup (Optional)
These are markers in src/main/java - they can be safely deleted:
- `src/main/java/com/alchemod/AlchemodClient.java` (1 line marker)
- `src/main/java/com/alchemod/screen/BuilderScreen.java` (1 line marker)
- `src/main/java/com/alchemod/screen/ForgeScreen.java` (1 line marker)
- `src/main/java/com/alchemod/screen/CreatorScreen.java` (1 line marker)
- `src/main/java/com/alchemod/resource/SpriteCommandRenderer.java` (1 line marker)
- `src/main/java/com/alchemod/resource/RuntimeTextureManager.java` (1 line marker)
- `src/main/java/com/alchemod/resource/DynamicModelProvider.java` (1 line marker)
