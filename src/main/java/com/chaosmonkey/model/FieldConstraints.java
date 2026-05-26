package com.chaosmonkey.model;

import java.util.List;

/**
 * Holds all OpenAPI constraints declared for a single field.
 * All fields are nullable — a null value means the constraint
 * was not declared in the spec.
 *
 * Not used in v0.1 (no spec parsing yet) but declared now
 * so the model layer is stable across versions.
 */
public record FieldConstraints(
        Integer minimum,
        Integer maximum,
        Integer minLength,
        Integer maxLength,
        Boolean nullable,
        List<String> enumValues
) {}