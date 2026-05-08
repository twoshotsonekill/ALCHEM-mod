package com.alchemod.builder;

import com.alchemod.ai.AlchemodConfig;
import com.alchemod.ai.BuilderTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    void parsesAiSimpleTower() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":12345,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"A medieval stone tower with a pointed roof.","code":"box(0,0,0,4,15,4,'stone'); box(-1,15,0,5,18,4,'dark_oak_log');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertEquals(12345L, program.seed());
        assertFalse(program.legacyFallback());
        assertTrue(program.code().contains("box("));
        assertTrue(program.buildPlan().contains("tower"));
    }

    @Test
    void parsesAiFortressWithMultipleShapes() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":99999,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Nether fortress with twin towers and a bridge.","code":"box(-10,0,-10,10,20,10,'nether_bricks'); sphere(0,22,0,5,'nether_bricks'); line(-15,10,0,15,10,0,'glowstone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertEquals(99999L, program.seed());
        assertTrue(program.code().contains("box("));
        assertTrue(program.code().contains("sphere("));
        assertTrue(program.code().contains("line("));
    }

    @Test
    void parsesAiLargeSeedFromAi() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":9999999999,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Large seeded structure for consistent results.","code":"box(0,0,0,10,10,10,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertEquals(9999999999L, program.seed());
    }

    @Test
    void parsesAiZeroSeed() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":0,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Zero seed test.","code":"block(0,0,0,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertEquals(0L, program.seed());
    }

    @Test
    void parsesAiNegativeSeed() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":-42,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Negative seed test.","code":"block(0,0,0,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertEquals(-42L, program.seed());
    }

    @Test
    void parsesAiWithBlockFunction() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":1,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Single block placement.","code":"block(0,5,0,'diamond_block');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertTrue(program.code().contains("block("));
        assertTrue(program.code().contains("diamond_block"));
    }

    @Test
    void parsesAiWithSphereFunction() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":2,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Crystal dome structure.","code":"sphere(0,10,0,8,'glass'); sphere(0,10,0,6,'glowstone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertTrue(program.code().contains("sphere("));
        assertTrue(program.code().contains("glass"));
        assertTrue(program.code().contains("glowstone"));
    }

    @Test
    void parsesAiWithLineFunction() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":3,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Bridge connection.","code":"line(-20,5,-5,20,5,5,'oak_planks');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertTrue(program.code().contains("line("));
        assertTrue(program.code().contains("oak_planks"));
    }

    @Test
    void parsesAiWithMathFloor() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":4,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Randomized placement.","code":"box(Math.floor(rng()*10),0,0,Math.floor(rng()*10)+5,5,5,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertTrue(program.code().contains("Math.floor"));
    }

    @Test
    void parsesAiWithMathMinMax() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":5,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Bounded coordinates.","code":"box(Math.min(-10,x),0,0,Math.max(10,x),10,10,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertTrue(program.code().contains("Math.min"));
        assertTrue(program.code().contains("Math.max"));
    }

    @Test
    void parsesAiWithRng() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":6,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Random height variation.","code":"box(0,Math.floor(rng()*10),0,5,Math.floor(rng()*10)+5,5,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertTrue(program.code().contains("rng()"));
    }

    @Test
    void parsesAiWithNoSeed() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"No seed provided.","code":"box(0,0,0,5,5,5,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertNull(program.seed());
    }

    @Test
    void parsesAiWithNullSeed() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":null,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Null seed.","code":"box(0,0,0,5,5,5,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertNull(program.seed());
    }

    @Test
    void parsesAiWithArcaneBricks() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":7,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Magical tower.","code":"box(0,0,0,8,25,8,'alchemod:arcane_bricks');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertTrue(program.code().contains("alchemod:arcane_bricks"));
    }

    @Test
    void parsesAiWithEtherCrystal() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":8,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Crystal formation.","code":"sphere(0,15,0,5,'alchemod:ether_crystal');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertTrue(program.code().contains("alchemod:ether_crystal"));
    }

    @Test
    void executesAiStructure() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":100,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Execute test structure.","code":"box(0,0,0,3,3,3,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        List<String> placements = new ArrayList<>();
        BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z + ":" + blockId));

        assertFalse(placements.isEmpty());
        assertTrue(placements.size() > 50);
    }

    @Test
    void executesAiSphere() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":101,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Sphere test.","code":"sphere(0,5,0,3,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        List<String> placements = new ArrayList<>();
        BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z));

        assertFalse(placements.isEmpty());
    }

    @Test
    void executesAiLine() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":102,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Line test.","code":"line(0,0,0,10,10,10,'glowstone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        List<String> placements = new ArrayList<>();
        BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z));

        assertFalse(placements.isEmpty());
        assertEquals(11, placements.size());
    }

    @Test
    void parsesAiWithTrailingNewline() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":200,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Newline at end.","code":"box(0,0,0,5,5,5,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
    }

    @Test
    void parsesAiWithExtraWhitespace() {
        String aiResponse = """
                {  "tool"  :  "voxel.exec"  ,  "input"  :  {  "palette"  :  "simple_v1"  ,  "seed"  :  300  ,  "bounds"  :  {  "x"  :  [  -64  ,  64  ]  ,  "y"  :  [  -8  ,  72  ]  ,  "z"  :  [  -64  ,  64  ]  }  ,  "build_plan"  :  "Extra whitespace test."  ,  "code"  :  "box(0,0,0,5,5,5,'stone');"  }}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);

        assertNotNull(program);
        assertEquals(300L, program.seed());
    }

    @Test
    void endToEndValidAiResponse() {
        String validAiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":42,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"A simple stone pyramid.","code":"box(-5,0,-5,5,10,5,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(validAiResponse);

        assertNotNull(program);
        assertEquals("simple_v1", program.palette());
        assertEquals(42L, program.seed());
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

        assertEquals(999L, program.seed());
        assertTrue(program.buildPlan().contains("Twin towers"));
    }

    @Test
    void validatesBlockPaletteInCode() {
        String responseWithInvalidBlock = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":1,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Test with invalid block.","code":"block(0,0,0,'fake_block');"}}
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

        assertEquals(12345L, program.seed());
        assertFalse(program.legacyFallback());
        assertTrue(program.code().contains("box("));
        assertTrue(program.code().contains("line("));
    }

    @Test
    void configDefaultsAreValid() {
        assertEquals("deepseek/deepseek-v4-pro", AlchemodConfig.DEFAULT_BUILDER_MODEL);
        assertEquals("deepseek/deepseek-v4-pro", AlchemodConfig.DEFAULT_CREATOR_MODEL);
        assertEquals("deepseek/deepseek-v4-pro", AlchemodConfig.DEFAULT_FORGE_MODEL);
        assertTrue(AlchemodConfig.DEFAULT_BUILDER_MAX_TOKENS > 0);
    }
}
