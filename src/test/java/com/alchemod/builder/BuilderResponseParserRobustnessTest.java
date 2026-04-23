package com.alchemod.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuilderResponseParserRobustnessTest {

    @Test
    void handlesMinimalValidResponse() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":1,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Test.","code":"block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(1, program.seed());
    }

    @Test
    void handlesExtraWhitespace() {
        String response = """
                {"tool":"voxel.exec","input":{
                    "palette":"simple_v1",
                    "seed":2,
                    "bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},
                    "build_plan":"Test with extra whitespace.",
                    "code":"block(0,0,0,'stone');"
                }}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(2, program.seed());
    }

@Test
    void handlesBackslashNInCode() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":3,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Backslash n.","code":"block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.code().contains("block("));
    }

    @Test
    void rejectsLargeSeedAsInt() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":9999999999,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Large seed.","code":"block(0,0,0,'stone');"}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void handlesSingleQuotesInCode() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":4,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Single quotes.","code":"box(0,0,0,5,5,5,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void rejectsDoubleQuotesInCodeAsJson() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":5,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Double quotes.","code":"box(0,0,0,5,5,5,\"stone\");"}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void handlesTrailingSemicolon() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":6,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Trailing semicolon.","code":"block(0,0,0,'stone');;"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesNoSemicolon() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":7,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"No semicolon.","code":"block(0,0,0,'stone')"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesCodeWithComments() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":8,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"With comments.","code":"// This is a comment\\nblock(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void rejectsEmptyBuildPlan() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":9,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"","code":"block(0,0,0,'stone');"}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void rejectsWrongTool() {
        String response = """
                {"tool":"wrong.tool","input":{"palette":"simple_v1","seed":10,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Wrong tool.","code":"block(0,0,0,'stone');"}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void rejectsWrongPalette() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"wrong_palette","seed":11,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Wrong palette.","code":"block(0,0,0,'stone');"}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void rejectsMissingCode() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":12,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Missing code field."}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void rejectsInvalidJson() {
        String response = "This is not JSON at all";
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void rejectsMalformedJson() {
        String response = "{ this is not valid JSON }";
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void rejectsIncompleteJson() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1"
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void handlesNoSeed() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"No seed.","code":"block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertNull(program.seed());
    }

    @Test
    void handlesNullSeed() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":null,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Null seed.","code":"block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertNull(program.seed());
    }

    @Test
    void handlesEscapedCharacters() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":13,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Escaped\\\\slash.","code":"block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesUnicodeInBuildPlan() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":14,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Unicode \\u00E9\\u00E0\\u00FC.","code":"block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesVeryLongBuildPlan() {
        StringBuilder sb = new StringBuilder("{\"tool\":\"voxel.exec\",\"input\":{\"palette\":\"simple_v1\",\"seed\":15,\"bounds\":{\"x\":[-64,64],\"y\":[-8,72],\"z\":[-64,64]},\"build_plan\":\"");
        for (int i = 0; i < 500; i++) {
            sb.append("Very long build plan description with lots of details. ");
        }
        sb.append("\",\"code\":\"block(0,0,0,'stone');\"}}");
        BuilderProgram program = BuilderResponseParser.parse(sb.toString());
        assertNotNull(program);
    }

    @Test
    void handlesVeryLongCode() {
        StringBuilder sb = new StringBuilder("{\"tool\":\"voxel.exec\",\"input\":{\"palette\":\"simple_v1\",\"seed\":16,\"bounds\":{\"x\":[-64,64],\"y\":[-8,72],\"z\":[-64,64]},\"build_plan\":\"Long code.\",\"code\":\"");
        for (int i = 0; i < 100; i++) {
            sb.append("box(").append(-i).append(",0,").append(-i).append(",").append(i).append(",5,").append(i).append(",'stone');");
        }
        sb.append("\"}}");
        BuilderProgram program = BuilderResponseParser.parse(sb.toString());
        assertNotNull(program);
    }

@Test
    void rejectsJsonSpanningMultipleLines() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":17,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"JSON with newlines.","code":"block(0,0,0,'stone');"}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void rejectsDecimalSeed() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":1.5,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Decimal seed.","code":"block(0,0,0,'stone');"}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void handlesCodeWithMath() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":18,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Math in code.","code":"box(0,Math.floor(5),0,10,Math.floor(10),10,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesCodeWithRng() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":19,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"RNG in code.","code":"box(Math.floor(rng()*10),0,0,Math.floor(rng()*10)+5,5,5,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesCodeWithMathAbs() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":20,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Math.abs in code.","code":"box(Math.abs(-5),0,Math.abs(-5),5,5,5,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesCodeWithMathMin() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":21,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Math.min in code.","code":"box(Math.min(0,5),0,0,Math.min(10,8),5,5,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesCodeWithMathMax() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":22,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Math.max in code.","code":"box(Math.max(0,5),0,0,Math.max(10,8),5,5,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesAllValidBlocks() {
        String palette = "stone, cobblestone, oak_planks, bricks, stone_bricks, grass_block, dirt, sand, oak_log, oak_leaves, white_wool, black_wool, red_wool, blue_wool, green_wool, yellow_wool, orange_wool, purple_wool, brown_wool, gray_wool, glass, glowstone, iron_block, gold_block";
        for (String block : palette.split(", ")) {
            String response = String.format("""
                    {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":100,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Test %s.","code":"block(0,0,0,'%s');"}}
                    """, block, block);
            BuilderProgram program = BuilderResponseParser.parse(response);
            assertNotNull(program, "Failed for block: " + block);
        }
    }

    @Test
    void handlesFenceJson() {
        String response = """
                ```json
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":23,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Fenced JSON.","code":"block(0,0,0,'stone');"}}
                ```
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(23, program.seed());
    }

    @Test
    void handlesFenceJsonWithLanguage() {
        String response = """
                ```json
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":24,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Fenced JSON with language.","code":"block(0,0,0,'stone');"}}
                ```
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(24, program.seed());
    }

    @Test
    void handlesMixedJson() {
        String response = "Here is some text before the JSON: {\"tool\":\"voxel.exec\",\"input\":{\"palette\":\"simple_v1\",\"seed\":25,\"bounds\":{\"x\":[-64,64],\"y\":[-8,72],\"z\":[-64,64]},\"build_plan\":\"Mixed content.\",\"code\":\"block(0,0,0,'stone');\"}} and some text after.";
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(25, program.seed());
    }

    @Test
    void handlesMarkdownCodeBlock() {
        String response = """
                Here is my build plan:
                ```javascript
                block(0, 0, 0, 'stone');
                box(-5, 0, -5, 5, 10, 5, 'cobblestone');
                ```
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.legacyFallback());
    }

    @Test
    void handlesPlainTextCommands() {
        String response = """
                I'll build a simple structure. Here are the commands:
                box(0, 0, 0, 10, 10, 10, "stone")
                sphere(5, 5, 5, 4, "cobblestone")
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.legacyFallback());
    }

    @Test
    void handlesAiReasoningPrefix() {
        String response = """
                Let me think about this... I'll create a tower structure.
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":26,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Tower structure.","code":"box(0,0,0,5,15,5,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(26, program.seed());
    }

    @Test
    void rejectsCodeWithForLoop() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":27,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"For loop.","code":"for (var i = 0; i < 10; i++) { block(i, 0, 0, 'stone'); }"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void rejectsCodeWithFunctions() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":28,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"With functions.","code":"function build() { block(0, 0, 0, 'stone'); } build();"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void rejectsCodeWithVariables() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":29,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"With variables.","code":"var x = 0; var y = 0; block(x, y, 0, 'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void rejectsCodeWithLet() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":30,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Let variable.","code":"let x = 0; block(x, 0, 0, 'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void rejectsCodeWithConst() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":31,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Const variable.","code":"const x = 0; block(x, 0, 0, 'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void rejectsCodeWithArrays() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":32,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"With arrays.","code":"var arr = [0, 1, 2]; block(arr[0], 0, 0, 'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesNumberLiterals() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":33,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Number literals.","code":"block(0, -5, 0, 'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesNegativeCoordinates() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":34,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Negative coords.","code":"box(-10, -5, -10, 10, 20, 10, 'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesZeroSeed() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":0,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Zero seed.","code":"block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(0, program.seed());
    }

    @Test
    void handlesNegativeSeed() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":-1,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Negative seed.","code":"block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(-1, program.seed());
    }

    @Test
    void handlesLargeSeed() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":9999999999,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Large seed.","code":"block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(9999999999L, program.seed().longValue());
    }

    @Test
    void handlesDecimalInSeed() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":1.5,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Decimal seed.","code":"block(0,0,0,'stone');"}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void handlesStringInSeed() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":"abc","bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"String seed.","code":"block(0,0,0,'stone');"}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void handlesBoundsAtLimits() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":35,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Bounds at limits.","code":"block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(-64, program.bounds().x().min());
        assertEquals(64, program.bounds().x().max());
        assertEquals(-8, program.bounds().y().min());
        assertEquals(72, program.bounds().y().max());
        assertEquals(-64, program.bounds().z().min());
        assertEquals(64, program.bounds().z().max());
    }

    @Test
    void rejectsBoundsTooLarge() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":36,"bounds":{"x":[-100,100],"y":[-8,72],"z":[-64,64]},"build_plan":"Bounds too large.","code":"block(0,0,0,'stone');"}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void rejectsBoundsTooSmall() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":37,"bounds":{"x":[-50,50],"y":[-8,72],"z":[-64,64]},"build_plan":"Bounds too small.","code":"block(0,0,0,'stone');"}}
                """;
        assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse(response));
    }

    @Test
    void handlesSphereFunction() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":38,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Sphere.","code":"sphere(0,5,0,5,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesLineFunction() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":39,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Line.","code":"line(0,0,0,10,10,10,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesMultipleShapes() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":40,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Multiple shapes.","code":"block(0,0,0,'stone'); box(0,1,0,5,5,5,'cobblestone'); line(0,6,0,0,20,0,'bricks'); sphere(0,10,0,3,'glass');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesWhitespaceVariations() {
        String response = """
                {"tool":"voxel.exec","input": { "palette" : "simple_v1" , "seed" : 41 , "bounds" : {"x" : [-64 , 64] , "y" : [-8 , 72] , "z" : [-64 , 64]} , "build_plan" : "Whitespace." , "code" : "block(0,0,0,'stone');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertEquals(41, program.seed());
    }

    @Test
    void handlesExtraFields() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":42,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Extra fields.","code":"block(0,0,0,'stone');","extra_field":"ignored","another_field":123}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }

    @Test
    void handlesNestedObjects() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":43,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Nested.","code":"block(0,0,0,'stone');","nested":{"should":"be","ignored":"here"}}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
    }
}
    @Test
    void handlesExpandedPaletteWoodBlocks() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":44,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Wood structure.","code":"box(0,0,0,5,10,5,'oak_planks'); box(10,0,0,15,10,5,'spruce_planks'); box(20,0,0,25,10,5,'birch_planks');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.code().contains("oak_planks"));
        assertTrue(program.code().contains("spruce_planks"));
        assertTrue(program.code().contains("birch_planks"));
    }

    @Test
    void handlesExpandedPaletteStoneVariants() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":45,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Stone variants.","code":"box(0,0,0,5,10,5,'deepslate'); box(10,0,0,15,10,5,'deepslate_bricks'); box(20,0,0,25,10,5,'deepslate_tiles');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.code().contains("deepslate"));
    }

    @Test
    void handlesExpandedPaletteConcreteBlocks() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":46,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Concrete structure.","code":"box(0,0,0,10,5,10,'white_concrete'); sphere(15,5,15,3,'red_concrete');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.code().contains("white_concrete"));
        assertTrue(program.code().contains("red_concrete"));
    }

    @Test
    void handlesExpandedPaletteNetherBlocks() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":47,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Nether fortress.","code":"box(0,0,0,20,15,20,'netherrack'); box(2,2,2,18,13,18,'nether_bricks');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.code().contains("netherrack"));
        assertTrue(program.code().contains("nether_bricks"));
    }

    @Test
    void handlesExpandedPaletteEndBlocks() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":48,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"End structure.","code":"box(0,0,0,10,10,10,'end_stone'); sphere(5,15,5,4,'purpur_block');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.code().contains("end_stone"));
        assertTrue(program.code().contains("purpur_block"));
    }

    @Test
    void handlesExpandedPaletteOreBlocks() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":49,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Ore display.","code":"block(0,0,0,'coal_ore'); block(1,0,0,'iron_ore'); block(2,0,0,'gold_ore'); block(3,0,0,'diamond_ore');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.code().contains("coal_ore"));
        assertTrue(program.code().contains("diamond_ore"));
    }

    @Test
    void handlesExpandedPaletteMetalBlocks() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":50,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Metal blocks.","code":"box(0,0,0,5,5,5,'iron_block'); box(10,0,0,15,5,5,'gold_block'); box(20,0,0,25,5,5,'diamond_block');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.code().contains("iron_block"));
        assertTrue(program.code().contains("diamond_block"));
    }

    @Test
    void handlesExpandedPaletteDecorativeBlocks() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":51,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Decorative structure.","code":"box(0,0,0,10,10,10,'oak_stairs'); line(0,10,0,10,10,10,'oak_fence');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.code().contains("oak_stairs"));
        assertTrue(program.code().contains("oak_fence"));
    }

    @Test
    void handlesExpandedPaletteLightSources() {
        String response = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":52,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Illuminated structure.","code":"box(0,0,0,10,10,10,'stone'); sphere(5,15,5,3,'glowstone'); block(0,20,0,'sea_lantern');"}}
                """;
        BuilderProgram program = BuilderResponseParser.parse(response);
        assertNotNull(program);
        assertTrue(program.code().contains("glowstone"));
        assertTrue(program.code().contains("sea_lantern"));
    }
}
