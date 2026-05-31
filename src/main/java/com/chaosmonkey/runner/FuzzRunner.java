package com.chaosmonkey.runner;

import com.chaosmonkey.model.FuzzCase;
import com.chaosmonkey.model.FuzzConfig;
import com.chaosmonkey.model.FuzzResult;
import com.chaosmonkey.model.FieldSchema;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes fuzz cases against a target API using REST Assured.
 * Records the full response including status code, body, and duration
 * for every request fired.
 *
 * v0.1: accepted a flat list of payloads, fired everything as POST
 *       with a generic {"data": payload} wrapper.
 *
 * v0.2: accepts a list of FuzzCases. Each case carries the target
 *       endpoint, HTTP method, field name, and payload.
 *
 * v0.3: requests are built per the field's declared location:
 *       - path   → payload substituted into the {param} segment
 *       - header → payload sent as an HTTP header
 *       - query  → payload sent as a query parameter
 *       - body   → payload sent in the JSON body
 *       Non-fuzzed path params get a type-aware placeholder (1 for
 *       integer/number, "test" otherwise) so the URL resolves.
 *
 * Also retains the v0.1 static mode signature for backwards
 * compatibility with ChaosMonkeyCLI's --spec-less fallback.
 */
@Service
public class FuzzRunner {

    private static final Logger log = LoggerFactory.getLogger(FuzzRunner.class);

    private static final Pattern PATH_PARAM = Pattern.compile("\\{([^}]+)\\}");

    // ── v0.2+ — schema-aware mode ─────────────────────────────────────────────

    public List<FuzzResult> runCases(List<FuzzCase> cases, String baseUrl, FuzzConfig config) {
        List<FuzzResult> results = new ArrayList<>();

        RestAssured.useRelaxedHTTPSValidation();

        log.info("FuzzRunner starting — {} cases to fire against {}",
                cases.size(), baseUrl);

        for (FuzzCase fuzzCase : cases) {
            FuzzResult result = fireCase(fuzzCase, baseUrl, config);
            results.add(result);
        }

        log.info("FuzzRunner complete — {} requests fired", results.size());
        return results;
    }

    /**
     * Fires a single FuzzCase and captures the response as a FuzzResult.
     * Routes the fuzzed field to path / header / query / body based on
     * its declared location, resolving any other {pathParam} segments.
     */
    private FuzzResult fireCase(FuzzCase fuzzCase, String baseUrl, FuzzConfig config) {
        String rawPath = fuzzCase.target().path();
        String method = fuzzCase.target().method();
        String fieldName = fuzzCase.fieldName();
        Object payload = fuzzCase.payload();

        // Where does the fuzzed field live? path / query / header / body.
        String fieldIn = locationOf(fuzzCase, fieldName, rawPath);
        boolean fieldIsPathParam = "path".equals(fieldIn);

        String resolvedPath = resolvePath(rawPath, fuzzCase, fieldName, payload);
        String fullUrl = baseUrl + resolvedPath;

        long start = System.currentTimeMillis();

        try {
            RequestSpecification request = RestAssured
                    .given()
                    .baseUri(fullUrl)
                    .contentType("application/json")
                    .config(RestAssured.config()
                            .httpClient(io.restassured.config.HttpClientConfig
                                    .httpClientConfig()
                                    .setParam("http.connection.timeout", config.timeoutMs())
                                    .setParam("http.socket.timeout", config.timeoutMs())));

            // Route the fuzzed field to the correct place, unless it already went into the path.
            if (!fieldIsPathParam) {
                switch (fieldIn) {
                    case "header" ->
                            request.header(fieldName, payload == null ? "" : payload.toString());
                    case "query" ->
                            request.queryParam(fieldName, payload == null ? "" : payload.toString());
                    default -> { // "body" — and anything unrecognised
                        if (method.equals("GET") || method.equals("DELETE")) {
                            request.queryParam(fieldName, payload == null ? "" : payload.toString());
                        } else {
                            request.body(Map.of(fieldName, payload == null ? "" : payload));
                        }
                    }
                }
            }

            Response response = request
                    .when()
                    .request(method, "")
                    .then()
                    .extract()
                    .response();

            long duration = System.currentTimeMillis() - start;

            log.debug("{} {} | field: {} ({}) | payload: {} | status: {} | {}ms",
                    method, fullUrl, fieldName, fieldIn, payload,
                    response.statusCode(), duration);

            return new FuzzResult(
                    fullUrl,
                    method,
                    fieldName,
                    payload,
                    response.statusCode(),
                    response.body().asString(),
                    duration,
                    new ArrayList<>()
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Request failed — {} {} | field: {} | payload: {} | error: {}",
                    method, fullUrl, fieldName, payload, e.getMessage());

            return new FuzzResult(
                    fullUrl,
                    method,
                    fieldName,
                    payload,
                    0,
                    "REQUEST_FAILED: " + e.getMessage(),
                    duration,
                    new ArrayList<>()
            );
        }
    }

