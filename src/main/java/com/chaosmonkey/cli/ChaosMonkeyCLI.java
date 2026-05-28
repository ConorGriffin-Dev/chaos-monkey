package com.chaosmonkey.cli;

import com.chaosmonkey.fuzzer.FuzzPayloadGenerator;
import com.chaosmonkey.fuzzer.FuzzPayloadLibrary;
import com.chaosmonkey.model.FuzzCase;
import com.chaosmonkey.model.FuzzConfig;
import com.chaosmonkey.model.FuzzResult;
import com.chaosmonkey.model.FuzzTarget;
import com.chaosmonkey.parser.OpenApiParser;
import com.chaosmonkey.runner.FuzzRunner;
import com.chaosmonkey.runner.ResponseAnalyser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

/**
 * Entry point for Chaos Monkey CLI commands.
 *
 * v0.1: single code path — static payload list fired at one URL.
 *
 * v0.2: two code paths controlled by --spec:
 *   With --spec:    OpenApiParser → FuzzPayloadGenerator → FuzzRunner.runCases()
 *   Without --spec: FuzzPayloadLibrary → FuzzRunner.run() (v0.1 static fallback)
 */
@ShellComponent
public class ChaosMonkeyCLI {

    private static final Logger log = LoggerFactory.getLogger(ChaosMonkeyCLI.class);

    private final FuzzPayloadLibrary payloadLibrary;
    private final FuzzPayloadGenerator payloadGenerator;
    private final OpenApiParser openApiParser;
    private final FuzzRunner fuzzRunner;
    private final ResponseAnalyser responseAnalyser;

    public ChaosMonkeyCLI(FuzzPayloadLibrary payloadLibrary,
                          FuzzPayloadGenerator payloadGenerator,
                          OpenApiParser openApiParser,
                          FuzzRunner fuzzRunner,
                          ResponseAnalyser responseAnalyser) {
        this.payloadLibrary = payloadLibrary;
        this.payloadGenerator = payloadGenerator;
        this.openApiParser = openApiParser;
        this.fuzzRunner = fuzzRunner;
        this.responseAnalyser = responseAnalyser;
    }

    @ShellMethod(value = "Fuzz a target API using OpenAPI spec or static payload list", key = "fuzz")
    public void fuzz(
            @ShellOption(value = "--url",
                    help = "Base URL of the target API")
            String url,

            @ShellOption(value = "--spec",
                    help = "Path or URL to the OpenAPI 3.x spec. If omitted, falls back to static fuzz list",
                    defaultValue = ShellOption.NULL)
            String spec,

            @ShellOption(value = "--timeout",
                    help = "Request timeout in milliseconds",
                    defaultValue = "10000")
            int timeoutMs
    ) {
        FuzzConfig config = new FuzzConfig(url, spec, FuzzConfig.DEFAULT_OUTPUT_DIR, timeoutMs);

        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Chaos Monkey — v0.2 Schema-Aware Fuzzer");
        System.out.println("  Target : " + url);
        System.out.println("  Spec   : " + (spec != null ? spec : "none — static mode"));
        System.out.println("  Timeout: " + timeoutMs + "ms");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        List<FuzzResult> analysed;

        if (spec != null) {
            analysed = runSpecMode(url, spec, config);
        } else {
            analysed = runStaticMode(url, config);
        }

        printSummary(analysed);
    }

    // ── Spec-aware mode ───────────────────────────────────────────────────────

    private List<FuzzResult> runSpecMode(String url, String spec, FuzzConfig config) {
        System.out.println("  Mode: schema-aware — parsing spec...");

        // Parse spec → FuzzTargets
        List<FuzzTarget> targets = openApiParser.parse(spec);
        System.out.println("  Endpoints discovered: " + targets.size());

        // Generate FuzzCases from FuzzTargets
        List<FuzzCase> cases = payloadGenerator.generateAll(targets);
        System.out.println("  Fuzz cases generated: " + cases.size());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Fire all cases
        List<FuzzResult> raw = fuzzRunner.runCases(cases, url, config);

        // Analyse and return
        return responseAnalyser.analyseAll(raw);
    }

    // ── Static fallback mode (v0.1) ───────────────────────────────────────────

    private List<FuzzResult> runStaticMode(String url, FuzzConfig config) {
        System.out.println("  Mode: static — no spec provided");

        List<Object> payloads = payloadLibrary.getAll();
        System.out.println("  Payloads loaded: " + payloads.size());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        List<FuzzResult> raw = fuzzRunner.run(url, payloads, config);
        return responseAnalyser.analyseAll(raw);
    }

    // ── Output ────────────────────────────────────────────────────────────────

    private void printSummary(List<FuzzResult> results) {
        long flaggedCount = results.stream()
                .filter(r -> !r.flags().isEmpty())
                .count();

        System.out.println("\n  ── Flagged Results ──────────────────────────────");

        results.stream()
                .filter(r -> !r.flags().isEmpty())
                .forEach(r -> {
                    System.out.println();
                    System.out.println("  Endpoint : " + r.endpoint());
                    System.out.println("  Method   : " + r.method());
                    System.out.println("  Field    : " + r.fieldName());
                    System.out.println("  Payload  : " + truncate(String.valueOf(r.payload()), 120));
                    System.out.println("  Status   : " + r.statusCode());
                    System.out.println("  Duration : " + r.durationMs() + "ms");
                    System.out.println("  Flags    : " + r.flags());
                    System.out.println("  Body     : " + truncate(r.responseBody(), 200));
                    System.out.println("  ─────────────────────────────────────────────");
                });

        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Total requests : " + results.size());
        System.out.println("  Flagged        : " + flaggedCount);
        System.out.println("  Clean          : " + (results.size() - flaggedCount));
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private String truncate(String s, int maxLength) {
        if (s == null) return "null";
        return s.length() <= maxLength ? s : s.substring(0, maxLength) + "...";
    }
}