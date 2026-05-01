package com.alchemod.builder;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class BuilderRuntime {

    public static final String PALETTE_NAME = "simple_v1";
    public static final int MAX_BLOCK_PLACEMENTS = 24_576;
    public static final int MAX_XZ_OFFSET = 64;
    public static final int MIN_Y_OFFSET = -8;
    public static final int MAX_Y_OFFSET = 72;
    public static final int MAX_SPHERE_RADIUS = 16;
    public static final int INSTRUCTION_BUDGET = 1_500_000;
    public static final List<String> SIMPLE_PALETTE_BLOCKS = List.of(
            // Stone variants
            "stone", "cobblestone", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks",
            "deepslate", "deepslate_bricks", "deepslate_tiles",
            // Wood variants
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
            "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
            "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves",
            // Brick variants
            "bricks", "mud_bricks", "nether_bricks", "red_nether_bricks",
            // Natural blocks
            "grass_block", "dirt", "coarse_dirt", "rooted_dirt", "sand", "red_sand",
            "gravel", "clay", "mud", "dripstone_block",
            // Ores
            "coal_ore", "copper_ore", "iron_ore", "gold_ore", "diamond_ore", "emerald_ore", "lapis_ore", "redstone_ore",
            // Metals and precious
            "iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block", "redstone_block", "copper_block",
            // Glass variants
            "glass", "tinted_glass",
            // Light sources
            "glowstone", "sea_lantern", "shroomlight", "amethyst_block",
            // Wool variants
            "white_wool", "black_wool", "red_wool", "blue_wool", "green_wool",
            "yellow_wool", "orange_wool", "purple_wool", "brown_wool", "gray_wool",
            "light_gray_wool", "cyan_wool", "lime_wool", "pink_wool", "magenta_wool",
            // Concrete variants
            "white_concrete", "black_concrete", "red_concrete", "blue_concrete", "green_concrete",
            "yellow_concrete", "orange_concrete", "purple_concrete", "brown_concrete", "gray_concrete",
            "light_gray_concrete", "cyan_concrete", "lime_concrete", "pink_concrete", "magenta_concrete",
            // Terracotta variants
            "terracotta", "white_terracotta", "black_terracotta", "red_terracotta", "blue_terracotta",
            // Nether blocks
            "netherrack", "soul_sand", "soul_soil", "basalt", "blackstone", "gilded_blackstone",
            // End blocks
            "end_stone", "end_stone_bricks", "purpur_block", "purpur_pillar",
            // Decorative
            "oak_stairs", "stone_stairs", "brick_stairs", "sandstone_stairs",
            "oak_slab", "stone_slab", "brick_slab", "sandstone_slab",
            "oak_fence", "stone_brick_fence", "nether_brick_fence",
            "oak_door", "iron_door",
            // Misc
            "sandstone", "red_sandstone", "prismarine", "dark_prismarine", "sea_pickle");

    /** Alchemod custom blocks usable in World Sketcher builds. */
    public static final List<String> ALCHEMOD_PALETTE_BLOCKS = List.of(
            "alchemod:arcane_bricks",
            "alchemod:void_stone",
            "alchemod:ether_crystal",
            "alchemod:glowstone_bricks",
            "alchemod:reinforced_obsidian",
            "alchemod:alchemical_glass");

    private static final Set<String> SIMPLE_PALETTE;

    static {
        var combined = new java.util.HashSet<String>();
        SIMPLE_PALETTE_BLOCKS.stream()
                .map(b -> "minecraft:" + b)
                .forEach(combined::add);
        combined.addAll(ALCHEMOD_PALETTE_BLOCKS);
        SIMPLE_PALETTE = Set.copyOf(combined);
    }

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
            throw new Error("[Builder] Script exceeded the safe instruction budget.");
        }
    };

    private BuilderRuntime() {
    }

    public static ExecutionResult execute(BuilderProgram program, int fallbackSeed, PlacementSink sink) {
        int seed = program.seed() != null ? (int)(long) program.seed() : fallbackSeed;
        PlacementController controller = new PlacementController(sink);

        if (program.legacyFallback()) {
            executeLegacyCommands(program.code(), controller);
            return new ExecutionResult(controller.placements(), seed, true);
        }

        validateProgram(program);
        executeScript(program.code(), seed, controller);
        return new ExecutionResult(controller.placements(), seed, false);
    }

    private static void validateProgram(BuilderProgram program) {
        if (!PALETTE_NAME.equals(program.palette())) {
            throw new IllegalArgumentException("Builder program must use palette " + PALETTE_NAME);
        }
        if (program.bounds() == null) {
            throw new IllegalArgumentException("Builder program is missing bounds");
        }
    }

    private static String cleanAiCode(String code) {
        String cleaned = code;
        cleaned = cleaned.replaceAll("const\\s+", "");
        cleaned = cleaned.replaceAll("let\\s+", "");
        cleaned = cleaned.replaceAll("var\\s+", "");
        cleaned = cleaned.replaceAll("function\\s+\\w+\\s*\\([^)]*\\)\\s*\\{", "");
        cleaned = cleaned.replaceAll("\\[", "");
        cleaned = cleaned.replaceAll("\\]", "");
        cleaned = cleaned.replaceAll("rng\\.noise\\d*", "rng");
        cleaned = cleaned.replaceAll("\\.\\.\\.", "");
        cleaned = cleaned.replaceAll("Math\\.sin\\([^)]+\\)", "0");
        cleaned = cleaned.replaceAll("Math\\.cos\\([^)]+\\)", "0");
        cleaned = cleaned.replaceAll("Math\\.sqrt\\([^)]+\\)", "0");
        cleaned = cleaned.replaceAll("Math\\.pow\\([^)]+\\)", "0");
        return cleaned;
    }

    private static void executeScript(String code, int seed, PlacementController controller) {
        // BUG FIX: cleanAiCode() was computed but the original `code` was passed to
        // evaluateString instead of `cleaned`, making the whole sanitisation step a no-op.
        String cleaned = cleanAiCode(code);

        Context context = FACTORY.enterContext();
        try {
            Scriptable scope = context.initSafeStandardObjects();
            Random random = new Random(seed);

            ScriptableObject.putProperty(scope, "rng", new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable callableScope, Scriptable thisObj, Object[] args) {
                    return random.nextDouble();
                }
            });

            ScriptableObject.putProperty(scope, "block", primitiveFunction((args) -> {
                if (args.length != 4) {
                    throw new IllegalArgumentException("block() expects 4 arguments");
                }
                controller.place(
                        toInt(args[0]),
                        toInt(args[1]),
                        toInt(args[2]),
                        normaliseBlockId(Context.toString(args[3])));
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "box", primitiveFunction((args) -> {
                if (args.length != 7) {
                    throw new IllegalArgumentException("box() expects 7 arguments");
                }
                int x1 = toInt(args[0]);
                int y1 = toInt(args[1]);
                int z1 = toInt(args[2]);
                int x2 = toInt(args[3]);
                int y2 = toInt(args[4]);
                int z2 = toInt(args[5]);
                String blockId = normaliseBlockId(Context.toString(args[6]));

                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                        for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                            controller.place(x, y, z, blockId);
                        }
                    }
                }
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "line", primitiveFunction((args) -> {
                if (args.length != 7) {
                    throw new IllegalArgumentException("line() expects 7 arguments");
                }
                int x1 = toInt(args[0]);
                int y1 = toInt(args[1]);
                int z1 = toInt(args[2]);
                int x2 = toInt(args[3]);
                int y2 = toInt(args[4]);
                int z2 = toInt(args[5]);
                String blockId = normaliseBlockId(Context.toString(args[6]));
                int steps = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1));
                steps = Math.max(steps, 1);

                for (int step = 0; step <= steps; step++) {
                    double t = step / (double) steps;
                    int x = (int) Math.floor(x1 + ((x2 - x1) * t));
                    int y = (int) Math.floor(y1 + ((y2 - y1) * t));
                    int z = (int) Math.floor(z1 + ((z2 - z1) * t));
                    controller.place(x, y, z, blockId);
                }
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "sphere", primitiveFunction((args) -> {
                if (args.length != 5) {
                    throw new IllegalArgumentException("sphere() expects 5 arguments");
                }
                int centerX = toInt(args[0]);
                int centerY = toInt(args[1]);
                int centerZ = toInt(args[2]);
                int radius = Math.abs(toInt(args[3]));
                String blockId = normaliseBlockId(Context.toString(args[4]));

                if (radius > MAX_SPHERE_RADIUS) {
                    throw new IllegalArgumentException("sphere() radius exceeds the safe limit of " + MAX_SPHERE_RADIUS);
                }

                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            double distance = Math.sqrt((x * x) + (y * y) + (z * z));
                            if (distance <= radius && distance >= radius - 1.2) {
                                controller.place(centerX + x, centerY + y, centerZ + z, blockId);
                            }
                        }
                    }
                }
                return Undefined.instance;
            }));

            // BUG FIX: was `code` — now correctly passes the sanitised string
            context.evaluateString(scope, cleaned, "builder_program", 1, null);
        } catch (RhinoException e) {
            throw new IllegalArgumentException("Builder script runtime error: " + e.getLocalizedMessage(), e);
        } catch (Error e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } finally {
            Context.exit();
        }
    }

    private static BaseFunction primitiveFunction(Primitive primitive) {
        return new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable callableScope, Scriptable thisObj, Object[] args) {
                return primitive.call(args);
            }
        };
    }

    private static void executeLegacyCommands(String code, PlacementController controller) {
        List<String> commands = sanitiseLegacyCommands(code);
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("Legacy builder response returned no valid commands");
        }

        for (String command : commands) {
            int parenIndex = command.indexOf('(');
            int closeIndex = command.lastIndexOf(')');
            if (parenIndex <= 0 || closeIndex <= parenIndex) {
                throw new IllegalArgumentException("Invalid builder command: " + command);
            }

            String name = command.substring(0, parenIndex).trim().toLowerCase(Locale.ROOT);
            List<String> args = splitArgs(command.substring(parenIndex + 1, closeIndex));
            switch (name) {
                case "block" -> executeLegacyBlock(args, controller);
                case "box" -> executeLegacyBox(args, controller);
                case "line" -> executeLegacyLine(args, controller);
                case "sphere" -> executeLegacySphere(args, controller);
                default -> throw new IllegalArgumentException("Unsupported builder command: " + name);
            }
        }
    }

    private static void executeLegacyBlock(List<String> args, PlacementController controller) {
        if (args.size() != 4) throw new IllegalArgumentException("block() expects 4 arguments");
        controller.place(
                parseInt(args.get(0)), parseInt(args.get(1)), parseInt(args.get(2)),
                normaliseBlockId(stripQuotes(args.get(3))));
    }

    private static void executeLegacyBox(List<String> args, PlacementController controller) {
        if (args.size() != 7) throw new IllegalArgumentException("box() expects 7 arguments");
        int x1 = parseInt(args.get(0)), y1 = parseInt(args.get(1)), z1 = parseInt(args.get(2));
        int x2 = parseInt(args.get(3)), y2 = parseInt(args.get(4)), z2 = parseInt(args.get(5));
        String blockId = normaliseBlockId(stripQuotes(args.get(6)));
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    controller.place(x, y, z, blockId);
    }

    private static void executeLegacyLine(List<String> args, PlacementController controller) {
        if (args.size() != 7) throw new IllegalArgumentException("line() expects 7 arguments");
        int x1 = parseInt(args.get(0)), y1 = parseInt(args.get(1)), z1 = parseInt(args.get(2));
        int x2 = parseInt(args.get(3)), y2 = parseInt(args.get(4)), z2 = parseInt(args.get(5));
        String blockId = normaliseBlockId(stripQuotes(args.get(6)));
        int steps = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1));
        steps = Math.max(steps, 1);
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            controller.place((int) Math.floor(x1 + (x2 - x1) * t),
                             (int) Math.floor(y1 + (y2 - y1) * t),
                             (int) Math.floor(z1 + (z2 - z1) * t), blockId);
        }
    }

    private static void executeLegacySphere(List<String> args, PlacementController controller) {
        if (args.size() != 5) throw new IllegalArgumentException("sphere() expects 5 arguments");
        int cx = parseInt(args.get(0)), cy = parseInt(args.get(1)), cz = parseInt(args.get(2));
        int radius = Math.abs(parseInt(args.get(3)));
        String blockId = normaliseBlockId(stripQuotes(args.get(4)));
        if (radius > MAX_SPHERE_RADIUS)
            throw new IllegalArgumentException("sphere() radius exceeds the safe limit of " + MAX_SPHERE_RADIUS);
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++) {
                    double d = Math.sqrt(x*x + y*y + z*z);
                    if (d <= radius && d >= radius - 1.2)
                        controller.place(cx + x, cy + y, cz + z, blockId);
                }
    }

    private static List<String> sanitiseLegacyCommands(String code) {
        String[] lines = BuilderResponseParser.stripCodeFence(code).split("\\R");
        List<String> commands = new ArrayList<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            if (line.endsWith(";")) line = line.substring(0, line.length() - 1).trim();
            if (!line.matches("[a-zA-Z_]+\\s*\\(.*\\)")) continue;
            commands.add(line);
        }
        return commands;
    }

    private static List<String> splitArgs(String rawArgs) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        char stringDelimiter = 0;
        for (int i = 0; i < rawArgs.length(); i++) {
            char c = rawArgs.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || rawArgs.charAt(i - 1) != '\\')) {
                if (!inString) { inString = true; stringDelimiter = c; }
                else if (stringDelimiter == c) { inString = false; }
                current.append(c);
                continue;
            }
            if (c == ',' && !inString) {
                args.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) args.add(current.toString().trim());
        return args;
    }

    private static int parseInt(String value) { return Integer.parseInt(value.trim()); }
    private static int toInt(Object value) { return (int) Math.round(Context.toNumber(value)); }
    private static String stripQuotes(String value) { return value.trim().replace("\"", "").replace("'", ""); }

    private static String normaliseBlockId(String blockId) {
        String cleaned = blockId.trim().toLowerCase(Locale.ROOT);
        if (cleaned.contains(":")) {
            if (!SIMPLE_PALETTE.contains(cleaned))
                throw new IllegalArgumentException("Builder palette does not allow block " + cleaned);
            return cleaned;
        }
        String withMinecraft = "minecraft:" + cleaned;
        if (SIMPLE_PALETTE.contains(withMinecraft)) return withMinecraft;
        String withAlchemod = "alchemod:" + cleaned;
        if (SIMPLE_PALETTE.contains(withAlchemod)) return withAlchemod;
        throw new IllegalArgumentException("Builder palette does not allow block " + cleaned);
    }

    public interface PlacementSink { void place(int x, int y, int z, String blockId); }
    public record ExecutionResult(int placements, int seedUsed, boolean legacyFallback) {}
    private interface Primitive { Object call(Object[] args); }

    private static final class PlacementController {
        private final PlacementSink sink;
        private int placements;

        private PlacementController(PlacementSink sink) { this.sink = sink; }

        private void place(int x, int y, int z, String blockId) {
            validateRelativePosition(x, y, z);
            if (placements >= MAX_BLOCK_PLACEMENTS)
                throw new IllegalArgumentException("Generated structure exceeded safe block budget");
            placements++;
            sink.place(x, y, z, blockId);
        }

        private int placements() { return placements; }
    }

    private static void validateRelativePosition(int x, int y, int z) {
        if (Math.abs(x) > MAX_XZ_OFFSET || Math.abs(z) > MAX_XZ_OFFSET || y < MIN_Y_OFFSET || y > MAX_Y_OFFSET)
            throw new IllegalArgumentException("Generated structure exceeded the safe build bounds");
    }
}