    /**
     * Resolves where a fuzzed field lives: path / query / header / body.
     * Prefers the schema's declared location; falls back to detecting a
     * path placeholder, then to "body".
     */
    private String locationOf(FuzzCase fuzzCase, String fieldName, String rawPath) {
        Map<String, FieldSchema> fields = fuzzCase.target().fields();
        FieldSchema schema = fields == null ? null : fields.get(fieldName);
        if (schema != null && schema.in() != null) {
            return schema.in();
        }
        if (rawPath.contains("{" + fieldName + "}")) {
            return "path";
        }
        return "body";
    }

    /**
     * Replaces every {pathParam} segment in the path.
     * - If the segment matches the fuzzed field, the fuzz payload is substituted.
     * - Otherwise a type-aware placeholder is used (1 for integer/number, "test"
     *   otherwise) so the URL resolves and the actual fuzz target is exercised.
     */
    private String resolvePath(String rawPath, FuzzCase fuzzCase, String fuzzedField, Object payload) {
        Matcher m = PATH_PARAM.matcher(rawPath);
        StringBuilder out = new StringBuilder();

        while (m.find()) {
            String paramName = m.group(1);
            String replacement;

            if (paramName.equals(fuzzedField)) {
                replacement = payload == null ? "" : payload.toString();
            } else {
                replacement = placeholderFor(paramName, fuzzCase);
            }

            m.appendReplacement(out, Matcher.quoteReplacement(urlEncode(replacement)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Type-aware placeholder for a non-fuzzed path param: "1" for integer/number
     * declared types, "test" otherwise. Falls back to "1" if the type is unknown.
     */
    private String placeholderFor(String paramName, FuzzCase fuzzCase) {
        Map<String, FieldSchema> fields = fuzzCase.target().fields();
        FieldSchema schema = fields == null ? null : fields.get(paramName);
        if (schema != null && schema.type() != null) {
            String type = schema.type();
            if ("integer".equals(type) || "number".equals(type)) {
                return "1";
            }
            return "test";
        }
        return "1";
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ── v0.1 — static mode (retained for --spec-less fallback) ───────────────

    public List<FuzzResult> run(String targetUrl, List<Object> payloads, FuzzConfig config) {
        List<FuzzResult> results = new ArrayList<>();

        RestAssured.useRelaxedHTTPSValidation();

        log.info("FuzzRunner (static mode) starting — target: {} | payloads: {}",
                targetUrl, payloads.size());

        for (Object payload : payloads) {
            long start = System.currentTimeMillis();

            try {
                Response response = RestAssured
                        .given()
                        .baseUri(targetUrl)
                        .contentType("application/json")
                        .config(RestAssured.config()
                                .httpClient(io.restassured.config.HttpClientConfig
                                        .httpClientConfig()
                                        .setParam("http.connection.timeout", config.timeoutMs())
                                        .setParam("http.socket.timeout", config.timeoutMs())))
                        .body(Map.of("data", payload == null ? "" : payload))
                        .when()
                        .post()
                        .then()
                        .extract()
                        .response();

                long duration = System.currentTimeMillis() - start;

                results.add(new FuzzResult(
                        targetUrl, "POST", "data", payload,
                        response.statusCode(), response.body().asString(),
                        duration, new ArrayList<>()));

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                log.error("Request failed — target: {} | payload: {} | error: {}",
                        targetUrl, payload, e.getMessage());

                results.add(new FuzzResult(
                        targetUrl, "POST", "data", payload,
                        0, "REQUEST_FAILED: " + e.getMessage(),
                        duration, new ArrayList<>()));
            }
        }

        log.info("FuzzRunner (static mode) complete — {} requests fired", results.size());
        return results;
    }
}