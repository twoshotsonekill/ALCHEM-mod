package com.alchemod.ai;

import com.alchemod.AlchemodInit;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Makes a second OpenRouter request specifically to generate sprite drawing commands
 * via tool calling.  The model is forced to call {@code draw_sprite}, which returns
 * a structured JSON list of painting operations ({@code fill}, {@code rect},
 * {@code pixel}, {@code circle}).  Those operations are later executed client-side by
 * {@link com.alchemod.resource.SpriteCommandRenderer} — no Rhino, no sandboxing, no
 * network call at render time.
 *
 * <p>Returns the raw {@code arguments} JSON string from the tool call, suitable for
 * storing directly in {@code creator_sprite_commands} NBT.  Returns {@code null} on
 * any failure; callers must treat a null result as "use fallback texture".
 */
public final class SpriteToolClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private SpriteToolClient() {
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public static String generateSprite(
            String apiKey,
            String model,
            String name,
            String description,
            String rarity,
            String itemType,
            int timeoutSeconds) {

        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 700);

        // Define the draw_sprite tool
        JsonArray tools = new JsonArray();
        tools.add(buildToolDefinition());
        body.add("tools", tools);

        // Force the model to call this specific tool
        JsonObject toolChoice = new JsonObject();
        toolChoice.addProperty("type", "function");
        JsonObject choiceFunc = new JsonObject();
        choiceFunc.addProperty("name", "draw_sprite");
        toolChoice.add("function", choiceFunc);
        body.add("tool_choice", toolChoice);

