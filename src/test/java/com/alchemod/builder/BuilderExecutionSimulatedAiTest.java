package com.alchemod.builder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuilderExecutionSimulatedAiTest {

    @Test
    void executesAiGeneratedCode() {
        String simulatedAiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":42,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"A tower.","code":"box(0,0,0,5,15,5,'stone'); box(1,1,1,4,14,4,'cobblestone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(simulatedAiResponse);
        assertNotNull(program);

        List<String> placements = new ArrayList<>();
        BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z + ":" + blockId));

        assertFalse(placements.isEmpty());
        System.out.println("Generated " + placements.size() + " placements");
    }

    @Test
    void executesSimulatedAiResponseWithConst() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":123,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Test with const.","code":"tconst=5; block(tconst,0,0,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        List<String> placements = new ArrayList<>();
        try {
            BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z));
            System.out.println("SUCCESS: Generated " + placements.size() + " placements (const was allowed)");
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            throw e;
        }
    }

    @Test
    void executesSimulatedAiResponseWithLet() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":456,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Test with let.","code":"for(let i=0;i<3;i++){box(i,0,0,'stone');}"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        assertThrows(IllegalArgumentException.class, () -> {
            List<String> placements = new ArrayList<>();
            BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z));
        }, "Expected execution to fail due to 'let' declaration");
    }

    @Test
    void executesSimulatedAiResponseWithVar() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":789,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Test with var.","code":"var x=0; block(x,0,0,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        List<String> placements = new ArrayList<>();
        BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z));
        assertFalse(placements.isEmpty(), "var keyword should be stripped and code should execute");
    }

    @Test
    void executesSimulatedAiResponseWithTripleDots() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":999,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Test with triple dots.","code":"for(let dx=-3;dx<=3;dx+=3...){block(dx,0,0,'stone');}"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        // The code cleaner strips the triple dots, so the code should execute successfully
        List<String> placements = new ArrayList<>();
        BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z));
        assertFalse(placements.isEmpty(), "Expected some placements after cleaning triple dots");
    }

    @Test
    void executesSimulatedAiResponseWithNoise() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":111,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Test with noise.","code":"var n=rng.noise2(0,0); box(n,0,0,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        assertThrows(IllegalArgumentException.class, () -> {
            List<String> placements = new ArrayList<>();
            BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z));
        }, "Expected execution to fail due to unsupported rng.noise2() function");
    }

    @Test
    void documentsActualAiResponseStructure() {
        String actualAiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":123,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"A colossal fantasy stone gate fortress spans a deep ravine. Twin arched towers flank the gateway, each with layered battlements and buttressed supports. A massive central portcullis gate towers between them, with hanging chains. A stone bridge stretches across the gap, featuring decorative iron supports and flags. The area around the fortress is rugged, with rock outcrops, minor ruined outbuildings, and tree clusters. Upper walkways, balconies, and lookout turrets protrude from the main mass, while detailed steps and multi-level walls provide complex, iconic silhouette from all sides.","code":"// PRIMARY: Main ravine span with fortress and gate\\nconst = 38; // tower height\\nconst baseY = 0;\\nconst ravineY = -3;\\nconst bridgeY = 23;\\nconst gateW = 13;\\nconst gateH = 23;\\nconst towerR = 9;\\nconst towerGap = gateW+towerR*2+7; // distance between outside walls of towers\\nconst pGate = [0,baseY+4,0];\\n\\n// Tower positions\\nconst t1=[-towerGap/2,baseY,towerR+9];\\nconst t2=[towerGap/2,baseY,towerR+9];\\n\\n// Terrain: Build some elevated edges for ravine\\ntopZ = 15;\\nfor(let x=-60;x<=60;x+=6){\\n  let edge = Math.floor(rng.noise2(x,12)*5+24);\\n  box('stone',x,ravineY,x-3,x+3,ravineY+edge,topZ+rng()*(x%3*5));\\n  box('dirt',x,ravineY+edge,x-3,x+3,ravineY+edge+2,topZ+2+rng()*3);\\n  box('grass_block',x,ravineY+edge+2,x-3,x+3,ravineY+edge+3,topZ+3+rng()*2);\\n}"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(actualAiResponse);
        assertNotNull(program);
        assertEquals(123L, program.seed());
    }

    @Test
    void testSimpleBoxCode() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":1,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Simple box.","code":"box(0,0,0,10,10,10,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        List<String> placements = new ArrayList<>();
        BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z));

        assertFalse(placements.isEmpty());
        assertTrue(placements.size() > 1000, "Should have many placements for a 10x10x10 box");
    }

    @Test
    void testSphereCode() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":2,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Simple sphere.","code":"sphere(0,5,0,5,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        List<String> placements = new ArrayList<>();
        BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z));

        assertFalse(placements.isEmpty());
        System.out.println("Sphere generated " + placements.size() + " placements");
    }

    @Test
    void testLineCode() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":3,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Simple line.","code":"line(0,0,0,20,20,20,'stone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        List<String> placements = new ArrayList<>();
        BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z));

        assertFalse(placements.isEmpty());
        System.out.println("Line generated " + placements.size() + " placements");
    }

    @Test
    void testMixedCode() {
        String aiResponse = """
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":4,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Mixed shapes.","code":"box(-5,0,-5,5,20,5,'stone'); line(-5,20,0,5,20,0,'glowstone'); sphere(0,10,0,4,'cobblestone');"}}
                """;

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        assertNotNull(program);

        List<String> placements = new ArrayList<>();
        BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z + ":" + blockId));

        assertFalse(placements.isEmpty());
        System.out.println("Mixed generated " + placements.size() + " placements");
    }

    @Test
    void debugAiCodeExecution() {
        String code = "box(-5,0,-5,5,10,5,'stone');";
        String aiResponse = String.format("""
                {"tool":"voxel.exec","input":{"palette":"simple_v1","seed":1,"bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},"build_plan":"Debug test.","code":"%s"}}
                """, code);

        System.out.println("Testing code: " + code);
        System.out.println("Full response: " + aiResponse);

        BuilderProgram program = BuilderResponseParser.parse(aiResponse);
        System.out.println("Parsed program: " + program);

        List<String> placements = new ArrayList<>();
        try {
            BuilderRuntime.execute(program, 0, (x, y, z, blockId) -> placements.add(x + "," + y + "," + z + ":" + blockId));
            System.out.println("SUCCESS: " + placements.size() + " placements");
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
