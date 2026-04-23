package com.alchemod.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderResponseParserTest {

    @Test
    void parsesStructuredVoxelExecJson() {
        BuilderProgram program = BuilderResponseParser.parse("""
                {
                  "tool":"voxel.exec",
                  "input":{
                    "palette":"simple_v1",
                    "seed":123,
                    "bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},
                    "build_plan":"Layered gate with two towers and a recessed arch.",
                    "code":"box(-2,0,-2,2,4,2,'stone');"
                  }
                }
                """);

        assertFalse(program.legacyFallback());
        assertEquals("simple_v1", program.palette());
        assertEquals(123, program.seed());
        assertEquals("Layered gate with two towers and a recessed arch.", program.buildPlan());
    }

    @Test
    void parsesFencedStructuredJson() {
        BuilderProgram program = BuilderResponseParser.parse("""
                ```json
                {
                  "tool":"voxel.exec",
                  "input":{
                    "palette":"simple_v1",
                    "seed":77,
                    "bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},
                    "build_plan":"A bridge with layered supports.",
                    "code":"line(-5,0,0,5,0,0,'stone');"
                  }
                }
                ```
                """);

        assertFalse(program.legacyFallback());
        assertEquals(77, program.seed());
        assertEquals("A bridge with layered supports.", program.buildPlan());
    }

    @Test
    void rejectsMissingTool() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse("""
                {
                  "input":{
                    "palette":"simple_v1",
                    "seed":1,
                    "bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},
                    "build_plan":"Missing tool field.",
                    "code":"block(0,0,0,'stone');"
                  }
                }
                """));

        assertTrue(error.getMessage().contains("tool"));
    }

    @Test
    void rejectsMissingCode() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse("""
                {
                  "tool":"voxel.exec",
                  "input":{
                    "palette":"simple_v1",
                    "seed":1,
                    "bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},
                    "build_plan":"Missing code field."
                  }
                }
                """));

        assertTrue(error.getMessage().contains("code"));
    }

    @Test
    void rejectsMalformedBuildPlan() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> BuilderResponseParser.parse("""
                {
                  "tool":"voxel.exec",
                  "input":{
                    "palette":"simple_v1",
                    "seed":1,
                    "bounds":{"x":[-64,64],"y":[-8,72],"z":[-64,64]},
                    "build_plan":["not","a","string"],
                    "code":"block(0,0,0,'stone');"
                  }
                }
                """));

        assertTrue(error.getMessage().contains("build_plan"));
    }

    @Test
    void fallsBackToLegacyCommandsWhenStructuredJsonIsUnavailable() {
        BuilderProgram program = BuilderResponseParser.parse("""
                {"tool":"voxel.exec","input":
                box(0,0,0,3,3,3,"stone")
                line(-2,4,0,2,4,0,"glass")
                """);

        assertTrue(program.legacyFallback());
        assertTrue(program.code().contains("box("));
        assertTrue(program.buildPlan().contains("Legacy fallback"));
    }
}
