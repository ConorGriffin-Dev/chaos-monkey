package com.chaosmonkey.fuzzer;

import com.chaosmonkey.model.FieldConstraints;
import com.chaosmonkey.model.FieldSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzPayloadGeneratorTest {

    private FuzzPayloadGenerator generator;

    @BeforeEach
    void setUp() {
        FuzzPayloadLibrary library = new FuzzPayloadLibrary();
        library.load();
        generator = new FuzzPayloadGenerator(library);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private FieldSchema schema(String type) {
        return new FieldSchema(type, "body", new FieldConstraints(
                null, null, null, null, null, null));
    }

    private FieldSchema schema(String type, Integer min, Integer max,
                               Integer minLen, Integer maxLen) {
        return new FieldSchema(type, "body", new FieldConstraints(
                min, max, minLen, maxLen, null, null));
    }
    // ── Integer payloads ──────────────────────────────────────────────────────

    // FG01
    @Test
    void generateForType_integer_nullPayloadIncluded() {
        List<Object> payloads = generator.generateForType("integer");
        assertThat(payloads).containsNull();
    }

    // FG02
    @Test
    void generateForType_integer_intMaxIncluded() {
        List<Object> payloads = generator.generateForType("integer");
        assertThat(payloads).contains(Integer.MAX_VALUE);
    }

    // FG03
    @Test
    void generateForType_integer_intMinIncluded() {
        List<Object> payloads = generator.generateForType("integer");
        assertThat(payloads).contains(Integer.MIN_VALUE);
    }

    // FG04
    @Test
    void generateForType_integer_wrongTypeIncluded() {
        List<Object> payloads = generator.generateForType("integer");
        assertThat(payloads).contains("not_an_int");
    }

    @Test
    void generateForType_integer_zeroIncluded() {
        List<Object> payloads = generator.generateForType("integer");
        assertThat(payloads).contains(0);
    }

    @Test
    void generateForType_integer_negativeOneIncluded() {
        List<Object> payloads = generator.generateForType("integer");
        assertThat(payloads).contains(-1);
    }

    @Test
    void generateForType_integer_overflowValueIncluded() {
        List<Object> payloads = generator.generateForType("integer");
        assertThat(payloads).contains(9_999_999_999L);
    }

    // FG05
    @Test
    void generateForSchema_integerWithMinConstraint_belowMinIncluded() {
        FieldSchema schema = schema("integer", 1, null, null, null);
        List<Object> payloads = generator.generateForSchema(schema);
        assertThat(payloads).contains(0);  // minimum - 1
    }

    // FG06
    @Test
    void generateForSchema_integerWithMaxConstraint_aboveMaxIncluded() {
        FieldSchema schema = schema("integer", null, 100, null, null);
        List<Object> payloads = generator.generateForSchema(schema);
        assertThat(payloads).contains(101); // maximum + 1
    }

    @Test
    void generateForSchema_integerWithMinConstraint_wellBelowMinIncluded() {
        FieldSchema schema = schema("integer", 10, null, null, null);
        List<Object> payloads = generator.generateForSchema(schema);
        assertThat(payloads).contains(-90); // minimum - 100
    }

    // ── String payloads ───────────────────────────────────────────────────────

    // FG07
    @Test
    void generateForType_string_nullPayloadIncluded() {
        List<Object> payloads = generator.generateForType("string");
        assertThat(payloads).containsNull();
    }

    // FG08
    @Test
    void generateForType_string_emptyStringIncluded() {
        List<Object> payloads = generator.generateForType("string");
        assertThat(payloads).contains("");
    }

    // FG09
    @Test
    void generateForType_string_sqlInjectionIncluded() {
        List<Object> payloads = generator.generateForType("string");
        assertThat(payloads)
                .anyMatch(p -> p instanceof String s && s.contains("DROP TABLE"));
    }

    // FG10
    @Test
    void generateForType_string_xssPayloadIncluded() {
        List<Object> payloads = generator.generateForType("string");
        assertThat(payloads)
                .anyMatch(p -> p instanceof String s && s.contains("<script>"));
    }

    // FG11
    @Test
    void generateForType_string_hugeStringIncluded() {
        List<Object> payloads = generator.generateForType("string");
        assertThat(payloads)
                .anyMatch(p -> p instanceof String s && s.length() > 1000);
    }

    // FG12
    @Test
    void generateForSchema_stringWithMaxLength_aboveMaxLengthIncluded() {
        FieldSchema schema = schema("string", null, null, null, 50);
        List<Object> payloads = generator.generateForSchema(schema);
        assertThat(payloads)
                .anyMatch(p -> p instanceof String s && s.length() == 51);
    }

    @Test
    void generateForSchema_stringWithMinLength_belowMinLengthIncluded() {
        FieldSchema schema = schema("string", null, null, 5, null);
        List<Object> payloads = generator.generateForSchema(schema);
        assertThat(payloads)
                .anyMatch(p -> p instanceof String s && s.length() == 4);
    }

    // ── Boolean payloads ──────────────────────────────────────────────────────

    // FG13
    @Test
    void generateForType_boolean_nullIncluded() {
        List<Object> payloads = generator.generateForType("boolean");
        assertThat(payloads).containsNull();
    }

    // FG14
    @Test
    void generateForType_boolean_stringTrueIncluded() {
        List<Object> payloads = generator.generateForType("boolean");
        assertThat(payloads).contains("true");
    }

    // FG15
    @Test
    void generateForType_boolean_intOneIncluded() {
        List<Object> payloads = generator.generateForType("boolean");
        assertThat(payloads).contains(1);
    }

    @Test
    void generateForType_boolean_stringFalseIncluded() {
        List<Object> payloads = generator.generateForType("boolean");
        assertThat(payloads).contains("false");
    }

    // ── Number payloads ───────────────────────────────────────────────────────

    @Test
    void generateForType_number_nanIncluded() {
        List<Object> payloads = generator.generateForType("number");
        assertThat(payloads).contains(Double.NaN);
    }

    @Test
    void generateForType_number_infinityIncluded() {
        List<Object> payloads = generator.generateForType("number");
        assertThat(payloads).contains(Double.POSITIVE_INFINITY);
    }

    // ── Unknown type fallback ─────────────────────────────────────────────────

    // FG16
    @Test
    void generateForType_unknownType_stringPayloadsUsed() {
        List<Object> payloads = generator.generateForType("unknown");
        // Falls back to string generation — should include empty string and null
        assertThat(payloads).contains("");
        assertThat(payloads).containsNull();
    }

    // ── Enum constraint ───────────────────────────────────────────────────────

    @Test
    void generateForSchema_enumConstraint_invalidEnumValueIncluded() {
        FieldSchema schema = new FieldSchema("string", "body",
                new FieldConstraints(null, null, null, null, null,
                        List.of("available", "pending", "sold")));
        List<Object> payloads = generator.generateForSchema(schema);
        assertThat(payloads).contains("NOT_A_VALID_ENUM_VALUE");
    }

    // ── Non-nullable constraint ───────────────────────────────────────────────

    @Test
    void generateForSchema_nonNullableField_nullPayloadIncluded() {
        FieldSchema schema = new FieldSchema("string", "body",
                new FieldConstraints(null, null, null, null, false, null));
        List<Object> payloads = generator.generateForSchema(schema);
        assertThat(payloads).containsNull();
    }

    // ── generateAll ───────────────────────────────────────────────────────────

    @Test
    void generateForType_allTypesProduceNonEmptyLists() {
        List.of("string", "integer", "number", "boolean", "array", "object")
                .forEach(type -> assertThat(generator.generateForType(type))
                        .as("Payloads for type: " + type)
                        .isNotEmpty());
    }
}