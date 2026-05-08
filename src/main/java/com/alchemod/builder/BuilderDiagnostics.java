package com.alchemod.builder;

import java.util.Locale;

public record BuilderDiagnostics(
        String parseStatus,
        boolean repairAttempted,
        String fallbackReason,
        int placementCount,
        int seed,
        String errorSummary
) {
    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_PARSED = "parsed";
    public static final String STATUS_REPAIRED = "repaired";
    public static final String STATUS_FALLBACK = "fallback";
    public static final String STATUS_FAILED = "failed";

    public static BuilderDiagnostics idle() {
        return new BuilderDiagnostics(STATUS_IDLE, false, "", 0, 0, "");
    }

    public static BuilderDiagnostics parsed(int placementCount, int seed) {
        return new BuilderDiagnostics(STATUS_PARSED, false, "", placementCount, seed, "");
    }

    public static BuilderDiagnostics repaired(int placementCount, int seed, String previousError) {
        return new BuilderDiagnostics(STATUS_REPAIRED, true, "", placementCount, seed, shortError(previousError));
    }

    public static BuilderDiagnostics fallback(boolean repairAttempted, String reason, int placementCount, int seed) {
        return new BuilderDiagnostics(
                STATUS_FALLBACK, repairAttempted, shortError(reason), placementCount, seed, shortError(reason));
    }

    public static BuilderDiagnostics failed(boolean repairAttempted, String error) {
        return new BuilderDiagnostics(STATUS_FAILED, repairAttempted, "", 0, 0, shortError(error));
    }

    public int parseStatusCode() {
        return switch (normalise(parseStatus)) {
            case STATUS_PARSED -> 1;
            case STATUS_REPAIRED -> 2;
            case STATUS_FALLBACK -> 3;
            case STATUS_FAILED -> 4;
            default -> 0;
        };
    }

    public int fallbackReasonCode() {
        String reason = normalise(fallbackReason + " " + errorSummary);
        if (reason.isBlank()) return 0;
        if (reason.contains("quality") || reason.contains("few") || reason.contains("shape")) return 1;
        if (reason.contains("parse") || reason.contains("json")) return 2;
        if (reason.contains("palette") || reason.contains("block")) return 3;
        if (reason.contains("budget") || reason.contains("bounds")) return 4;
        if (reason.contains("api") || reason.contains("openrouter") || reason.contains("http")) return 5;
        return 6;
    }

    public String guiSummary() {
        String status = normalise(parseStatus);
        if (STATUS_IDLE.equals(status)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(status);
        if (repairAttempted) {
            builder.append(" repair");
        }
        if (placementCount > 0) {
            builder.append(" ").append(placementCount).append(" blocks");
        }
        if (!fallbackReason.isBlank()) {
            builder.append(" fallback: ").append(shortError(fallbackReason));
        } else if (!errorSummary.isBlank() && STATUS_FAILED.equals(status)) {
            builder.append(" error: ").append(shortError(errorSummary));
        }
        return builder.toString();
    }

    public static String statusFromCode(int code) {
        return switch (code) {
            case 1 -> STATUS_PARSED;
            case 2 -> STATUS_REPAIRED;
            case 3 -> STATUS_FALLBACK;
            case 4 -> STATUS_FAILED;
            default -> STATUS_IDLE;
        };
    }

    public static String fallbackReasonFromCode(int code) {
        return switch (code) {
            case 1 -> "quality";
            case 2 -> "parse";
            case 3 -> "palette";
            case 4 -> "bounds";
            case 5 -> "api";
            case 6 -> "validation";
            default -> "";
        };
    }

    public static String shortError(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 120 ? cleaned : cleaned.substring(0, 117) + "...";
    }

    private static String normalise(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
