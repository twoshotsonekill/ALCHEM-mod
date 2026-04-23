package com.alchemod.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public final class BuilderResponseParser {

    private BuilderResponseParser() {
    }

    public static BuilderProgram parse(String response) {
        String cleaned = stripCodeFence(response);
        String jsonBody = extractFirstJsonObject(cleaned);

        if (jsonBody != null) {
            try {
                return parseStructuredJson(jsonBody);
            } catch (JsonSyntaxException e) {
                BuilderProgram fallback = tryLegacyFallback(cleaned);
                if (fallback != null) {
                    return fallback;
                }
                throw new IllegalArgumentException("Builder AI returned invalid JSON", e);
            }
        }

        BuilderProgram fallback = tryLegacyFallback(cleaned);
        if (fallback != null) {
            return fallback;
        }

        throw new IllegalArgumentException("Builder AI returned neither valid voxel.exec JSON nor legacy commands");
    }

    private static BuilderProgram parseStructuredJson(String jsonBody) {
        JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
        String tool = requireString(root, "tool");
        if (!"voxel.exec".equals(tool)) {
            throw new IllegalArgumentException("Builder response must use tool voxel.exec");
        }

        JsonObject input = root.has("input") && root.get("input").isJsonObject()
                ? root.getAsJsonObject("input")
                : null;
        if (input == null) {
            throw new IllegalArgumentException("Builder response is missing input");
        }

        String palette = requireString(input, "palette");
        if (!BuilderRuntime.PALETTE_NAME.equals(palette)) {
            throw new IllegalArgumentException("Builder response must use palette " + BuilderRuntime.PALETTE_NAME);
        }

        BuilderProgram.Bounds bounds = parseBounds(input.getAsJsonObject("bounds"));
        String buildPlan = requireString(input, "build_plan");
        if (buildPlan.isBlank()) {
            throw new IllegalArgumentException("Builder response build_plan must be a non-empty string");
        }

        String code = requireString(input, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("Builder response code must be a non-empty string");
        }

        Integer seed = null;
        if (input.has("seed") && !input.get("seed").isJsonNull()) {
            JsonElement seedElement = input.get("seed");
            if (!seedElement.isJsonPrimitive() || !seedElement.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Builder response seed must be a number");
            }
            seed = seedElement.getAsInt();
        }

        return new BuilderProgram(palette, seed, bounds, buildPlan.trim(), code, false);
    }

    private static BuilderProgram.Bounds parseBounds(JsonObject boundsObject) {
        if (boundsObject == null) {
            throw new IllegalArgumentException("Builder response is missing bounds");
        }

        BuilderProgram.AxisBounds x = parseAxis(boundsObject, "x");
        BuilderProgram.AxisBounds y = parseAxis(boundsObject, "y");
        BuilderProgram.AxisBounds z = parseAxis(boundsObject, "z");

        if (x.min() != -BuilderRuntime.MAX_XZ_OFFSET || x.max() != BuilderRuntime.MAX_XZ_OFFSET
                || z.min() != -BuilderRuntime.MAX_XZ_OFFSET || z.max() != BuilderRuntime.MAX_XZ_OFFSET
                || y.min() != BuilderRuntime.MIN_Y_OFFSET || y.max() != BuilderRuntime.MAX_Y_OFFSET) {
            throw new IllegalArgumentException("Builder response bounds must match the safe runtime limits");
        }

        return new BuilderProgram.Bounds(x, y, z);
    }

    private static BuilderProgram.AxisBounds parseAxis(JsonObject boundsObject, String key) {
        JsonArray array = boundsObject.has(key) && boundsObject.get(key).isJsonArray()
                ? boundsObject.getAsJsonArray(key)
                : null;
        if (array == null || array.size() != 2) {
            throw new IllegalArgumentException("Builder response bounds." + key + " must be a two-element array");
        }

        return new BuilderProgram.AxisBounds(array.get(0).getAsInt(), array.get(1).getAsInt());
    }

    private static String requireString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Builder response is missing string field " + key);
        }
        return element.getAsString();
    }

    private static BuilderProgram tryLegacyFallback(String cleaned) {
        String legacyCode = sanitiseLegacyCommands(cleaned);
        if (legacyCode == null) {
            return null;
        }

        BuilderProgram.Bounds bounds = new BuilderProgram.Bounds(
                new BuilderProgram.AxisBounds(-BuilderRuntime.MAX_XZ_OFFSET, BuilderRuntime.MAX_XZ_OFFSET),
                new BuilderProgram.AxisBounds(BuilderRuntime.MIN_Y_OFFSET, BuilderRuntime.MAX_Y_OFFSET),
                new BuilderProgram.AxisBounds(-BuilderRuntime.MAX_XZ_OFFSET, BuilderRuntime.MAX_XZ_OFFSET));
        return new BuilderProgram(
                BuilderRuntime.PALETTE_NAME,
                null,
                bounds,
                "Legacy fallback response: executed line-based builder commands because structured voxel.exec JSON was unavailable.",
                legacyCode,
                true);
    }

    private static String sanitiseLegacyCommands(String code) {
        String[] lines = stripCodeFence(code).split("\\R");
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            if (line.endsWith(";")) {
                line = line.substring(0, line.length() - 1).trim();
            }
            if (!line.matches("[a-zA-Z_]+\\s*\\(.*\\)")) {
                continue;
            }
            if (count > 0) {
                builder.append('\n');
            }
            builder.append(line);
            count++;
        }
        return count > 0 ? builder.toString() : null;
    }

    static String stripCodeFence(String value) {
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

    static String extractFirstJsonObject(String content) {
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }

            if (current == '\\') {
                escaped = true;
                continue;
            }

            if (current == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (current == '{') {
                if (depth == 0) {
                    start = index;
                }
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    return content.substring(start, index + 1);
                }
            }
        }

        return null;
    }
}
