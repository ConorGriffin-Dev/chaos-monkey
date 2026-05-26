package com.chaosmonkey.runner;

import com.chaosmonkey.model.FuzzConfig;
import com.chaosmonkey.model.FuzzResult;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes fuzz payloads against a target API using REST Assured.
 * Records the full response including status code, body, and duration
 * for every request fired.
 *
 * In v0.1, FuzzRunner receives a flat list of payloads and fires each
 * one at the target URL as a POST request with a JSON body.
 * Schema-aware field-level targeting is introduced in v0.2.
 */
@Service
public class FuzzRunner {

    private static final Logger log = LoggerFactory.getLogger(FuzzRunner.class);

    /**
     * Fires each payload in the list at the target URL.
     * Each payload is sent as the value of a "data" key in a JSON body.
     *
     * @param targetUrl  the full URL to fuzz (base URL + path)
     * @param payloads   the list of payloads to fire
     * @param config     runtime config — used for timeout
     * @return           one FuzzResult per payload, flags list empty (filled by ResponseAnalyser)
     */
    public List<FuzzResult> run(String targetUrl, List<Object> payloads, FuzzConfig config) {
        List<FuzzResult> results = new ArrayList<>();

        RestAssured.useRelaxedHTTPSValidation();

        log.info("FuzzRunner starting — target: {} | payloads: {}", targetUrl, payloads.size());

        for (Object payload : payloads) {
            FuzzResult result = fireRequest(targetUrl, payload, config);
            results.add(result);
        }

        log.info("FuzzRunner complete — {} requests fired", results.size());
        return results;
    }

    /**
     * Fires a single request and captures the response as a FuzzResult.
     * Duration is measured as wall-clock time around the REST Assured call.
     */
    private FuzzResult fireRequest(String targetUrl, Object payload, FuzzConfig config) {
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

            log.debug("POST {} | payload: {} | status: {} | {}ms",
                    targetUrl, payload, response.statusCode(), duration);

            return new FuzzResult(
                    targetUrl,
                    "POST",
                    "data",
                    payload,
                    response.statusCode(),
                    response.body().asString(),
                    duration,
                    new ArrayList<>()
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Request failed — target: {} | payload: {} | error: {}",
                    targetUrl, payload, e.getMessage());

            // Record the failure as a result rather than crashing the run
            return new FuzzResult(
                    targetUrl,
                    "POST",
                    "data",
                    payload,
                    0,
                    "REQUEST_FAILED: " + e.getMessage(),
                    duration,
                    new ArrayList<>()
            );
        }
    }
}