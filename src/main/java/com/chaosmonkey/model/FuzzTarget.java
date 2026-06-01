package com.chaosmonkey.model;

import java.util.Map;

/**
 * Represents a single endpoint and HTTP method extracted from the OpenAPI spec.
 * fields maps each field name to its declared type and constraints.
 *
 * One FuzzTarget is produced per endpoint per HTTP method.
 * Example: POST /pet and GET /pet are two separate FuzzTargets.
 *
 * multipart (v0.4) is true when the endpoint declares a
 * multipart/form-data request body. Chaos Monkey sends JSON, so these
 * endpoints are fuzzed but their results are marked MULTIPART_UNSUPPORTED
 * rather than counted as genuine server errors.
 */
public record FuzzTarget(
        String path,
        String method,
        Map<String, FieldSchema> fields,
        boolean multipart
) {}