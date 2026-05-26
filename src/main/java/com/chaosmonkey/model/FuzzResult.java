package com.chaosmonkey.model;

import java.util.List;

/**
 * Represents the outcome of a single fuzz request.
 * Produced by FuzzRunner and enriched with flags by ResponseAnalyser.
 *
 * flags is populated after analysis — an empty list means
 * the response triggered no detection rules (clean result).
 */
public record FuzzResult(
        String endpoint,
        String method,
        String fieldName,
        Object payload,
        int statusCode,
        String responseBody,
        long durationMs,
        List<String> flags
) {}