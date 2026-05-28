package com.chaosmonkey.runner;

import com.chaosmonkey.model.FuzzConfig;
import com.chaosmonkey.model.FuzzResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzRunnerTest {

    private FuzzRunner fuzzRunner;
    private FuzzConfig config;

    @BeforeEach
    void setUp() {
        fuzzRunner = new FuzzRunner();
        config = new FuzzConfig(
                "https://jsonplaceholder.typicode.com/posts",
                null,
                FuzzConfig.DEFAULT_OUTPUT_DIR,
                FuzzConfig.DEFAULT_TIMEOUT_MS
        );
    }

    // ── Result count ──────────────────────────────────────────────────────────

    // FR05 — one result returned per payload
    @Test
    void run_multipleFuzzCases_allResultsReturned() {
        List<Object> payloads = List.of("payload1", "payload2", "payload3");

        List<FuzzResult> results = fuzzRunner.run(
                "https://jsonplaceholder.typicode.com/posts", payloads, config);

        assertThat(results).hasSize(3);
    }

    // ── Result fields populated ───────────────────────────────────────────────

    // FR02 — status code captured
    @Test
    void run_singlePayload_statusCodeCaptured() {
        List<FuzzResult> results = fuzzRunner.run(
                "https://jsonplaceholder.typicode.com/posts",
                List.of("test payload"),
                config);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).statusCode()).isGreaterThan(0);
    }

    // FR03 — response body captured
    @Test
    void run_singlePayload_responseBodyCaptured() {
        List<FuzzResult> results = fuzzRunner.run(
                "https://jsonplaceholder.typicode.com/posts",
                List.of("test payload"),
                config);

        assertThat(results.get(0).responseBody()).isNotNull();
    }

    // FR04 — duration captured and positive
    @Test
    void run_singlePayload_durationCaptured() {
        List<FuzzResult> results = fuzzRunner.run(
                "https://jsonplaceholder.typicode.com/posts",
                List.of("test payload"),
                config);

        assertThat(results.get(0).durationMs()).isGreaterThan(0);
    }

    // ── Endpoint and method recorded correctly ────────────────────────────────

    @Test
    void run_singlePayload_endpointRecordedCorrectly() {
        String targetUrl = "https://jsonplaceholder.typicode.com/posts";
        List<FuzzResult> results = fuzzRunner.run(
                targetUrl, List.of("test"), config);

        assertThat(results.get(0).endpoint()).isEqualTo(targetUrl);
    }

    @Test
    void run_singlePayload_methodIsPost() {
        List<FuzzResult> results = fuzzRunner.run(
                "https://jsonplaceholder.typicode.com/posts",
                List.of("test"), config);

        assertThat(results.get(0).method()).isEqualTo("POST");
    }

    // ── Payload recorded correctly ────────────────────────────────────────────

    @Test
    void run_singlePayload_payloadRecordedOnResult() {
        List<FuzzResult> results = fuzzRunner.run(
                "https://jsonplaceholder.typicode.com/posts",
                List.of("my-test-payload"),
                config);

        assertThat(results.get(0).payload()).isEqualTo("my-test-payload");
    }

    // ── Flags list starts empty ───────────────────────────────────────────────

    // FuzzRunner produces raw results — flags are added by ResponseAnalyser
    @Test
    void run_singlePayload_flagsListEmptyBeforeAnalysis() {
        List<FuzzResult> results = fuzzRunner.run(
                "https://jsonplaceholder.typicode.com/posts",
                List.of("test"),
                config);

        assertThat(results.get(0).flags()).isEmpty();
    }

    // ── Error handling ────────────────────────────────────────────────────────

    // FR06 — bad URL produces a result rather than throwing an exception
    @Test
    void run_unreachableUrl_resultRecordedWithStatusZero() {
        List<FuzzResult> results = fuzzRunner.run(
                "https://this-url-does-not-exist-chaos-monkey.invalid/posts",
                List.of("test"),
                config);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).statusCode()).isEqualTo(0);
        assertThat(results.get(0).responseBody()).startsWith("REQUEST_FAILED");
    }

    // ── Empty payload list ────────────────────────────────────────────────────

    @Test
    void run_emptyPayloadList_returnsEmptyResults() {
        List<FuzzResult> results = fuzzRunner.run(
                "https://jsonplaceholder.typicode.com/posts",
                List.of(),
                config);

        assertThat(results).isEmpty();
    }
}