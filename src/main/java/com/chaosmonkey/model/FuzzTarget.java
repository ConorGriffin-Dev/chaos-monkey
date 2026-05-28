package com.chaosmonkey.model;

import java.util.Map;

/**
 * Represents a single endpoint and HTTP method extracted from the OpenAPI spec.
 * fields maps each field name to its declared type and constraints.
 *
 * One FuzzTarget is produced per endpoint per HTTP method.
 * Example: POST /pet and GET /pet are two separate FuzzTargets.
 */
public record FuzzTarget(
        String path,
        String method,
        Map<String, FieldSchema> fields
) {}