        // Messages
        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", SYSTEM_PROMPT);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", buildUserPrompt(name, description, rarity, itemType));
        messages.add(userMsg);

        body.add("messages", messages);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://github.com/alchemod")
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                AlchemodInit.LOG.warn("[Sprite] Tool call HTTP {}", response.statusCode());
                return null;
            }

            return extractToolArguments(response.body());
        } catch (Exception e) {
            AlchemodInit.LOG.warn("[Sprite] Tool call failed: {}", e.getMessage());
            return null;
        }
    }

    // ── Tool definition ───────────────────────────────────────────────────────

    private static JsonObject buildToolDefinition() {
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");

        JsonObject func = new JsonObject();
        func.addProperty("name", "draw_sprite");
        func.addProperty("description",
                "Produce a 16x16 Minecraft-style pixel art icon by emitting a list of layered drawing commands. "
                + "Commands are applied in order (later commands paint over earlier ones). "
                + "Think of classic 16x16 Minecraft item textures: clear silhouettes, 3-6 colours, no gradients.");
        func.add("parameters", buildParameterSchema());
        tool.add("function", func);
        return tool;
    }

    private static JsonObject buildParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonArray required = new JsonArray();
        required.add("commands");
        schema.add("required", required);

        JsonObject properties = new JsonObject();

        // commands array
        JsonObject commandsArr = new JsonObject();
        commandsArr.addProperty("type", "array");
        commandsArr.addProperty("description", "Ordered list of drawing operations, max 35 entries.");
        commandsArr.addProperty("maxItems", 35);
        commandsArr.add("items", buildCommandItemSchema());
        properties.add("commands", commandsArr);

        schema.add("properties", properties);
        return schema;
    }

    private static JsonObject buildCommandItemSchema() {
        JsonObject item = new JsonObject();
        item.addProperty("type", "object");

        JsonArray required = new JsonArray();
        required.add("op");
        required.add("r");
        required.add("g");
        required.add("b");
        item.add("required", required);

        JsonObject props = new JsonObject();
        addEnum(props, "op",
                "Operation: fill=entire canvas, rect=filled rectangle, pixel=single pixel, circle=filled circle",
                "fill", "rect", "pixel", "circle");
        addIntProp(props, "x",      "Pixel X (0-15) — required for op=pixel",                0, 15);
        addIntProp(props, "y",      "Pixel Y (0-15) — required for op=pixel",                0, 15);
        addIntProp(props, "x1",     "Left edge (0-15) — required for op=rect",               0, 15);
        addIntProp(props, "y1",     "Top edge (0-15) — required for op=rect",                0, 15);
        addIntProp(props, "x2",     "Right edge (0-15, inclusive) — required for op=rect",   0, 15);
        addIntProp(props, "y2",     "Bottom edge (0-15, inclusive) — required for op=rect",  0, 15);
        addIntProp(props, "cx",     "Centre X (0-15) — required for op=circle",              0, 15);
        addIntProp(props, "cy",     "Centre Y (0-15) — required for op=circle",              0, 15);
        addIntProp(props, "radius", "Circle radius (1-8) — required for op=circle",          1,  8);
        addIntProp(props, "r",      "Red channel 0-255",   0, 255);
        addIntProp(props, "g",      "Green channel 0-255", 0, 255);
        addIntProp(props, "b",      "Blue channel 0-255",  0, 255);

        item.add("properties", props);
        return item;
    }

    // ── Response extraction ───────────────────────────────────────────────────

    /**
     * Pulls the {@code arguments} string out of an OpenAI-format tool_calls response,
     * with a fallback for Anthropic-style {@code content[].type=tool_use} blocks that
     * some OpenRouter models emit.
     */
    private static String extractToolArguments(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return null;

            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null) return null;

            // OpenAI format: message.tool_calls[0].function.arguments
            if (message.has("tool_calls")) {
                JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    JsonObject firstCall = toolCalls.get(0).getAsJsonObject();
                    JsonObject func = firstCall.getAsJsonObject("function");
                    if (func != null && func.has("arguments")) {
                        return func.get("arguments").getAsString();
                    }
                }
            }

            // Anthropic format: message.content[]{type:"tool_use", input:{...}}
            JsonElement contentEl = message.get("content");
            if (contentEl != null && contentEl.isJsonArray()) {
                for (JsonElement el : contentEl.getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    JsonObject block = el.getAsJsonObject();
                    if ("tool_use".equals(getString(block, "type")) && block.has("input")) {
                        return block.get("input").toString();
                    }
                }
            }

        } catch (Exception e) {
            AlchemodInit.LOG.warn("[Sprite] Failed to parse tool response: {}", e.getMessage());
        }
        return null;
    }

    // ── Prompt text ───────────────────────────────────────────────────────────

    private static final String SYSTEM_PROMPT = """
You are a Minecraft pixel art sprite designer. You always call draw_sprite.

Think like the Minecraft art team: strong silhouette, 3-6 flat colours, no gradients, clear read at 16x16.

Workflow — start with structure, then add details:
1. `fill` — background colour (dark, usually 10-30 per channel; magic items can be fully transparent: r=0 g=0 b=0 — but you MUST still call fill)
2. 1-3 `rect` calls for the main shape body
3. 1-2 `rect` or `circle` calls for secondary details (grip, gem, label, etc.)
4. `pixel` highlights — 2-4 single bright pixels for sheen

Rarity colour hints (for accent colours, not backgrounds):
  common     → greys 130-190
  uncommon   → greens (g channel 140-200, r/b ~60-90)
  rare       → blues/cyans (b channel 160-220)
  epic       → purples (r 160-200, b 180-220, g 40-80)
  legendary  → gold (r 220-255, g 160-200, b 20-60) + bright pixel highlights

Shape vocabulary:
  sword / dagger → tall narrow rect (blade) + small wide rect (crossguard) + tiny rect (grip)
  bow            → thin arc of pixels + diagonal string line
  wand / rod     → single-pixel-wide diagonal line, gem circle at tip
  potion         → circle body (cy~9, r=4) + narrow rect neck + small rect stopper
  food (apple)   → circle + 1-2 pixel leaves
  spawn_egg      → two-colour circle split horizontally at cy=8
  totem          → tall rect with inset face: eye pixels, mouth rect
  throwable      → small circle + 2-3 pixel trail
  key            → rect handle + 1-pixel-wide stick + pixel teeth
  orb / gem      → circle + 2 bright highlight pixels
""";

    private static String buildUserPrompt(String name, String description, String rarity, String itemType) {
        return String.format(
                "Item: %s\nType: %s  Rarity: %s\nFlavour: %s\n\nCall draw_sprite now.",
                name, itemType, rarity, description);
    }

    // ── Schema helpers ────────────────────────────────────────────────────────

    private static void addEnum(JsonObject props, String name, String desc, String... values) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "string");
        obj.addProperty("description", desc);
        JsonArray enumArr = new JsonArray();
        for (String v : values) enumArr.add(v);
        obj.add("enum", enumArr);
        props.add(name, obj);
    }

    private static void addIntProp(JsonObject props, String name, String desc, int min, int max) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "integer");
        obj.addProperty("description", desc);
        obj.addProperty("minimum", min);
        obj.addProperty("maximum", max);
        props.add(name, obj);
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && !el.isJsonNull() ? el.getAsString() : "";
    }
}
