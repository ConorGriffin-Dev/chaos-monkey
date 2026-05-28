package com.chaosmonkey.runner;

import com.chaosmonkey.model.FuzzResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.chaosmonkey.runner.ResponseAnalyser.*;
import static org.assertj.core.api.Assertions.assertThat;

class ResponseAnalyserTest {

    private ResponseAnalyser analyser;

    @BeforeEach
    void setUp() {
        analyser = new ResponseAnalyser();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private FuzzResult result(int status, String body, Object payload, long durationMs) {
        return new FuzzResult(
                "https://api.example.com/test",
                "POST",
                "data",
                payload,
                status,
                body,
                durationMs,
                new ArrayList<>()
        );
    }

    // ── SERVER_ERROR ──────────────────────────────────────────────────────────

    // RA01
    @Test
    void analyse_500Response_serverErrorFlagAttached() {
        FuzzResult result = analyser.analyse(result(500, "Internal Server Error", "payload", 100));
        assertThat(result.flags()).contains(SERVER_ERROR);
    }

    // RA02
    @Test
    void analyse_502Response_serverErrorFlagAttached() {
        FuzzResult result = analyser.analyse(result(502, "Bad Gateway", "payload", 100));
        assertThat(result.flags()).contains(SERVER_ERROR);
    }

    // RA03
    @Test
    void analyse_200Response_cleanInput_noFlagsAttached() {
        FuzzResult result = analyser.analyse(result(200, "{\"id\": 1}", "normal input", 100));
        assertThat(result.flags()).isEmpty();
    }

    // ── STACK_TRACE_LEAKED ────────────────────────────────────────────────────

    // RA04
    @Test
    void analyse_bodyContainsNullPointerException_stackTraceFlagAttached() {
        String body = "java.lang.NullPointerException at com.example.Service.method(Service.java:42)";
        FuzzResult result = analyser.analyse(result(500, body, "payload", 100));
        assertThat(result.flags()).contains(STACK_TRACE_LEAKED);
    }

    // RA05
    @Test
    void analyse_bodyContainsAtCom_stackTraceFlagAttached() {
        String body = "Error: at com.example.api.Controller.handle(Controller.java:88)";
        FuzzResult result = analyser.analyse(result(200, body, "payload", 100));
        assertThat(result.flags()).contains(STACK_TRACE_LEAKED);
    }

    // ── DB_ERROR_EXPOSED ──────────────────────────────────────────────────────

    // RA06
    @Test
    void analyse_bodyContainsSQLException_dbErrorFlagAttached() {
        String body = "SQLException: syntax error near unexpected token";
        FuzzResult result = analyser.analyse(result(500, body, "payload", 100));
        assertThat(result.flags()).contains(DB_ERROR_EXPOSED);
    }

    // RA07
    @Test
    void analyse_bodyContainsOraError_dbErrorFlagAttached() {
        String body = "ORA-00942: table or view does not exist";
        FuzzResult result = analyser.analyse(result(500, body, "payload", 100));
        assertThat(result.flags()).contains(DB_ERROR_EXPOSED);
    }

    // ── UNEXPECTED_SUCCESS ────────────────────────────────────────────────────

    // RA08
    @Test
    void analyse_200OnNullPayload_unexpectedSuccessFlagAttached() {
        FuzzResult result = analyser.analyse(result(200, "{\"id\": 1}", null, 100));
        assertThat(result.flags()).contains(UNEXPECTED_SUCCESS);
    }

    // RA09 — null payload sent to an integer field also triggers UNEXPECTED_SUCCESS
    @Test
    void analyse_201OnNullPayload_unexpectedSuccessFlagAttached() {
        FuzzResult result = analyser.analyse(result(201, "{\"id\": 1}", null, 100));
        assertThat(result.flags()).contains(UNEXPECTED_SUCCESS);
    }

    // ── SLOW_RESPONSE ─────────────────────────────────────────────────────────

    // RA10
    @Test
    void analyse_responseTakes4000ms_slowResponseFlagAttached() {
        FuzzResult result = analyser.analyse(result(200, "{\"id\": 1}", "payload", 4000));
        assertThat(result.flags())
                .anyMatch(flag -> flag.startsWith(SLOW_RESPONSE_PREFIX));
    }

    // RA11
    @Test
    void analyse_responseTakes2000ms_noSlowFlag() {
        FuzzResult result = analyser.analyse(result(200, "{\"id\": 1}", "payload", 2000));
        assertThat(result.flags())
                .noneMatch(flag -> flag.startsWith(SLOW_RESPONSE_PREFIX));
    }

    // ── EMPTY_SUCCESS_BODY ────────────────────────────────────────────────────

    // RA12
    @Test
    void analyse_200WithEmptyBody_emptySuccessFlagAttached() {
        FuzzResult result = analyser.analyse(result(200, "", "payload", 100));
        assertThat(result.flags()).contains(EMPTY_SUCCESS_BODY);
    }

    // ── VALIDATION_MISSING ────────────────────────────────────────────────────

    // RA13
    @Test
    void analyse_400OnInvalidInput_noValidationMissingFlag() {
        FuzzResult result = analyser.analyse(result(400, "Bad Request", "bad input", 100));
        assertThat(result.flags()).doesNotContain(VALIDATION_MISSING);
    }

    // RA14
    @Test
    void analyse_200OnHugeString_validationMissingFlagAttached() {
        String hugeString = "A".repeat(10_000);
        FuzzResult result = analyser.analyse(result(200, "{\"id\": 1}", hugeString, 100));
        assertThat(result.flags()).contains(VALIDATION_MISSING);
    }

    // ── MULTIPLE FLAGS ────────────────────────────────────────────────────────

    // RA15
    @Test
    void analyse_500WithStackTrace_bothFlagsAttached() {
        String body = "NullPointerException at com.example.Service.method(Service.java:10)";
        FuzzResult result = analyser.analyse(result(500, body, "payload", 100));
        assertThat(result.flags())
                .contains(SERVER_ERROR)
                .contains(STACK_TRACE_LEAKED);
    }

    // ── analyseAll ────────────────────────────────────────────────────────────

    @Test
    void analyseAll_multipleResults_allAnalysed() {
        List<FuzzResult> results = List.of(
                result(500, "error", "payload", 100),
                result(200, "{\"id\": 1}", "normal", 100),
                result(200, "", null, 100)
        );

        List<FuzzResult> analysed = analyser.analyseAll(results);

        assertThat(analysed).hasSize(3);
        assertThat(analysed.get(0).flags()).contains(SERVER_ERROR);
        assertThat(analysed.get(1).flags()).isEmpty();
        assertThat(analysed.get(2).flags()).contains(UNEXPECTED_SUCCESS, EMPTY_SUCCESS_BODY);
    }
}