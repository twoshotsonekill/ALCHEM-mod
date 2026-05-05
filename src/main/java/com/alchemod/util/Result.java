package com.alchemod.util;

/**
 * Generic result wrapper for success/error handling.
 * Replaces per-block result records (CreationResult, ForgeResult, TransmuterResult)
 * with a unified pattern.
 *
 * @param <T> The type of successful result data
 */
public class Result<T> {
    private final T data;
    private final String error;

    private Result(T data) {
        this.data = data;
        this.error = null;
    }

    private Result(String error) {
        this.data = null;
        this.error = error;
    }

    /**
     * Create a successful result.
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(data);
    }

    /**
     * Create an error result.
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(message);
    }

    /**
     * Check if this result is an error.
     */
    public boolean isError() {
        return error != null;
    }

    /**
     * Get the successful data (null if error).
     */
    public T get() {
        return data;
    }

    /**
     * Get the error message (null if success).
     */
    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return isError() ? "Result.error(" + error + ")" : "Result.success(" + data + ")";
    }
}
