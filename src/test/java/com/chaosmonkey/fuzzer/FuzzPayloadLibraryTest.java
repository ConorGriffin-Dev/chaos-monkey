package com.chaosmonkey.fuzzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzPayloadLibraryTest {

    private FuzzPayloadLibrary library;

    @BeforeEach
    void setUp() {
        library = new FuzzPayloadLibrary();
        library.load();
    }

    // ── Load verification ─────────────────────────────────────────────────────

    @Test
    void load_jsonFile_stringsNotEmpty() {
        assertThat(library.getStrings()).isNotEmpty();
    }

    @Test
    void load_jsonFile_integersNotEmpty() {
        assertThat(library.getIntegers()).isNotEmpty();
    }

    @Test
    void load_jsonFile_booleansNotEmpty() {
        assertThat(library.getBooleans()).isNotEmpty();
    }

    // ── String payload content ────────────────────────────────────────────────

    @Test
    void getStrings_containsEmptyString() {
        assertThat(library.getStrings()).contains("");
    }

    @Test
    void getStrings_containsWhitespaceOnlyString() {
        assertThat(library.getStrings()).contains(" ");
    }

    @Test
    void getStrings_containsSqlInjectionPayload() {
        assertThat(library.getStrings())
                .anyMatch(p -> p instanceof String s && s.contains("DROP TABLE"));
    }

    @Test
    void getStrings_containsXssPayload() {
        assertThat(library.getStrings())
                .anyMatch(p -> p instanceof String s && s.contains("<script>"));
    }

    @Test
    void getStrings_containsPathTraversalPayload() {
        assertThat(library.getStrings())
                .anyMatch(p -> p instanceof String s && s.contains("../"));
    }

    @Test
    void getStrings_containsLargeString() {
        assertThat(library.getStrings())
                .anyMatch(p -> p instanceof String s && s.length() > 1000);
    }

    // ── Integer payload content ───────────────────────────────────────────────

    @Test
    void getIntegers_containsZero() {
        assertThat(library.getIntegers()).contains(0L);
    }

    @Test
    void getIntegers_containsNegativeOne() {
        assertThat(library.getIntegers()).contains(-1L);
    }

    @Test
    void getIntegers_containsOverflowValue() {
        assertThat(library.getIntegers())
                .anyMatch(p -> p instanceof Long l && l > Integer.MAX_VALUE);
    }

    // ── Boolean payload content ───────────────────────────────────────────────

    @Test
    void getBooleans_containsNull() {
        assertThat(library.getBooleans()).containsNull();
    }

    @Test
    void getBooleans_containsStringTrue() {
        assertThat(library.getBooleans()).contains("true");
    }

    @Test
    void getBooleans_containsStringFalse() {
        assertThat(library.getBooleans()).contains("false");
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsCombinedList() {
        List<Object> all = library.getAll();
        int expected = library.getStrings().size()
                + library.getIntegers().size()
                + library.getBooleans().size();
        assertThat(all).hasSize(expected);
    }

    @Test
    void getAll_containsPayloadsFromAllCategories() {
        List<Object> all = library.getAll();
        assertThat(all).contains("");        // from strings
        assertThat(all).contains(0L);        // from integers
        assertThat(all).contains("true");    // from booleans
    }
}