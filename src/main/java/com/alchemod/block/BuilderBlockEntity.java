package com.alchemod.block;

import com.alchemod.AlchemodInit;
import com.alchemod.screen.BuilderScreenHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class BuilderBlockEntity extends BlockEntity implements NamedScreenHandlerFactory, Inventory {

    public static final int SLOT_A = 0;
    public static final int SLOT_B = 1;

    public static final int STATE_IDLE = 0;
    public static final int STATE_PROCESSING = 1;
    public static final int STATE_BUILDING = 2;
    public static final int STATE_COMPLETE = 3;
    public static final int STATE_ERROR = 4;

    public static final int MODE_BLOCK = 0;
    public static final int MODE_TEXT = 1;

    private static final int MAX_PROGRESS = 100;
    private static final int MAX_BLOCK_PLACEMENTS = 4096;
    private static final int MAX_STRUCTURE_COMMANDS = 160;
    private static final int MAX_XZ_OFFSET = 24;
    private static final int MIN_Y_OFFSET = -4;
    private static final int MAX_Y_OFFSET = 32;
    private static final int MAX_SPHERE_RADIUS = 10;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);

    private int state = STATE_IDLE;
    private int progress = 0;
    private String promptText = "";
    private String lastError = "";
    private boolean aiPending = false;
    private int builderMode = MODE_TEXT;

    private final PropertyDelegate delegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> state;
                case 1 -> progress;
                case 2 -> builderMode;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> state = value;
                case 1 -> progress = value;
                case 2 -> builderMode = value;
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return 3;
        }
    };

    public BuilderBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemodInit.BUILDER_BE_TYPE, pos, state);
    }

    public void serverTick(World world, BlockPos pos) {
        if (state == STATE_IDLE
                && builderMode == MODE_BLOCK
                && !items.get(SLOT_A).isEmpty()
                && !items.get(SLOT_B).isEmpty()
                && !aiPending) {
            startBuild(buildBlockPrompt(), world);
        }

        if (state == STATE_PROCESSING) {
            progress = Math.min(progress + 1, MAX_PROGRESS - 1);
            markDirty();
        }
    }

    public void startBuild(String prompt, World world) {
        if (aiPending || prompt == null || prompt.isBlank()) {
            return;
        }

        promptText = prompt.trim();
        aiPending = true;
        state = STATE_PROCESSING;
        progress = 0;
        lastError = "";
        markDirty();

        CompletableFuture.supplyAsync(() -> generateStructureCode(promptText))
                .thenAccept(code -> {
                    if (world.getServer() != null) {
                        world.getServer().execute(() -> executeStructureCode(code, world));
                    }
                });
    }

    public void receivePrompt(String prompt, World world) {
        if (builderMode == MODE_TEXT && prompt != null && !prompt.isBlank()) {
            startBuild(prompt, world);
        }
    }

    public void setBuilderMode(int mode) {
        builderMode = mode == MODE_BLOCK ? MODE_BLOCK : MODE_TEXT;
        markDirty();
    }

    public int getBuilderMode() {
        return builderMode;
    }

    private String buildBlockPrompt() {
        return "Create a large Minecraft landmark inspired by "
                + items.get(SLOT_A).getName().getString()
                + " and "
                + items.get(SLOT_B).getName().getString()
                + ". Make it bold, multi-part, and readable from far away with layered foundations, clear vertical shapes, and real architectural detail instead of a tiny prop.";
    }

    private String generateStructureCode(String prompt) {
        String key = AlchemodInit.OPENROUTER_KEY;
        if (key.isBlank()) {
            return "ERROR:OPENROUTER_API_KEY not set";
        }

        String system = """
You are a Minecraft builder AI.
Return ONLY plain text commands, one command per line, with no markdown, no numbering, and no commentary.

Allowed commands:
- block(x, y, z, "minecraft:block_id")
- box(x1, y1, z1, x2, y2, z2, "minecraft:block_id")
- line(x1, y1, z1, x2, y2, z2, "minecraft:block_id")
- sphere(x, y, z, radius, "minecraft:block_id")

Design goals:
- Build a substantial landmark, building, ruin, gate, shrine, tower, hall, bridge, or other multi-part structure.
- Favor strong silhouettes, layered foundations, supports, roofs, arches, framing, and readable detail passes.
- Prefer structures that feel intentional from far away instead of tiny props or flat blobs.

Rules:
- Coordinates are relative to the builder block.
- Keep x and z between -24 and 24.
- Keep y between -4 and 32.
- Keep spheres at radius 10 or less.
- Keep the design under 4096 placed blocks total.
- Prefer 35 to 140 commands.
- Leave interior air where appropriate and avoid giant solid cuboids unless explicitly requested.
- Use only simple vanilla blocks and avoid fluids, portals, command blocks, crops, redstone contraptions, and tile entities.
- If the prompt is vague, choose a bold fantasy or alchemical interpretation and still make it feel large.
""";

        String user = "Design a large Minecraft structure for this request: " + prompt;
        String body = "{"
                + "\"model\":\"openai/gpt-4o-mini\","
                + "\"max_tokens\":900,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + quoted(system) + "},"
                + "{\"role\":\"user\",\"content\":" + quoted(user) + "}"
                + "]}";

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .header("HTTP-Referer", "https://github.com/alchemod")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(25))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "ERROR:HTTP " + response.statusCode();
            }

            return extractOpenRouterText(response.body());
        } catch (Exception e) {
            AlchemodInit.LOG.error("[Builder] Request failed", e);
            return "ERROR:" + e.getMessage();
        }
    }

    private void executeStructureCode(String code, World world) {
        aiPending = false;

        if (code == null || code.isBlank() || code.startsWith("ERROR:")) {
            state = STATE_ERROR;
            progress = 0;
            lastError = code == null ? "Unknown builder error" : code;
            markDirty();
            return;
        }

        state = STATE_BUILDING;
        progress = 10;
        markDirty();

        try {
            BlockPos origin = getPos().up();
            PlacementBudget budget = new PlacementBudget(MAX_BLOCK_PLACEMENTS);
            List<String> commands = sanitiseCommands(code);

            for (String command : commands) {
                executeCommand(command, origin, world, budget);
            }

            progress = MAX_PROGRESS;
            state = STATE_COMPLETE;
            if (builderMode == MODE_BLOCK) {
                consumeInputs();
            }
            lastError = "";
        } catch (IllegalArgumentException e) {
            state = STATE_ERROR;
            progress = 0;
            lastError = e.getMessage();
        } catch (Exception e) {
            state = STATE_ERROR;
            progress = 0;
            lastError = e.getMessage();
            AlchemodInit.LOG.error("[Builder] Failed to execute structure code", e);
        }

        markDirty();
    }

    private List<String> sanitiseCommands(String code) {
        String cleaned = stripCodeFence(code);
        String[] lines = cleaned.split("\\R");
        List<String> commands = new ArrayList<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            if (line.endsWith(";")) {
                line = line.substring(0, line.length() - 1);
            }
            if (!line.matches("[a-zA-Z_]+\\s*\\(.*\\)")) {
                continue;
            }
            commands.add(line);
            if (commands.size() > MAX_STRUCTURE_COMMANDS) {
                throw new IllegalArgumentException("Generated structure used too many commands");
            }
        }

        if (commands.isEmpty()) {
            throw new IllegalArgumentException("Builder AI returned no valid build commands");
        }

        return commands;
    }

    private void executeCommand(String command, BlockPos origin, World world, PlacementBudget budget) {
        int parenIndex = command.indexOf('(');
        int closeIndex = command.lastIndexOf(')');
        if (parenIndex <= 0 || closeIndex <= parenIndex) {
            throw new IllegalArgumentException("Invalid builder command: " + command);
        }

        String name = command.substring(0, parenIndex).trim().toLowerCase(Locale.ROOT);
        List<String> args = splitArgs(command.substring(parenIndex + 1, closeIndex));

        switch (name) {
            case "block" -> executeBlock(args, origin, world, budget);
            case "box" -> executeBox(args, origin, world, budget);
            case "line" -> executeLine(args, origin, world, budget);
            case "sphere" -> executeSphere(args, origin, world, budget);
            default -> throw new IllegalArgumentException("Unsupported builder command: " + name);
        }

        progress = Math.min(MAX_PROGRESS - 1, progress + Math.max(1, 70 / Math.max(1, budget.remaining())));
    }

    private void executeBlock(List<String> args, BlockPos origin, World world, PlacementBudget budget) {
        if (args.size() != 4) {
            throw new IllegalArgumentException("block() expects 4 arguments");
        }

        placeRelativeBlock(world, origin,
                parseInt(args.get(0)),
                parseInt(args.get(1)),
                parseInt(args.get(2)),
                parseBlockId(args.get(3)),
                budget);
    }

    private void executeBox(List<String> args, BlockPos origin, World world, PlacementBudget budget) {
        if (args.size() != 7) {
            throw new IllegalArgumentException("box() expects 7 arguments");
        }

        int x1 = parseInt(args.get(0));
        int y1 = parseInt(args.get(1));
        int z1 = parseInt(args.get(2));
        int x2 = parseInt(args.get(3));
        int y2 = parseInt(args.get(4));
        int z2 = parseInt(args.get(5));
        Identifier blockId = parseBlockId(args.get(6));

        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    placeRelativeBlock(world, origin, x, y, z, blockId, budget);
                }
            }
        }
    }

    private void executeLine(List<String> args, BlockPos origin, World world, PlacementBudget budget) {
        if (args.size() != 7) {
            throw new IllegalArgumentException("line() expects 7 arguments");
        }

        int x1 = parseInt(args.get(0));
        int y1 = parseInt(args.get(1));
        int z1 = parseInt(args.get(2));
        int x2 = parseInt(args.get(3));
        int y2 = parseInt(args.get(4));
        int z2 = parseInt(args.get(5));
        Identifier blockId = parseBlockId(args.get(6));
        int steps = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1));
        steps = Math.max(steps, 1);

        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int x = MathHelper.floor(MathHelper.lerp(t, x1, x2));
            int y = MathHelper.floor(MathHelper.lerp(t, y1, y2));
            int z = MathHelper.floor(MathHelper.lerp(t, z1, z2));
            placeRelativeBlock(world, origin, x, y, z, blockId, budget);
        }
    }

    private void executeSphere(List<String> args, BlockPos origin, World world, PlacementBudget budget) {
        if (args.size() != 5) {
            throw new IllegalArgumentException("sphere() expects 5 arguments");
        }

        int centerX = parseInt(args.get(0));
        int centerY = parseInt(args.get(1));
        int centerZ = parseInt(args.get(2));
        int radius = Math.min(Math.abs(parseInt(args.get(3))), MAX_SPHERE_RADIUS);
        Identifier blockId = parseBlockId(args.get(4));

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance <= radius && distance >= radius - 1.2) {
                        placeRelativeBlock(world, origin, centerX + x, centerY + y, centerZ + z, blockId, budget);
                    }
                }
            }
        }
    }

    private BlockPos offset(BlockPos origin, String x, String y, String z) {
        return offset(origin, parseInt(x), parseInt(y), parseInt(z));
    }

    private BlockPos offset(BlockPos origin, int x, int y, int z) {
        validateRelativePosition(x, y, z);
        return origin.add(x, y, z);
    }

    private int parseInt(String value) {
        return Integer.parseInt(value.trim());
    }

    private Identifier parseBlockId(String value) {
        String cleaned = value.trim().replace("\"", "").replace("'", "");
        Identifier id = cleaned.contains(":") ? Identifier.of(cleaned) : Identifier.of("minecraft", cleaned);
        if (!Registries.BLOCK.containsId(id)) {
            throw new IllegalArgumentException("Unknown block id: " + cleaned);
        }
        return id;
    }

    private void placeBlock(World world, BlockPos pos, Identifier blockId, PlacementBudget budget) {
        if (!world.isInBuildLimit(pos)) {
            return;
        }

        budget.consume();
        Block block = Registries.BLOCK.get(blockId);
        world.setBlockState(pos, block.getDefaultState(), Block.NOTIFY_ALL);
    }

    private void placeRelativeBlock(World world, BlockPos origin, int x, int y, int z, Identifier blockId, PlacementBudget budget) {
        placeBlock(world, offset(origin, x, y, z), blockId, budget);
    }

    private void validateRelativePosition(int x, int y, int z) {
        if (Math.abs(x) > MAX_XZ_OFFSET || Math.abs(z) > MAX_XZ_OFFSET || y < MIN_Y_OFFSET || y > MAX_Y_OFFSET) {
            throw new IllegalArgumentException("Generated structure exceeded the safe build bounds");
        }
    }

    private void consumeInputs() {
        for (int slot = 0; slot < 2; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                stack.decrement(1);
                if (stack.isEmpty()) {
                    items.set(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    private static List<String> splitArgs(String rawArgs) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        char stringDelimiter = 0;

        for (int index = 0; index < rawArgs.length(); index++) {
            char currentChar = rawArgs.charAt(index);
            if ((currentChar == '"' || currentChar == '\'') && (index == 0 || rawArgs.charAt(index - 1) != '\\')) {
                if (!inString) {
                    inString = true;
                    stringDelimiter = currentChar;
                } else if (stringDelimiter == currentChar) {
                    inString = false;
                }
                current.append(currentChar);
                continue;
            }

            if (currentChar == ',' && !inString) {
                args.add(current.toString().trim());
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        if (current.length() > 0) {
            args.add(current.toString().trim());
        }

        return args;
    }

    private static String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstLine = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLine < 0 || lastFence <= firstLine) {
            return trimmed.replace("```", "").trim();
        }

        return trimmed.substring(firstLine + 1, lastFence).trim();
    }

    private static String extractOpenRouterText(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                return body;
            }

            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                return body;
            }

            return message.get("content").getAsString();
        } catch (Exception e) {
            return body;
        }
    }

    private static String quoted(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "") + "\"";
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        items.clear();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.alchemod.build_creator");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new BuilderScreenHandler(syncId, playerInventory, this, delegate, getPos());
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        Inventories.writeNbt(nbt, items, registryLookup);
        nbt.putInt("State", state);
        nbt.putInt("Progress", progress);
        nbt.putString("Prompt", promptText);
        nbt.putString("Error", lastError);
        nbt.putInt("Mode", builderMode);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        Inventories.readNbt(nbt, items, registryLookup);
        state = nbt.getInt("State");
        progress = nbt.getInt("Progress");
        promptText = nbt.getString("Prompt");
        lastError = nbt.getString("Error");
        builderMode = nbt.getInt("Mode");
        if (state == STATE_PROCESSING || state == STATE_BUILDING) {
            state = STATE_IDLE;
            progress = 0;
            aiPending = false;
        }
    }

    public int getState() {
        return state;
    }

    public int getProgress() {
        return progress;
    }

    public String getPrompt() {
        return promptText;
    }

    public String getError() {
        return lastError;
    }

    public PropertyDelegate getDelegate() {
        return delegate;
    }

    private static final class PlacementBudget {
        private int remaining;

        private PlacementBudget(int remaining) {
            this.remaining = remaining;
        }

        private void consume() {
            if (remaining <= 0) {
                throw new IllegalArgumentException("Generated structure exceeded safe block budget");
            }
            remaining--;
        }

        private int remaining() {
            return remaining;
        }
    }
}
