package com.chaosmonkey.model;

/**
 * Holds all runtime configuration for a fuzz run.
 * Built from CLI arguments by ChaosMonkeyCLI and passed
 * through the component chain via Spring DI.
 *
 * specLocation and outputDir are not used in v0.1 —
 * declared here so the record is stable across versions.
 *
 * authHeader (v0.4) is the full Authorization header VALUE to attach
 * to every fuzz request (e.g. "Bearer eyJ..."). Null = no auth header.
 */
public record FuzzConfig(
        String baseUrl,
        String specLocation,
        String outputDir,
        int timeoutMs,
        boolean flagOnly,
        boolean dryRun,
        String authHeader
) {
    public static final int DEFAULT_TIMEOUT_MS = 10_000;
    public static final String DEFAULT_OUTPUT_DIR = "./allure-results";
}