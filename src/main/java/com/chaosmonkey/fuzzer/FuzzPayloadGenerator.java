package com.chaosmonkey.fuzzer;

import com.chaosmonkey.model.FieldConstraints;
import com.chaosmonkey.model.FieldSchema;
import com.chaosmonkey.model.FuzzCase;
import com.chaosmonkey.model.FuzzTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a list of FuzzCase objects for each FuzzTarget.
 *
 * For each field in each FuzzTarget, generates payloads based on
 * the declared type and constraints — producing inputs specifically
 * designed to violate the field's rules.
 *
 * Schema-aware generation is supplemented by the static payload
 * library for string fields.
 */
@Service
public class FuzzPayloadGenerator {

    private static final Logger log = LoggerFactory.getLogger(FuzzPayloadGenerator.class);

    private final FuzzPayloadLibrary payloadLibrary;

    public FuzzPayloadGenerator(FuzzPayloadLibrary payloadLibrary) {
        this.payloadLibrary = payloadLibrary;
    }

    /**
     * Generates all FuzzCases for a list of FuzzTargets.
     * One FuzzCase is produced per field per payload.
     */
    public List<FuzzCase> generateAll(List<FuzzTarget> targets) {
        List<FuzzCase> cases = new ArrayList<>();

        for (FuzzTarget target : targets) {
            target.fields().forEach((fieldName, schema) -> {
                List<Object> payloads = generateForSchema(schema);
                for (Object payload : payloads) {
                    cases.add(new FuzzCase(target, fieldName, payload));
                }
            });
        }

        log.info("FuzzPayloadGenerator complete — {} FuzzCases generated from {} targets",
                cases.size(), targets.size());
        return cases;
    }

    /**
     * Generates payloads for a single field based on its declared type.
     * Delegates to type-specific methods, then applies constraint-aware
     * boundary generation on top.
     */
    public List<Object> generateForSchema(FieldSchema schema) {
        List<Object> payloads = new ArrayList<>(generateForType(schema.type()));
        payloads.addAll(generateConstraintPayloads(schema.type(), schema.constraints()));
        return payloads;
    }

    /**
     * Generates base payloads for a given OpenAPI primitive type.
     * Falls back to static string list for unknown types.
     */
    public List<Object> generateForType(String type) {
        return switch (type) {
            case "integer" -> generateIntegerPayloads();
            case "number"  -> generateNumberPayloads();
            case "boolean" -> generateBooleanPayloads();
            case "array"   -> generateArrayPayloads();
            case "object"  -> generateObjectPayloads();
            default        -> generateStringPayloads(); // "string" + unknown types
        };
    }

    // ── Type-specific payload generators ─────────────────────────────────────

    private List<Object> generateIntegerPayloads() {
        List<Object> payloads = new ArrayList<>();
        payloads.add(null);
        payloads.add(0);
        payloads.add(-1);
        payloads.add(1);
        payloads.add(Integer.MAX_VALUE);
        payloads.add(Integer.MIN_VALUE);
        payloads.add(9_999_999_999L);   // overflow
        payloads.add(-9_999_999_999L);  // overflow negative
        payloads.add("not_an_int");     // wrong type
        payloads.add("");               // empty string
        payloads.add(3.14);             // float sent to int field
        return payloads;
    }

    private List<Object> generateNumberPayloads() {
        List<Object> payloads = new ArrayList<>(generateIntegerPayloads());
        payloads.add(Double.NaN);
        payloads.add(Double.POSITIVE_INFINITY);
        payloads.add(Double.NEGATIVE_INFINITY);
        payloads.add(Double.MAX_VALUE);
        payloads.add(Double.MIN_VALUE);
        return payloads;
    }

    private List<Object> generateStringPayloads() {
        List<Object> payloads = new ArrayList<>();
        payloads.add(null);
        payloads.add("");
        payloads.add(" ");
        payloads.add("null");
        payloads.add("undefined");
        // Supplement with the full static library
        payloads.addAll(payloadLibrary.getStrings());
        return payloads;
    }

    private List<Object> generateBooleanPayloads() {
        List<Object> payloads = new ArrayList<>();
        payloads.add(null);
        payloads.add("true");
        payloads.add("false");
        payloads.add(1);
        payloads.add(0);
        payloads.add("yes");
        payloads.add("no");
        payloads.add("TRUE");
        payloads.add(2);        // out of range boolean
        payloads.add("");
        return payloads;
    }

    private List<Object> generateArrayPayloads() {
        List<Object> payloads = new ArrayList<>();
        payloads.add(null);
        payloads.add(List.of());                        // empty array
        payloads.add(List.of("single"));                // single element
        payloads.add("not_an_array");                   // wrong type

        // Mixed types — using ArrayList because List.of() rejects null elements
        List<Object> mixed = new ArrayList<>();
        mixed.add(1);
        mixed.add("mixed");
        mixed.add(null);
        mixed.add(true);
        payloads.add(mixed);

        payloads.add(generateLargeArray());             // large array
        return payloads;
    }

    private List<Object> generateObjectPayloads() {
        List<Object> payloads = new ArrayList<>();
        payloads.add(null);
        payloads.add(java.util.Map.of());                        // empty object
        payloads.add("not_an_object");                           // wrong type
        payloads.add(java.util.Map.of(
                "unexpected_field", "chaos",
                "another_field", 99999));                        // unknown fields
        return payloads;
    }

    // ── Constraint-aware boundary generation ──────────────────────────────────

    /**
     * Generates payloads that specifically violate the declared constraints.
     * These are added on top of the base type payloads.
     */
    private List<Object> generateConstraintPayloads(String type, FieldConstraints constraints) {
        List<Object> payloads = new ArrayList<>();

        if (constraints == null) return payloads;

        // Integer/number boundary violations
        if (constraints.minimum() != null) {
            payloads.add(constraints.minimum() - 1);  // below minimum
            payloads.add(constraints.minimum() - 100); // well below minimum
        }

        if (constraints.maximum() != null) {
            payloads.add(constraints.maximum() + 1);   // above maximum
            payloads.add(constraints.maximum() + 100); // well above maximum
        }

        // String length boundary violations
        if (constraints.minLength() != null && constraints.minLength() > 0) {
            // String shorter than minLength
            payloads.add("A".repeat(Math.max(0, constraints.minLength() - 1)));
        }

        if (constraints.maxLength() != null) {
            // String one character longer than maxLength
            payloads.add("A".repeat(constraints.maxLength() + 1));
            // String significantly longer than maxLength
            payloads.add("A".repeat(constraints.maxLength() + 100));
        }

        // Null sent to a non-nullable field
        if (constraints.nullable() != null && !constraints.nullable()) {
            payloads.add(null);
        }

        // Enum violations — send a value not in the enum list
        if (constraints.enumValues() != null && !constraints.enumValues().isEmpty()) {
            payloads.add("NOT_A_VALID_ENUM_VALUE");
            payloads.add("");
            payloads.add(null);
        }

        return payloads;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> generateLargeArray() {
        List<String> large = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            large.add("item" + i);
        }
        return large;
    }
}