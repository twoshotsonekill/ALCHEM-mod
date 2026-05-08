package com.alchemod.builder;

import com.alchemod.ai.OpenRouterClient;

public final class BuilderBuildPlanner {

    private BuilderBuildPlanner() {
    }

    @FunctionalInterface
    public interface RepairClient {
        OpenRouterClient.ChatResult repair(String systemPrompt, String userPrompt);
    }

    public static BuildResult plan(
            String prompt,
            String rawResponse,
            String primaryError,
            int fallbackSeed,
            RepairClient repairClient
    ) {
        if (primaryError != null && !primaryError.isBlank()) {
            return fallback(prompt, fallbackSeed, false, "OpenRouter request failed: " + primaryError, rawResponse, null);
        }

        Candidate primary = validate(rawResponse, fallbackSeed);
        if (primary.valid()) {
            return new BuildResult(
                    primary.program(), primary.preview(),
                    BuilderDiagnostics.parsed(primary.preview().placementCount(), primary.preview().seedUsed()),
                    rawResponse, null, null);
        }

        String repairedResponse = null;
        if (repairClient != null) {
            OpenRouterClient.ChatResult repaired = repairClient.repair(
                    BuilderPromptFactory.buildRepairSystemPrompt(),
                    BuilderPromptFactory.buildRepairUserPrompt(prompt, rawResponse, primary.error()));
            repairedResponse = repaired.rawBody() != null ? repaired.rawBody() : repaired.content();

            if (repaired.isError()) {
                return fallback(prompt, fallbackSeed, true,
                        primary.error() + "; repair request failed: " + repaired.error(),
                        rawResponse, repairedResponse);
            }

            Candidate repairedCandidate = validate(repaired.content(), fallbackSeed);
            if (repairedCandidate.valid()) {
                return new BuildResult(
                        repairedCandidate.program(),
                        repairedCandidate.preview(),
                        BuilderDiagnostics.repaired(
                                repairedCandidate.preview().placementCount(),
                                repairedCandidate.preview().seedUsed(),
                                primary.error()),
                        rawResponse,
                        repairedResponse,
                        primary.error());
            }

            return fallback(prompt, fallbackSeed, true,
                    primary.error() + "; repair invalid: " + repairedCandidate.error(),
                    rawResponse, repairedResponse);
        }

        return fallback(prompt, fallbackSeed, false, primary.error(), rawResponse, null);
    }

    private static Candidate validate(String response, int fallbackSeed) {
        try {
            BuilderProgram program = BuilderResponseParser.parse(response);
            BuilderRuntime.PlacementPreview preview = BuilderRuntime.preview(program, fallbackSeed);
            BuilderRuntime.QualityReport quality = BuilderRuntime.assessQuality(preview);
            if (!quality.accepted()) {
                return Candidate.invalid("quality check failed: " + quality.reason());
            }
            return Candidate.valid(program, preview);
        } catch (Exception e) {
            return Candidate.invalid(e.getMessage());
        }
    }

    private static BuildResult fallback(
            String prompt,
            int fallbackSeed,
            boolean repairAttempted,
            String reason,
            String rawResponse,
            String repairedResponse
    ) {
        try {
            BuilderProgram program = BuilderLocalFallback.create(prompt, fallbackSeed, reason);
            BuilderRuntime.PlacementPreview preview = BuilderRuntime.preview(program, fallbackSeed);
            return new BuildResult(
                    program,
                    preview,
                    BuilderDiagnostics.fallback(repairAttempted, reason, preview.placementCount(), preview.seedUsed()),
                    rawResponse,
                    repairedResponse,
                    reason);
        } catch (Exception e) {
            return new BuildResult(
                    null,
                    null,
                    BuilderDiagnostics.failed(repairAttempted, reason + "; local fallback failed: " + e.getMessage()),
                    rawResponse,
                    repairedResponse,
                    reason);
        }
    }

    private record Candidate(BuilderProgram program, BuilderRuntime.PlacementPreview preview, String error) {
        static Candidate valid(BuilderProgram program, BuilderRuntime.PlacementPreview preview) {
            return new Candidate(program, preview, null);
        }

        static Candidate invalid(String error) {
            return new Candidate(null, null, BuilderDiagnostics.shortError(error));
        }

        boolean valid() {
            return program != null && preview != null;
        }
    }

    public record BuildResult(
            BuilderProgram program,
            BuilderRuntime.PlacementPreview preview,
            BuilderDiagnostics diagnostics,
            String rawResponse,
            String repairedResponse,
            String validationError
    ) {
        public boolean ok() {
            return program != null && preview != null;
        }
    }
}
