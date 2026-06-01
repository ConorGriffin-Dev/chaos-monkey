package com.chaosmonkey.parser;

import com.chaosmonkey.model.FieldConstraints;
import com.chaosmonkey.model.FieldSchema;
import com.chaosmonkey.model.FuzzTarget;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an OpenAPI 3.x specification and produces a list of FuzzTarget
 * objects — one per endpoint per HTTP method.
 *
 * Each FuzzTarget contains the path, method, and a map of field names
 * to their declared type, location, and constraints. This gives
 * FuzzPayloadGenerator everything it needs to generate targeted payloads
 * per field, and gives FuzzRunner the location (path/query/header/body)
 * needed to fire each payload in the right place.
 *
 * Supports:
 * - Remote URLs and local file paths
 * - JSON and YAML specs
 * - Request body schemas (POST, PUT, PATCH)
 * - Path, query, and header parameter schemas
 * - $ref resolution (handled automatically by Swagger Parser)
 * - Fallback to "string" type for fields with no declared type
 */
@Service
public class OpenApiParser {

    private static final Logger log = LoggerFactory.getLogger(OpenApiParser.class);

    /**
     * Parses an OpenAPI spec from a URL or local file path and returns
     * a list of FuzzTargets — one per endpoint per HTTP method.
     *
     * @param specLocation  URL or file path to the OpenAPI spec
     * @return              list of FuzzTargets extracted from the spec
     * @throws IllegalArgumentException if the spec cannot be parsed
     */
    public List<FuzzTarget> parse(String specLocation) {
        log.info("OpenApiParser reading spec from: {}", specLocation);

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        OpenAPI openAPI = new OpenAPIV3Parser().read(specLocation, null, options);

        if (openAPI == null) {
            throw new IllegalArgumentException(
                    "Failed to parse OpenAPI spec from: " + specLocation
                            + " — check the URL or file path is valid and returns a valid OpenAPI 3.x spec");
        }

        if (openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            log.warn("OpenAPI spec parsed but no paths found — spec may be empty");
            return List.of();
        }

        List<FuzzTarget> targets = new ArrayList<>();

        openAPI.getPaths().forEach((path, pathItem) -> {

            if (pathItem.getGet() != null) {
                targets.add(buildTarget(path, "GET", pathItem.getGet(), null));
            }
            if (pathItem.getPost() != null) {
                targets.add(buildTarget(path, "POST", pathItem.getPost(),
                        extractRequestBodySchema(pathItem.getPost())));
            }
            if (pathItem.getPut() != null) {
                targets.add(buildTarget(path, "PUT", pathItem.getPut(),
                        extractRequestBodySchema(pathItem.getPut())));
            }
            if (pathItem.getPatch() != null) {
                targets.add(buildTarget(path, "PATCH", pathItem.getPatch(),
                        extractRequestBodySchema(pathItem.getPatch())));
            }
            if (pathItem.getDelete() != null) {
                targets.add(buildTarget(path, "DELETE", pathItem.getDelete(), null));
            }
        });

        log.info("OpenApiParser complete — {} FuzzTargets extracted from spec", targets.size());
        return targets;
    }

    /**
     * Builds a FuzzTarget for a single endpoint and HTTP method.
     * Combines fields from path/query/header parameters and the request body schema.
     */
    private FuzzTarget buildTarget(String path,
                                   String method,
                                   Operation operation,
                                   Schema<?> requestBodySchema) {
        Map<String, FieldSchema> fields = new HashMap<>();

        // Extract path, query, and header parameters
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                String in = param.getIn() != null ? param.getIn() : "query";
                if (param.getSchema() != null) {
                    fields.put(param.getName(), buildFieldSchema(param.getSchema(), in));
                } else {
                    // No schema declared — fall back to untyped string fuzzing
                    fields.put(param.getName(), fallbackSchema(in));
                }
            }
        }

        // Extract request body fields (POST, PUT, PATCH)
        if (requestBodySchema != null && requestBodySchema.getProperties() != null) {
            requestBodySchema.getProperties().forEach((fieldName, fieldSchema) ->
                    fields.put(fieldName, buildFieldSchema((Schema<?>) fieldSchema, "body")));
        }

        // If no fields found at all, add a generic "body" field for static fuzzing
        if (fields.isEmpty()) {
            fields.put("body", fallbackSchema("body"));
        }

        boolean multipart = consumesMultipart(operation);

        log.debug("FuzzTarget built — {} {} | fields: {} | multipart: {}",
                method, path, fields.keySet(), multipart);
        return new FuzzTarget(path, method, fields, multipart);
    }

    /**
     * True if the operation's request body declares a multipart/form-data
     * content type. Chaos Monkey sends JSON, so such endpoints can't be
     * fuzzed meaningfully — their results are marked MULTIPART_UNSUPPORTED.
     */
    private boolean consumesMultipart(Operation operation) {
        if (operation.getRequestBody() == null) return false;
        if (operation.getRequestBody().getContent() == null) return false;
        return operation.getRequestBody().getContent().keySet().stream()
                .anyMatch(ct -> ct != null && ct.toLowerCase().contains("multipart/form-data"));
    }

    /**
     * Extracts the request body schema from a POST, PUT, or PATCH operation.
     * Returns null if no request body or no JSON schema is declared.
     */
    @SuppressWarnings("rawtypes")
    private Schema<?> extractRequestBodySchema(Operation operation) {
        if (operation.getRequestBody() == null) return null;
        if (operation.getRequestBody().getContent() == null) return null;

        var content = operation.getRequestBody().getContent();

        // Prefer application/json — fall back to first available content type
        if (content.containsKey("application/json")) {
            return content.get("application/json").getSchema();
        }

        return content.values().stream()
                .findFirst()
                .map(mt -> (Schema<?>) mt.getSchema())
                .orElse(null);
    }

    /**
     * Builds a FieldSchema from an OpenAPI Schema object.
     * Extracts type and all declared constraints, tagged with its location.
     * Falls back to "string" type if no type is declared.
     */
    @SuppressWarnings("rawtypes")
    private FieldSchema buildFieldSchema(Schema<?> schema, String in) {
        String type = schema.getType() != null ? schema.getType() : "string";

        List<String> enumValues = null;
        if (schema.getEnum() != null) {
            enumValues = schema.getEnum().stream()
                    .map(Object::toString)
                    .toList();
        }

        FieldConstraints constraints = new FieldConstraints(
                schema.getMinimum() != null
                        ? schema.getMinimum().intValue() : null,
                schema.getMaximum() != null
                        ? schema.getMaximum().intValue() : null,
                schema.getMinLength(),
                schema.getMaxLength(),
                schema.getNullable(),
                enumValues
        );

        return new FieldSchema(type, in, constraints);
    }

    /**
     * Returns a default FieldSchema used when no type is declared in the spec.
     * Triggers static string fuzzing in FuzzPayloadGenerator.
     */
    private FieldSchema fallbackSchema(String in) {
        return new FieldSchema("string", in, new FieldConstraints(
                null, null, null, null, null, null));
    }
}