package com.alchemod.ai;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record AlchemodConfig(
        String openRouterApiKey,
        String builderModel,
        String creatorModel,
        int builderMaxTokens,
        int creatorMaxTokensScripted,
        int creatorMaxTokensPlain,
        int builderTimeoutSeconds,
        int creatorTimeoutSeconds
) {

    public static final String DEFAULT_BUILDER_MODEL = "openai/gpt-5.4-nano";
    public static final String DEFAULT_CREATOR_MODEL = "google/gemini-2.5-flash-lite-preview-05-20";
    public static final int DEFAULT_BUILDER_MAX_TOKENS = 3500;
    public static final int DEFAULT_CREATOR_MAX_TOKENS_SCRIPTED = 1200;
    public static final int DEFAULT_CREATOR_MAX_TOKENS_PLAIN = 400;
    public static final int DEFAULT_BUILDER_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_CREATOR_TIMEOUT_SECONDS = 40;

    public static AlchemodConfig load(Path configPath, Logger logger) {
        if (!Files.exists(configPath)) {
            writeDefaultConfig(configPath, logger);
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            properties.load(in);
        } catch (IOException e) {
            logger.error("[Alchemod] Failed to read config: {}", e.getMessage());
        }

        String configApiKey = properties.getProperty("openrouter_api_key", "").trim();
        String envApiKey = System.getenv("OPENROUTER_API_KEY");
        String effectiveApiKey = configApiKey;
        if (envApiKey != null && !envApiKey.isBlank()) {
            logger.info("[Alchemod] API key loaded from environment variable.");
            effectiveApiKey = envApiKey.trim();
        } else if (!configApiKey.isBlank() && !configApiKey.startsWith("YOUR_")) {
            logger.info("[Alchemod] API key loaded from config/alchemod.properties.");
        } else {
            effectiveApiKey = "";
            logger.warn("[Alchemod] No API key configured. Set OPENROUTER_API_KEY or update config/alchemod.properties.");
        }

        return new AlchemodConfig(
                effectiveApiKey,
                stringProperty(properties, "builder_model", DEFAULT_BUILDER_MODEL),
                stringProperty(properties, "creator_model", DEFAULT_CREATOR_MODEL),
                intProperty(properties, "builder_max_tokens", DEFAULT_BUILDER_MAX_TOKENS, logger),
                intProperty(properties, "creator_max_tokens_scripted", DEFAULT_CREATOR_MAX_TOKENS_SCRIPTED, logger),
                intProperty(properties, "creator_max_tokens_plain", DEFAULT_CREATOR_MAX_TOKENS_PLAIN, logger),
                intProperty(properties, "builder_timeout_seconds", DEFAULT_BUILDER_TIMEOUT_SECONDS, logger),
                intProperty(properties, "creator_timeout_seconds", DEFAULT_CREATOR_TIMEOUT_SECONDS, logger));
    }

    private static String stringProperty(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key, fallback).trim();
        return value.isBlank() ? fallback : value;
    }

    private static int intProperty(Properties properties, String key, int fallback, Logger logger) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            logger.warn("[Alchemod] Invalid integer for {} in config: {}", key, raw);
            return fallback;
        }
    }

    private static void writeDefaultConfig(Path path, Logger logger) {
        String template = """
                # Alchemod - OpenRouter and AI generation configuration
                # ======================================================
                #
                # Get a free key at https://openrouter.ai
                #
                # Option A (recommended) - set a global OS environment variable so every
                # Minecraft instance and launcher shares the same key automatically:
                #
                #   Windows : System Properties -> Advanced -> Environment Variables -> New
                #             Variable name : OPENROUTER_API_KEY
                #             Variable value: sk-or-v1-xxxxxxxx...
                #
                #   macOS   : echo 'export OPENROUTER_API_KEY=sk-or-v1-...' >> ~/.zshrc
                #             (restart your terminal / launcher afterwards)
                #
                #   Linux   : echo 'export OPENROUTER_API_KEY=sk-or-v1-...' >> ~/.profile
                #
                # Option B - paste your key directly below (used only if env var is absent):
                #
                openrouter_api_key=YOUR_KEY_HERE
                builder_model=%s
                creator_model=%s
                builder_max_tokens=%d
                creator_max_tokens_scripted=%d
                creator_max_tokens_plain=%d
                builder_timeout_seconds=%d
                creator_timeout_seconds=%d
                """.formatted(
                DEFAULT_BUILDER_MODEL,
                DEFAULT_CREATOR_MODEL,
                DEFAULT_BUILDER_MAX_TOKENS,
                DEFAULT_CREATOR_MAX_TOKENS_SCRIPTED,
                DEFAULT_CREATOR_MAX_TOKENS_PLAIN,
                DEFAULT_BUILDER_TIMEOUT_SECONDS,
                DEFAULT_CREATOR_TIMEOUT_SECONDS);

        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(template.getBytes(StandardCharsets.UTF_8));
            logger.info("[Alchemod] Created default config at {}", path);
        } catch (IOException e) {
            logger.error("[Alchemod] Could not create config file: {}", e.getMessage());
        }
    }
}
