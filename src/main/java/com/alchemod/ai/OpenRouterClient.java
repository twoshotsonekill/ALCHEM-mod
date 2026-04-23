package com.alchemod.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OpenRouterClient {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private OpenRouterClient() {
    }

    public static ChatResult chat(String apiKey, ChatRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return ChatResult.error("OPENROUTER_API_KEY not set");
        }

        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", request.model());
            body.addProperty("max_tokens", request.maxTokens());

            JsonArray messages = new JsonArray();
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", request.systemPrompt());
            messages.add(systemMessage);

            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", request.userPrompt());
            messages.add(userMessage);

            body.add("messages", messages);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://github.com/alchemod")
                    .timeout(Duration.ofSeconds(Math.max(1, request.timeoutSeconds())))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = HTTP.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ChatResult.error("HTTP " + response.statusCode(), response.body());
            }

            return new ChatResult(extractContent(response.body()), response.body(), null);
        } catch (Exception e) {
            return ChatResult.error(e.getMessage(), null);
        }
    }

    public static String extractContent(String apiBody) {
        try {
            JsonObject root = JsonParser.parseString(apiBody).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                return apiBody;
            }

            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null) {
                return apiBody;
            }

            JsonElement content = message.get("content");
            if (content == null || content.isJsonNull()) {
                return apiBody;
            }

            if (content.isJsonPrimitive()) {
                return content.getAsString();
            }

            if (content.isJsonArray()) {
                StringBuilder builder = new StringBuilder();
                for (JsonElement element : content.getAsJsonArray()) {
                    JsonObject part = element.getAsJsonObject();
                    if (part.has("text")) {
                        builder.append(part.get("text").getAsString());
                    }
                }
                return builder.toString();
            }
        } catch (Exception ignored) {
        }

        return apiBody;
    }

    public record ChatRequest(
            String model,
            int maxTokens,
            int timeoutSeconds,
            String systemPrompt,
            String userPrompt
    ) {
    }

    public record ChatResult(
            String content,
            String rawBody,
            String error
    ) {
        public static ChatResult error(String message) {
            return new ChatResult(null, null, message);
        }

        public static ChatResult error(String message, String rawBody) {
            return new ChatResult(null, rawBody, message);
        }

        public boolean isError() {
            return error != null && !error.isBlank();
        }
    }
}
