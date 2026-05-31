package com.chaosmonkey.model;

/**
 * Represents the declared type, constraints, and location of a single
 * field extracted from the OpenAPI spec.
 *
 * type maps to the OpenAPI primitive types:
 * "string", "integer", "number", "boolean", "array", "object"
 *
 * in maps to the OpenAPI parameter location:
 * "path", "query", "header", or "body" (for request body fields).
 *
 * If no type is declared in the spec, type defaults to "string"
 * and fuzzing falls back to the static string payload list.
 */
public record FieldSchema(
        String type,
        String in,
        FieldConstraints constraints
) {}