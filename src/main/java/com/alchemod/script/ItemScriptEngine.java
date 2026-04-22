package com.alchemod.script;

import com.alchemod.AlchemodInit;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

public final class ItemScriptEngine {

    private static final int INSTRUCTION_BUDGET = 500_000;

    private static final ContextFactory FACTORY = new ContextFactory() {
        @Override
        protected Context makeContext() {
            Context context = super.makeContext();
            context.setOptimizationLevel(-1);
            context.setLanguageVersion(Context.VERSION_ES6);
            context.setInstructionObserverThreshold(INSTRUCTION_BUDGET);
            return context;
        }

        @Override
        protected void observeInstructionCount(Context context, int instructionCount) {
            throw new Error("[ItemScript] Budget exceeded - script terminated.");
        }
    };

    private ItemScriptEngine() {
    }

    public static boolean execute(String script, PlayerEntity player, ServerWorld world, ItemStack stack) {
        if (script == null || script.isBlank()) {
            return false;
        }

        Context context = FACTORY.enterContext();
        try {
            Scriptable scope = context.initSafeStandardObjects();

            PlayerApi playerApi = new PlayerApi(player, world);
            WorldApi worldApi = new WorldApi(world, player);

            ScriptableObject.putProperty(scope, "player", Context.javaToJS(playerApi, scope));
            ScriptableObject.putProperty(scope, "world", Context.javaToJS(worldApi, scope));
            ScriptableObject.putProperty(scope, "stack", Context.javaToJS(stack, scope));

            ScriptableObject.putProperty(scope, "nearbyEntities", new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable callableScope, Scriptable thisObj, Object[] args) {
                    double radius = args.length > 0 ? Context.toNumber(args[0]) : 8.0;
                    Object[] entities = worldApi.nearbyEntities(radius);
                    Scriptable array = cx.newArray(callableScope, entities.length);
                    for (int index = 0; index < entities.length; index++) {
                        array.put(index, array, Context.javaToJS(entities[index], callableScope));
                    }
                    return array;
                }
            });

            ScriptableObject.putProperty(scope, "log", new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable callableScope, Scriptable thisObj, Object[] args) {
                    if (args.length > 0) {
                        AlchemodInit.LOG.info("[ItemScript] {}", Context.toString(args[0]));
                    }
                    return Undefined.instance;
                }
            });

            context.evaluateString(scope, script, "item_script", 1, null);

            Object function = ScriptableObject.getProperty(scope, "onUse");
            if (function instanceof Function onUse) {
                onUse.call(context, scope, scope, new Object[]{
                        Context.javaToJS(playerApi, scope),
                        Context.javaToJS(worldApi, scope)
                });
                return true;
            }

            AlchemodInit.LOG.warn("[ItemScript] Script has no onUse() function.");
        } catch (RhinoException e) {
            AlchemodInit.LOG.warn("[ItemScript] Script runtime error: {}", e.getLocalizedMessage());
        } catch (Error e) {
            AlchemodInit.LOG.warn("[ItemScript] Script aborted: {}", e.getMessage());
        } catch (Exception e) {
            AlchemodInit.LOG.error("[ItemScript] Unexpected error", e);
        } finally {
            Context.exit();
        }

        return false;
    }
}
