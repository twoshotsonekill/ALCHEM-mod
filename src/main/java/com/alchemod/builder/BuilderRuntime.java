package com.alchemod.builder;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.util.ArrayList;
import java.util.HashSet;
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
    public static final int MAX_CYLINDER_RADIUS = 16;
    public static final int MAX_HELPER_SPAN = 80;
    public static final int INSTRUCTION_BUDGET = 1_500_000;
    public static final int MIN_QUALITY_PLACEMENTS = 900;
    public static final int MIN_QUALITY_SHAPE_VARIETY = 3;
    public static final int MIN_QUALITY_UNIQUE_BLOCKS = 3;
    public static final int MIN_QUALITY_MAJOR_HORIZONTAL_SPAN = 12;
    public static final int MIN_QUALITY_DEPTH_SPAN = 5;
    public static final int MIN_QUALITY_HEIGHT_SPAN = 6;

    public static final List<String> SIMPLE_PALETTE_BLOCKS = List.of(
            "stone", "cobblestone", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks",
            "deepslate", "deepslate_bricks", "deepslate_tiles",
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
            "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
            "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves",
            "bricks", "mud_bricks", "nether_bricks", "red_nether_bricks",
            "grass_block", "dirt", "coarse_dirt", "rooted_dirt", "sand", "red_sand",
            "gravel", "clay", "mud", "dripstone_block",
            "coal_ore", "copper_ore", "iron_ore", "gold_ore", "diamond_ore", "emerald_ore", "lapis_ore", "redstone_ore",
            "iron_block", "gold_block", "diamond_block", "emerald_block", "lapis_block", "redstone_block", "copper_block",
            "glass", "tinted_glass",
            "glowstone", "sea_lantern", "shroomlight", "amethyst_block",
            "white_wool", "black_wool", "red_wool", "blue_wool", "green_wool",
            "yellow_wool", "orange_wool", "purple_wool", "brown_wool", "gray_wool",
            "light_gray_wool", "cyan_wool", "lime_wool", "pink_wool", "magenta_wool",
            "white_concrete", "black_concrete", "red_concrete", "blue_concrete", "green_concrete",
            "yellow_concrete", "orange_concrete", "purple_concrete", "brown_concrete", "gray_concrete",
            "light_gray_concrete", "cyan_concrete", "lime_concrete", "pink_concrete", "magenta_concrete",
            "terracotta", "white_terracotta", "black_terracotta", "red_terracotta", "blue_terracotta",
            "netherrack", "soul_sand", "soul_soil", "basalt", "blackstone", "gilded_blackstone",
            "end_stone", "end_stone_bricks", "purpur_block", "purpur_pillar",
            "oak_stairs", "stone_stairs", "brick_stairs", "sandstone_stairs",
            "oak_slab", "stone_slab", "brick_slab", "sandstone_slab",
            "oak_fence", "stone_brick_fence", "nether_brick_fence",
            "oak_door", "iron_door",
            "sandstone", "red_sandstone", "prismarine", "dark_prismarine", "sea_pickle");

    public static final List<String> ALCHEMOD_PALETTE_BLOCKS = List.of(
            "alchemod:arcane_bricks",
            "alchemod:void_stone",
            "alchemod:ether_crystal",
            "alchemod:glowstone_bricks",
            "alchemod:reinforced_obsidian",
            "alchemod:alchemical_glass");

    private static final Set<String> SIMPLE_PALETTE;

    static {
        Set<String> combined = new HashSet<>();
        SIMPLE_PALETTE_BLOCKS.stream()
                .map(block -> "minecraft:" + block)
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
        PlacementPreview preview = preview(program, fallbackSeed);
        preview.replay(sink);
        return new ExecutionResult(preview.placementCount(), preview.seedUsed(), preview.legacyFallback());
    }

    public static PlacementPreview preview(BuilderProgram program, int fallbackSeed) {
        int seed = program.seed() != null ? (int) (long) program.seed() : fallbackSeed;
        List<Placement> placements = new ArrayList<>();
        PlacementController controller = new PlacementController(
                (x, y, z, blockId) -> placements.add(new Placement(x, y, z, blockId)));

        if (program.legacyFallback()) {
            executeLegacyCommands(program.code(), controller);
            return new PlacementPreview(
                    placements, seed, true, controller.primitiveVariety(), controller.uniqueBlockCount());
        }

        validateProgram(program);
        executeScript(program.code(), seed, controller);
        return new PlacementPreview(
                placements, seed, false, controller.primitiveVariety(), controller.uniqueBlockCount());
    }

    public static QualityReport assessQuality(PlacementPreview preview) {
        if (preview.placementCount() < MIN_QUALITY_PLACEMENTS) {
            return QualityReport.rejected("too few placements: " + preview.placementCount());
        }
        if (preview.primitiveVariety() < MIN_QUALITY_SHAPE_VARIETY) {
            return QualityReport.rejected("too little shape variety: " + preview.primitiveVariety());
        }
        if (preview.uniqueBlockCount() < MIN_QUALITY_UNIQUE_BLOCKS) {
            return QualityReport.rejected("too few materials: " + preview.uniqueBlockCount());
        }

        SpatialFootprint footprint = SpatialFootprint.from(preview.placements());
        if (footprint.majorHorizontalSpan() < MIN_QUALITY_MAJOR_HORIZONTAL_SPAN) {
            return QualityReport.rejected("too little horizontal scale: " + footprint.majorHorizontalSpan());
        }
        if (footprint.depthSpan() < MIN_QUALITY_DEPTH_SPAN) {
            return QualityReport.rejected("too little 3D depth: " + footprint.depthSpan());
        }
        if (footprint.heightSpan() < MIN_QUALITY_HEIGHT_SPAN) {
            return QualityReport.rejected("too little height variation: " + footprint.heightSpan());
        }
        return QualityReport.pass();
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
        String cleaned = code == null ? "" : code;
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

            ScriptableObject.putProperty(scope, "block", primitiveFunction("block", controller, args -> {
                if (args.length != 4) {
                    throw new IllegalArgumentException("block() expects 4 arguments");
                }
                controller.place(toInt(args[0]), toInt(args[1]), toInt(args[2]),
                        normaliseBlockId(Context.toString(args[3])));
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "box", primitiveFunction("box", controller, args -> {
                if (args.length != 7) {
                    throw new IllegalArgumentException("box() expects 7 arguments");
                }
                placeBox(controller,
                        toInt(args[0]), toInt(args[1]), toInt(args[2]),
                        toInt(args[3]), toInt(args[4]), toInt(args[5]),
                        normaliseBlockId(Context.toString(args[6])));
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "line", primitiveFunction("line", controller, args -> {
                if (args.length != 7) {
                    throw new IllegalArgumentException("line() expects 7 arguments");
                }
                placeLine(controller,
                        toInt(args[0]), toInt(args[1]), toInt(args[2]),
                        toInt(args[3]), toInt(args[4]), toInt(args[5]),
                        normaliseBlockId(Context.toString(args[6])));
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "sphere", primitiveFunction("sphere", controller, args -> {
                if (args.length != 5) {
                    throw new IllegalArgumentException("sphere() expects 5 arguments");
                }
                placeSphere(controller,
                        toInt(args[0]), toInt(args[1]), toInt(args[2]),
                        Math.abs(toInt(args[3])),
                        normaliseBlockId(Context.toString(args[4])));
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "hollowBox", primitiveFunction("hollowBox", controller, args -> {
                if (args.length != 7) {
                    throw new IllegalArgumentException("hollowBox() expects 7 arguments");
                }
                placeHollowBox(controller,
                        toInt(args[0]), toInt(args[1]), toInt(args[2]),
                        toInt(args[3]), toInt(args[4]), toInt(args[5]),
                        normaliseBlockId(Context.toString(args[6])));
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "cylinder", primitiveFunction("cylinder", controller, args -> {
                if (args.length != 6) {
                    throw new IllegalArgumentException("cylinder() expects 6 arguments");
                }
                placeCylinder(controller,
                        toInt(args[0]), toInt(args[1]), toInt(args[2]),
                        Math.abs(toInt(args[3])), Math.abs(toInt(args[4])),
                        normaliseBlockId(Context.toString(args[5])));
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "dome", primitiveFunction("dome", controller, args -> {
                if (args.length != 5) {
                    throw new IllegalArgumentException("dome() expects 5 arguments");
                }
                placeDome(controller,
                        toInt(args[0]), toInt(args[1]), toInt(args[2]),
                        Math.abs(toInt(args[3])),
                        normaliseBlockId(Context.toString(args[4])));
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "pillar", primitiveFunction("pillar", controller, args -> {
                if (args.length != 5) {
                    throw new IllegalArgumentException("pillar() expects 5 arguments");
                }
                int height = toInt(args[3]);
                placeLine(controller,
                        toInt(args[0]), toInt(args[1]), toInt(args[2]),
                        toInt(args[0]), toInt(args[1]) + Math.max(0, height - 1), toInt(args[2]),
                        normaliseBlockId(Context.toString(args[4])));
                return Undefined.instance;
            }));

            ScriptableObject.putProperty(scope, "stairs", primitiveFunction("stairs", controller, args -> {
                if (args.length != 6 && args.length != 7) {
                    throw new IllegalArgumentException("stairs() expects 6 or 7 arguments");
                }
                int width = args.length == 7 ? Math.abs(toInt(args[3])) : 3;
                int steps = args.length == 7 ? Math.abs(toInt(args[4])) : Math.abs(toInt(args[3]));
                String direction = Context.toString(args.length == 7 ? args[5] : args[4]);
                String blockId = normaliseBlockId(Context.toString(args.length == 7 ? args[6] : args[5]));
                placeStairs(controller, toInt(args[0]), toInt(args[1]), toInt(args[2]), width, steps, direction, blockId);
                return Undefined.instance;
            }));

            context.evaluateString(scope, cleaned, "builder_program", 1, null);
        } catch (RhinoException e) {
            throw new IllegalArgumentException("Builder script runtime error: " + e.getLocalizedMessage(), e);
        } catch (Error e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } finally {
            Context.exit();
        }
    }

    private static BaseFunction primitiveFunction(String name, PlacementController controller, Primitive primitive) {
        return new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable callableScope, Scriptable thisObj, Object[] args) {
                controller.recordPrimitive(name);
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
            controller.recordPrimitive(name);
            switch (name) {
                case "block" -> executeLegacyBlock(args, controller);
                case "box" -> executeLegacyBox(args, controller);
                case "line" -> executeLegacyLine(args, controller);
                case "sphere" -> executeLegacySphere(args, controller);
                case "hollowbox", "hollow_box" -> executeLegacyHollowBox(args, controller);
                case "cylinder" -> executeLegacyCylinder(args, controller);
                case "dome" -> executeLegacyDome(args, controller);
                case "pillar" -> executeLegacyPillar(args, controller);
                case "stairs" -> executeLegacyStairs(args, controller);
                default -> throw new IllegalArgumentException("Unsupported builder command: " + name);
            }
        }
    }

    private static void executeLegacyBlock(List<String> args, PlacementController controller) {
        if (args.size() != 4) throw new IllegalArgumentException("block() expects 4 arguments");
        controller.place(parseInt(args.get(0)), parseInt(args.get(1)), parseInt(args.get(2)),
                normaliseBlockId(stripQuotes(args.get(3))));
    }

    private static void executeLegacyBox(List<String> args, PlacementController controller) {
        if (args.size() != 7) throw new IllegalArgumentException("box() expects 7 arguments");
        placeBox(controller,
                parseInt(args.get(0)), parseInt(args.get(1)), parseInt(args.get(2)),
                parseInt(args.get(3)), parseInt(args.get(4)), parseInt(args.get(5)),
                normaliseBlockId(stripQuotes(args.get(6))));
    }

    private static void executeLegacyLine(List<String> args, PlacementController controller) {
        if (args.size() != 7) throw new IllegalArgumentException("line() expects 7 arguments");
        placeLine(controller,
                parseInt(args.get(0)), parseInt(args.get(1)), parseInt(args.get(2)),
                parseInt(args.get(3)), parseInt(args.get(4)), parseInt(args.get(5)),
                normaliseBlockId(stripQuotes(args.get(6))));
    }

    private static void executeLegacySphere(List<String> args, PlacementController controller) {
        if (args.size() != 5) throw new IllegalArgumentException("sphere() expects 5 arguments");
        placeSphere(controller,
                parseInt(args.get(0)), parseInt(args.get(1)), parseInt(args.get(2)),
                Math.abs(parseInt(args.get(3))),
                normaliseBlockId(stripQuotes(args.get(4))));
    }

    private static void executeLegacyHollowBox(List<String> args, PlacementController controller) {
        if (args.size() != 7) throw new IllegalArgumentException("hollowBox() expects 7 arguments");
        placeHollowBox(controller,
                parseInt(args.get(0)), parseInt(args.get(1)), parseInt(args.get(2)),
                parseInt(args.get(3)), parseInt(args.get(4)), parseInt(args.get(5)),
                normaliseBlockId(stripQuotes(args.get(6))));
    }

    private static void executeLegacyCylinder(List<String> args, PlacementController controller) {
        if (args.size() != 6) throw new IllegalArgumentException("cylinder() expects 6 arguments");
        placeCylinder(controller,
                parseInt(args.get(0)), parseInt(args.get(1)), parseInt(args.get(2)),
                Math.abs(parseInt(args.get(3))), Math.abs(parseInt(args.get(4))),
                normaliseBlockId(stripQuotes(args.get(5))));
    }

    private static void executeLegacyDome(List<String> args, PlacementController controller) {
        if (args.size() != 5) throw new IllegalArgumentException("dome() expects 5 arguments");
        placeDome(controller,
                parseInt(args.get(0)), parseInt(args.get(1)), parseInt(args.get(2)),
                Math.abs(parseInt(args.get(3))),
                normaliseBlockId(stripQuotes(args.get(4))));
    }

    private static void executeLegacyPillar(List<String> args, PlacementController controller) {
        if (args.size() != 5) throw new IllegalArgumentException("pillar() expects 5 arguments");
        int x = parseInt(args.get(0));
        int y = parseInt(args.get(1));
        int z = parseInt(args.get(2));
        int height = Math.max(0, parseInt(args.get(3)));
        placeLine(controller, x, y, z, x, y + Math.max(0, height - 1), z, normaliseBlockId(stripQuotes(args.get(4))));
    }

    private static void executeLegacyStairs(List<String> args, PlacementController controller) {
        if (args.size() != 6 && args.size() != 7) throw new IllegalArgumentException("stairs() expects 6 or 7 arguments");
        int width = args.size() == 7 ? Math.abs(parseInt(args.get(3))) : 3;
        int steps = args.size() == 7 ? Math.abs(parseInt(args.get(4))) : Math.abs(parseInt(args.get(3)));
        String direction = stripQuotes(args.size() == 7 ? args.get(5) : args.get(4));
        String blockId = normaliseBlockId(stripQuotes(args.size() == 7 ? args.get(6) : args.get(5)));
        placeStairs(controller, parseInt(args.get(0)), parseInt(args.get(1)), parseInt(args.get(2)),
                width, steps, direction, blockId);
    }

    private static void placeBox(PlacementController controller,
            int x1, int y1, int z1, int x2, int y2, int z2, String blockId) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        validateSpan(minX, maxX, "x");
        validateSpan(minY, maxY, "y");
        validateSpan(minZ, maxZ, "z");
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    controller.place(x, y, z, blockId);
                }
            }
        }
    }

    private static void placeHollowBox(PlacementController controller,
            int x1, int y1, int z1, int x2, int y2, int z2, String blockId) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        validateSpan(minX, maxX, "x");
        validateSpan(minY, maxY, "y");
        validateSpan(minZ, maxZ, "z");
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean shell = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                    if (shell) {
                        controller.place(x, y, z, blockId);
                    }
                }
            }
        }
    }

    private static void placeLine(PlacementController controller,
            int x1, int y1, int z1, int x2, int y2, int z2, String blockId) {
        int steps = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1));
        steps = Math.max(steps, 1);
        if (steps > MAX_HELPER_SPAN) {
            throw new IllegalArgumentException("line() exceeds the safe helper span");
        }

        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int x = (int) Math.floor(x1 + ((x2 - x1) * t));
            int y = (int) Math.floor(y1 + ((y2 - y1) * t));
            int z = (int) Math.floor(z1 + ((z2 - z1) * t));
            controller.place(x, y, z, blockId);
        }
    }

    private static void placeSphere(PlacementController controller,
            int centerX, int centerY, int centerZ, int radius, String blockId) {
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
    }

    private static void placeCylinder(PlacementController controller,
            int centerX, int baseY, int centerZ, int radius, int height, String blockId) {
        if (radius > MAX_CYLINDER_RADIUS) {
            throw new IllegalArgumentException("cylinder() radius exceeds the safe limit of " + MAX_CYLINDER_RADIUS);
        }
        if (height > MAX_HELPER_SPAN) {
            throw new IllegalArgumentException("cylinder() height exceeds the safe helper span");
        }
        int radiusSquared = radius * radius;
        for (int y = baseY; y < baseY + Math.max(1, height); y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if ((x * x) + (z * z) <= radiusSquared) {
                        controller.place(centerX + x, y, centerZ + z, blockId);
                    }
                }
            }
        }
    }

    private static void placeDome(PlacementController controller,
            int centerX, int baseY, int centerZ, int radius, String blockId) {
        if (radius > MAX_SPHERE_RADIUS) {
            throw new IllegalArgumentException("dome() radius exceeds the safe limit of " + MAX_SPHERE_RADIUS);
        }

        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt((x * x) + (y * y) + (z * z));
                    if (distance <= radius && distance >= radius - 1.2) {
                        controller.place(centerX + x, baseY + y, centerZ + z, blockId);
                    }
                }
            }
        }
    }

    private static void placeStairs(PlacementController controller,
            int x, int y, int z, int width, int steps, String rawDirection, String blockId) {
        if (steps > MAX_HELPER_SPAN || width > MAX_HELPER_SPAN) {
            throw new IllegalArgumentException("stairs() exceeds the safe helper span");
        }
        String direction = rawDirection == null ? "north" : rawDirection.toLowerCase(Locale.ROOT).trim();
        int dx = switch (direction) {
            case "east" -> 1;
            case "west" -> -1;
            default -> 0;
        };
        int dz = switch (direction) {
            case "south" -> 1;
            case "north" -> -1;
            default -> 0;
        };
        if (dx == 0 && dz == 0) {
            dz = -1;
        }

        int half = Math.max(1, width) / 2;
        for (int step = 0; step < Math.max(1, steps); step++) {
            for (int offset = -half; offset <= half; offset++) {
                int px = x + dx * step + (dz != 0 ? offset : 0);
                int pz = z + dz * step + (dx != 0 ? offset : 0);
                controller.place(px, y + step, pz, blockId);
            }
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
                if (!inString) {
                    inString = true;
                    stringDelimiter = c;
                } else if (stringDelimiter == c) {
                    inString = false;
                }
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

    private static int parseInt(String value) {
        return Integer.parseInt(value.trim());
    }

    private static int toInt(Object value) {
        return (int) Math.round(Context.toNumber(value));
    }

    private static String stripQuotes(String value) {
        return value.trim().replace("\"", "").replace("'", "");
    }

    private static String normaliseBlockId(String blockId) {
        String cleaned = blockId.trim().toLowerCase(Locale.ROOT);
        if (cleaned.contains(":")) {
            if (!SIMPLE_PALETTE.contains(cleaned)) {
                throw new IllegalArgumentException("Builder palette does not allow block " + cleaned);
            }
            return cleaned;
        }
        String withMinecraft = "minecraft:" + cleaned;
        if (SIMPLE_PALETTE.contains(withMinecraft)) return withMinecraft;
        String withAlchemod = "alchemod:" + cleaned;
        if (SIMPLE_PALETTE.contains(withAlchemod)) return withAlchemod;
        throw new IllegalArgumentException("Builder palette does not allow block " + cleaned);
    }

    private static void validateSpan(int min, int max, String axis) {
        if (Math.abs(max - min) > MAX_HELPER_SPAN) {
            throw new IllegalArgumentException(axis + " span exceeds the safe helper span");
        }
    }

    private static void validateRelativePosition(int x, int y, int z) {
        if (Math.abs(x) > MAX_XZ_OFFSET || Math.abs(z) > MAX_XZ_OFFSET
                || y < MIN_Y_OFFSET || y > MAX_Y_OFFSET) {
            throw new IllegalArgumentException("Generated structure exceeded the safe build bounds");
        }
    }

    public interface PlacementSink {
        void place(int x, int y, int z, String blockId);
    }

    public record Placement(int x, int y, int z, String blockId) {
    }

    public record PlacementPreview(
            List<Placement> placements,
            int seedUsed,
            boolean legacyFallback,
            int primitiveVariety,
            int uniqueBlockCount
    ) {
        public PlacementPreview {
            placements = List.copyOf(placements);
        }

        public int placementCount() {
            return placements.size();
        }

        public void replay(PlacementSink sink) {
            for (Placement placement : placements) {
                sink.place(placement.x(), placement.y(), placement.z(), placement.blockId());
            }
        }
    }

    public record QualityReport(boolean accepted, String reason) {
        public static QualityReport pass() {
            return new QualityReport(true, "");
        }

        public static QualityReport rejected(String reason) {
            return new QualityReport(false, reason);
        }
    }

    private record SpatialFootprint(int xSpan, int ySpan, int zSpan) {
        static SpatialFootprint from(List<Placement> placements) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (Placement placement : placements) {
                minX = Math.min(minX, placement.x());
                maxX = Math.max(maxX, placement.x());
                minY = Math.min(minY, placement.y());
                maxY = Math.max(maxY, placement.y());
                minZ = Math.min(minZ, placement.z());
                maxZ = Math.max(maxZ, placement.z());
            }

            if (placements.isEmpty()) {
                return new SpatialFootprint(0, 0, 0);
            }
            return new SpatialFootprint(maxX - minX, maxY - minY, maxZ - minZ);
        }

        int majorHorizontalSpan() {
            return Math.max(xSpan, zSpan);
        }

        int depthSpan() {
            return Math.min(xSpan, zSpan);
        }

        int heightSpan() {
            return ySpan;
        }
    }

    public record ExecutionResult(int placements, int seedUsed, boolean legacyFallback) {
    }

    private interface Primitive {
        Object call(Object[] args);
    }

    private static final class PlacementController {
        private final PlacementSink sink;
        private final Set<String> primitiveNames = new HashSet<>();
        private final Set<String> blockIds = new HashSet<>();
        private int placements;

        private PlacementController(PlacementSink sink) {
            this.sink = sink;
        }

        private void recordPrimitive(String primitiveName) {
            primitiveNames.add(primitiveName.toLowerCase(Locale.ROOT));
        }

        private void place(int x, int y, int z, String blockId) {
            validateRelativePosition(x, y, z);
            if (placements >= MAX_BLOCK_PLACEMENTS) {
                throw new IllegalArgumentException("Generated structure exceeded safe block budget");
            }
            placements++;
            blockIds.add(blockId);
            sink.place(x, y, z, blockId);
        }

        private int primitiveVariety() {
            return primitiveNames.size();
        }

        private int uniqueBlockCount() {
            return blockIds.size();
        }
    }
}
