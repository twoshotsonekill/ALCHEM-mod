package com.alchemod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Shared JSON parsing utilities for AI API responses.
 * Consolidates duplicated methods from CreatorBlockEntity, ForgeBlockEntity, TransmuterBlockEntity.
 */
public final class JsonParsingUtils {
    private JsonParsingUtils() {}

    /**
     * Remove markdown code fences and extra whitespace from response text.
     */
    public static String stripCodeFence(String content) {
        if (content == null) return "";
        content = content.replaceAll("```(?:json)?", "").trim();
        return content;
    }

    /**
     * Extract the first JSON object from text, handling common patterns like:
     * - Bare JSON object
     * - JSON inside code fences
     * - JSON with text before/after
     */
    public static String extractFirstJsonObject(String content) {
        if (content == null || content.isBlank()) return null;

        int braceStart = content.indexOf('{');
        if (braceStart == -1) return null;

        int braceDepth = 0;
        for (int i = braceStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    return content.substring(braceStart, i + 1);
                }
            }
        }

        return null; // No matching closing brace
    }

    /**
     * Get a string value from a JSON object with a fallback.
     */
    public static String getString(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        try {
            return el.getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Get a nullable string value (returns null if not found or not a string).
     */
    public static String getNullableString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        try {
            return el.isJsonNull() ? null : el.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Normalize a string: lowercase and trim.
     */
    public static String normalise(String value) {
        if (value == null) return "";
        return value.toLowerCase().trim();
    }

    /**
     * Get an integer from a JSON object with a fallback.
     */
    public static int getInt(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        try {
            return el.getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Get a double from a JSON object with a fallback.
     */
    public static double getDouble(JsonObject obj, String key, double fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        JsonElement el = obj.get(key);
        try {
            return el.getAsDouble();
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Check if a JSON object has a key and it's a JSON array.
     */
    public static boolean isJsonArray(JsonObject obj, String key) {
        return obj != null && obj.has(key) && obj.get(key).isJsonArray();
    }

    /**
     * Check if a JSON object has a key and it's a JSON object.
     */
    public static boolean isJsonObject(JsonObject obj, String key) {
        return obj != null && obj.has(key) && obj.get(key).isJsonObject();
    }

    /**
     * Get a list of strings from a JSON array value.
     */
    public static java.util.List<String> getStringList(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return java.util.List.of();
        JsonElement el = obj.get(key);
        if (!el.isJsonArray()) return java.util.List.of();
        
        java.util.List<String> result = new java.util.ArrayList<>();
        for (JsonElement item : el.getAsJsonArray()) {
            if (item != null && !item.isJsonNull()) {
                try {
                    result.add(item.getAsString());
                } catch (Exception e) {
                    // Skip non-string elements
                }
            }
        }
        return result;
    }
}
