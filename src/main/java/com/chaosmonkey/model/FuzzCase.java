package com.chaosmonkey.model;

/**
 * Represents a single fuzz request to be fired —
 * one specific payload targeting one specific field
 * on one specific endpoint.
 *
 * Produced by FuzzPayloadGenerator, consumed by FuzzRunner.
 * One FuzzCase produces one FuzzResult.
 */
public record FuzzCase(
        FuzzTarget target,
        String fieldName,
        Object payload
) {}