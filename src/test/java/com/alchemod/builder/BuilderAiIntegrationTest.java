package com.alchemod.builder;

import com.alchemod.ai.AlchemodConfig;
import com.alchemod.ai.BuilderTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BuilderAiIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void promptFactoryGeneratesValidSystemPrompt() {
        String systemPrompt = BuilderPromptFactory.buildSystemPrompt();

        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("voxel.exec"), "Should contain voxel.exec tool");
        assertTrue(systemPrompt.contains("simple_v1"), "Should reference simple_v1 palette");
        assertTrue(systemPrompt.contains("CRITICAL"), "Should have critical instructions");
    }

    @Test
    void promptFactoryGeneratesValidUserPrompt() {
        String userPrompt = BuilderPromptFactory.buildUserPrompt("A castle with towers");

        assertNotNull(userPrompt);
        assertTrue(userPrompt.contains("A castle with towers"));
    }

    @Test
    void endToEndValidAiResponse() {
        String validAiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":42,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"A simple stone pyramid.","code":"box(-5,0,-5,5,10,5,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(validAiResponse);

        assertNotNull(program);
        assertEquals("simple_v1", program.palette());
        assertEquals(42, program.seed());
        assertFalse(program.legacyFallback());
        assertTrue(program.code().contains("box("));
        assertTrue(program.buildPlan().contains("pyramid"));
    }

    @Test
    void parsesAiResponseWithCodeFence() {
        String fencedResponse = """
                ```json
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":999,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Twin towers connected by a bridge.","code":"box(-8,0,-2,8,20,2,'oak_planks');"}}
                ```
                """;

        BuilderProgram program = BuilderResponseParser.parse(fencedResponse);

        assertEquals(999, program.seed());
        assertTrue(program.buildPlan().contains("Twin towers"));
    }

    @Test
    void validatesBlockPaletteInCode() {
        String responseWithInvalidBlock = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":1,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Test with invalid block.","code":"block(0,0,0,'diamond_block');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(responseWithInvalidBlock);

        assertThrows(IllegalArgumentException.class, () ->
                BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> {}));
    }

    @Test
    void validStructuredJsonWithAllShapes() {
        String complexResponse = """
                {
                  "tool":"voxel.exec",
                  "input":{
                    "palette":"simple_v1",
                    "seed":12345,
                    "bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},
                    "build_plan":"Multi-structure fortress with multiple shapes.",
                    "code":"box(-10,0,-10,10,15,10,'stone'); line(-10,15,0,10,15,0,'bricks');"
                  }
                }
                """;

        BuilderProgram program = BuilderResponseParser.parse(complexResponse);

        assertEquals(12345, program.seed());
        assertFalse(program.legacyFallback());
        assertTrue(program.code().contains("box("));
        assertTrue(program.code().contains("line("));
    }

    @Test
    void configDefaultsAreValid() {
        assertEquals("openai/gpt-5.4-mini", AlchemodConfig.DEFAULT_BUILDER_MODEL);
        assertEquals("google/gemini-2.5-flash-lite-preview-05-20", AlchemodConfig.DEFAULT_CREATOR_MODEL);
        assertTrue(AlchemodConfig.DEFAULT_BUILDER_MAX_TOKENS > 0);
    }
}