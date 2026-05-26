package com.chaosmonkey.runner;

import com.chaosmonkey.model.FuzzResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Examines each FuzzResult and attaches flags based on what the
 * response reveals about API behaviour.
 *
 * All seven flag types defined in the system design are implemented here.
 * Flags are string constants — multiple flags can be attached to one result.
 */
@Component
public class ResponseAnalyser {

    private static final Logger log = LoggerFactory.getLogger(ResponseAnalyser.class);

    // Flag constants — match the definitions in the system design document
    public static final String SERVER_ERROR        = "SERVER_ERROR";
    public static final String STACK_TRACE_LEAKED  = "STACK_TRACE_LEAKED";
    public static final String DB_ERROR_EXPOSED    = "DB_ERROR_EXPOSED";
    public static final String UNEXPECTED_SUCCESS  = "UNEXPECTED_SUCCESS";
    public static final String VALIDATION_MISSING  = "VALIDATION_MISSING";
    public static final String EMPTY_SUCCESS_BODY  = "EMPTY_SUCCESS_BODY";

    // Slow response flag is dynamic — includes the actual duration in the flag string
    public static final String SLOW_RESPONSE_PREFIX = "SLOW_RESPONSE_";

    // Default threshold — overridden by FuzzConfig.timeoutMs in v0.2+
    private static final long SLOW_RESPONSE_THRESHOLD_MS = 3_000;

    /**
     * Analyses a single FuzzResult and returns a new FuzzResult
     * with all applicable flags attached.
     *
     * Records are immutable — we build a new flags list and return
     * a new FuzzResult with it attached.
     */
    public FuzzResult analyse(FuzzResult result) {
        List<String> flags = new ArrayList<>();
        String body = result.responseBody() == null ? "" : result.responseBody();
        int status = result.statusCode();

        // ── SERVER_ERROR ─────────────────────────────────────────────────────
        // Any 5xx response indicates the server crashed or errored internally
        if (status >= 500 && status < 600) {
            flags.add(SERVER_ERROR);
        }

        // ── STACK_TRACE_LEAKED ───────────────────────────────────────────────
        // Java stack trace patterns in the response body expose internal details
        if (body.contains("at com.")
                || body.contains("at org.")
                || body.contains("at java.")
                || body.contains("NullPointerException")
                || body.contains("IllegalArgumentException")
                || body.contains("IllegalStateException")
                || body.contains("StackTrace")
                || body.contains("Traceback")) {
            flags.add(STACK_TRACE_LEAKED);
        }

        // ── DB_ERROR_EXPOSED ─────────────────────────────────────────────────
        // Database error messages indicate insufficient error handling
        if (body.contains("SQLException")
                || body.contains("syntax error")
                || body.contains("ORA-")
                || body.contains("ERROR: column")
                || body.contains("SQLSTATE")
                || body.contains("mysql_fetch")
                || body.contains("pg_query")) {
            flags.add(DB_ERROR_EXPOSED);
        }

        // ── UNEXPECTED_SUCCESS ───────────────────────────────────────────────
        // 2xx response to a payload that was intentionally invalid.
        // The payload field on the result tells us what was sent —
        // null sent to any field is always intentionally invalid.
        if (status >= 200 && status < 300 && result.payload() == null) {
            flags.add(UNEXPECTED_SUCCESS);
        }

        // ── VALIDATION_MISSING ───────────────────────────────────────────────
        // 2xx response when a 400 was expected for a clearly out-of-range input.
        // In v0.1 we detect this for the 10,000-char string payload specifically —
        // no real API should accept that without validation.
        if (status >= 200 && status < 300
                && result.payload() instanceof String s
                && s.length() > 1000) {
            flags.add(VALIDATION_MISSING);
        }

        // ── SLOW_RESPONSE ────────────────────────────────────────────────────
        // Response took longer than the threshold — possible ReDoS or missing index
        if (result.durationMs() > SLOW_RESPONSE_THRESHOLD_MS) {
            flags.add(SLOW_RESPONSE_PREFIX + result.durationMs() + "ms");
        }

        // ── EMPTY_SUCCESS_BODY ───────────────────────────────────────────────
        // 2xx with a blank body may indicate silent failure
        if (status >= 200 && status < 300 && body.isBlank()) {
            flags.add(EMPTY_SUCCESS_BODY);
        }

        if (!flags.isEmpty()) {
            log.warn("Flagged — endpoint: {} | field: {} | payload: {} | status: {} | flags: {}",
                    result.endpoint(), result.fieldName(), result.payload(),
                    result.statusCode(), flags);
        }

        return new FuzzResult(
                result.endpoint(),
                result.method(),
                result.fieldName(),
                result.payload(),
                result.statusCode(),
                result.responseBody(),
                result.durationMs(),
                flags
        );
    }

    /**
     * Analyses a list of FuzzResults and returns a new list
     * with all applicable flags attached to each result.
     */
    public List<FuzzResult> analyseAll(List<FuzzResult> results) {
        return results.stream()
                .map(this::analyse)
                .toList();
    }
}