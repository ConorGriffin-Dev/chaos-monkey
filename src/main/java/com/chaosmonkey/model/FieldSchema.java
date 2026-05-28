package com.chaosmonkey.model;

/**
 * Represents the declared type and constraints for a single field
 * extracted from the OpenAPI spec.
 *
 * type maps to the OpenAPI primitive types:
 * "string", "integer", "number", "boolean", "array", "object"
 *
 * If no type is declared in the spec, type defaults to "string"
 * and fuzzing falls back to the static string payload list.
 */
public record FieldSchema(
        String type,
        FieldConstraints constraints
) {}