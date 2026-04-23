package com.alchemod.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BuilderTestHelper {

    private BuilderTestHelper() {
    }

    public static AlchemodConfig createTestConfig(Path configPath) {
        if (Files.exists(configPath)) {
            return AlchemodConfig.load(configPath, null);
        }

        writeTestConfig(configPath, AlchemodConfig.DEFAULT_BUILDER_MODEL, AlchemodConfig.DEFAULT_BUILDER_MAX_TOKENS);
        return AlchemodConfig.load(configPath, null);
    }

    public static void writeTestConfig(Path configPath, String model, int maxTokens) {
        try {
            String content = String.format("""
                    openrouter_api_key=test_key_123
                    builder_model=%s
                    builder_max_tokens=%d
                    creator_model=%s
                    creator_max_tokens_scripted=%d
                    creator_max_tokens_plain=%d
                    builder_timeout_seconds=%d
                    creator_timeout_seconds=%d
                    """,
                    model,
                    maxTokens,
                    AlchemodConfig.DEFAULT_CREATOR_MODEL,
                    AlchemodConfig.DEFAULT_CREATOR_MAX_TOKENS_SCRIPTED,
                    AlchemodConfig.DEFAULT_CREATOR_MAX_TOKENS_PLAIN,
                    AlchemodConfig.DEFAULT_BUILDER_TIMEOUT_SECONDS,
                    AlchemodConfig.DEFAULT_CREATOR_TIMEOUT_SECONDS);

            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write test config", e);
        }
    }

    public static AlchemodConfig createConfigWithoutApiKey(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            String content = String.format("""
                    openrouter_api_key=
                    builder_model=%s
                    builder_max_tokens=%d
                    """,
                    AlchemodConfig.DEFAULT_BUILDER_MODEL,
                    AlchemodConfig.DEFAULT_BUILDER_MAX_TOKENS);

            Files.writeString(configPath, content);
            return AlchemodConfig.load(configPath, null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write test config", e);
        }
    }
}