package com.chaosmonkey.runner;

import com.chaosmonkey.model.FuzzCase;
import com.chaosmonkey.model.FuzzConfig;
import com.chaosmonkey.model.FuzzResult;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes fuzz cases against a target API using REST Assured.
 * Records the full response including status code, body, and duration
 * for every request fired.
 *
 * v0.1: accepted a flat list of payloads, fired everything as POST
 *       with a generic {"data": payload} wrapper.
 *
 * v0.2: accepts a list of FuzzCases. Each case carries the target
 *       endpoint, HTTP method, field name, and payload. Requests are
 *       built correctly per method — GET/DELETE use query params,
 *       POST/PUT/PATCH use a JSON body with the actual field name.
 *
 * Also retains the v0.1 static mode signature for backwards
 * compatibility with ChaosMonkeyCLI's --spec-less fallback.
 */
@Service
public class FuzzRunner {

    private static final Logger log = LoggerFactory.getLogger(FuzzRunner.class);

    // ── v0.2 — schema-aware mode ──────────────────────────────────────────────

    /**
     * Executes a list of FuzzCases against the target API.
     * Each FuzzCase is fired as the correct HTTP method with the
     * payload targeting the correct field.
     *
     * @param cases   list of FuzzCases produced by FuzzPayloadGenerator
     * @param baseUrl base URL of the target API
     * @param config  runtime config — used for timeout
     * @return        one FuzzResult per FuzzCase, flags empty (filled by ResponseAnalyser)
     */
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
     * Builds the request correctly based on the HTTP method declared
     * in the FuzzTarget.
     */
    private FuzzResult fireCase(FuzzCase fuzzCase, String baseUrl, FuzzConfig config) {
        String fullUrl = baseUrl + fuzzCase.target().path();
        String method = fuzzCase.target().method();
        String fieldName = fuzzCase.fieldName();
        Object payload = fuzzCase.payload();

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

            Response response = switch (method) {
                case "GET", "DELETE" ->
                        request
                                .queryParam(fieldName, payload == null ? "" : payload.toString())
                                .when()
                                .request(method, "")
                                .then()
                                .extract()
                                .response();

                default -> // POST, PUT, PATCH
                        request
                                .body(Map.of(fieldName, payload == null ? "" : payload))
                                .when()
                                .request(method, "")
                                .then()
                                .extract()
                                .response();
            };

            long duration = System.currentTimeMillis() - start;

            log.debug("{} {} | field: {} | payload: {} | status: {} | {}ms",
                    method, fullUrl, fieldName, payload,
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

    // ── v0.1 — static mode (retained for --spec-less fallback) ───────────────

    /**
     * v0.1 static fuzz mode — fires a flat list of payloads at a single
     * URL as POST requests with a generic {"data": payload} wrapper.
     * Retained for use when no --spec argument is provided.
     */
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