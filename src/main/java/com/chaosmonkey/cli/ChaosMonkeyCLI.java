package com.chaosmonkey.cli;

import com.chaosmonkey.fuzzer.FuzzPayloadGenerator;
import com.chaosmonkey.fuzzer.FuzzPayloadLibrary;
import com.chaosmonkey.model.FuzzCase;
import com.chaosmonkey.model.FuzzConfig;
import com.chaosmonkey.model.FuzzResult;
import com.chaosmonkey.model.FuzzTarget;
import com.chaosmonkey.parser.OpenApiParser;
import com.chaosmonkey.reporter.AllureReporter;
import com.chaosmonkey.runner.FuzzRunner;
import com.chaosmonkey.runner.ResponseAnalyser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Entry point for Chaos Monkey CLI commands.
 *
 * v0.1: single code path — static payload list fired at one URL.
 *
 * v0.2: two code paths controlled by --spec:
 *   With --spec:    OpenApiParser → FuzzPayloadGenerator → FuzzRunner.runCases()
 *   Without --spec: FuzzPayloadLibrary → FuzzRunner.run() (v0.1 static fallback)
 *
 * v0.3: AllureReporter wired in. New CLI args: --output, --flag-only, --dry-run.
 */
@ShellComponent
public class ChaosMonkeyCLI {

    private static final Logger log = LoggerFactory.getLogger(ChaosMonkeyCLI.class);

    private final FuzzPayloadLibrary fuzzPayloadLibrary;
    private final FuzzPayloadGenerator fuzzPayloadGenerator;
    private final OpenApiParser openApiParser;
    private final FuzzRunner fuzzRunner;
    private final ResponseAnalyser responseAnalyser;
    private final AllureReporter allureReporter;

    public ChaosMonkeyCLI(FuzzPayloadLibrary fuzzPayloadLibrary,
                          FuzzPayloadGenerator fuzzPayloadGenerator,
                          OpenApiParser openApiParser,
                          FuzzRunner fuzzRunner,
                          ResponseAnalyser responseAnalyser,
                          AllureReporter allureReporter) {
        this.fuzzPayloadLibrary = fuzzPayloadLibrary;
        this.fuzzPayloadGenerator = fuzzPayloadGenerator;
        this.openApiParser = openApiParser;
        this.fuzzRunner = fuzzRunner;
        this.responseAnalyser = responseAnalyser;
        this.allureReporter = allureReporter;
    }

    @ShellMethod(key = "fuzz", value = "Run Chaos Monkey against a target API")
    public void fuzz(
            @ShellOption(value = "--url",
                    help = "Base URL of the target API")
            String url,

            @ShellOption(value = "--spec",
                    defaultValue = ShellOption.NULL,
                    help = "Path or URL to the OpenAPI 3.x spec. If omitted, falls back to static fuzz list")
            String spec,

            @ShellOption(value = "--output",
                    defaultValue = "./allure-results",
                    help = "Output directory for Allure result files")
            String output,

            @ShellOption(value = "--timeout",
                    defaultValue = "10000",
                    help = "Request timeout in milliseconds")
            int timeout,

            @ShellOption(value = "--flag-only",
                    defaultValue = "false",
                    help = "Write only flagged results to Allure — reduces report noise")
            boolean flagOnly,

            @ShellOption(value = "--dry-run",
                    defaultValue = "false",
                    help = "Parse spec and generate payloads but do not fire requests")
            boolean dryRun
    ) {
        FuzzConfig config = new FuzzConfig(url, spec, output, timeout, flagOnly, dryRun);

        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Chaos Monkey — v0.3");
        System.out.println("  Target : " + url);
        System.out.println("  Spec   : " + (spec != null ? spec : "none — static mode"));
        System.out.println("  Output : " + output);
        System.out.println("  Timeout: " + timeout + "ms");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        List<FuzzResult> results;

        if (spec != null) {
            // v0.2+ — schema-aware mode
            System.out.println("  Mode: schema-aware — parsing spec...");

            List<FuzzTarget> targets = openApiParser.parse(spec);
            System.out.println("  Endpoints discovered: " + targets.size());

            List<FuzzCase> cases = fuzzPayloadGenerator.generateAll(targets);
            System.out.println("  Fuzz cases generated: " + cases.size());
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            if (dryRun) {
                System.out.println("  [DRY RUN] " + cases.size()
                        + " fuzz cases generated across "
                        + targets.size() + " endpoints — no requests fired.");
                return;
            }

            results = fuzzRunner.runCases(cases, url, config);

        } else {
            // v0.1 fallback — static payload mode
            System.out.println("  Mode: static — no spec provided");

            List<Object> payloads = fuzzPayloadLibrary.getAll();
            System.out.println("  Payloads loaded: " + payloads.size());
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            if (dryRun) {
                System.out.println("  [DRY RUN] Static mode — "
                        + payloads.size() + " payloads loaded — no requests fired.");
                return;
            }

            results = fuzzRunner.run(url, payloads, config);
        }

        results = responseAnalyser.analyseAll(results);
        allureReporter.write(results, output, flagOnly);
        printSummary(results, output);
    }

    // ── Output ────────────────────────────────────────────────────────────────

    private void printSummary(List<FuzzResult> results, String outputDir) {
        long flagged = results.stream().filter(r -> !r.flags().isEmpty()).count();
        long clean = results.size() - flagged;

        Map<String, Long> byFlag = results.stream()
                .flatMap(r -> r.flags().stream())
                .collect(Collectors.groupingBy(f -> f, Collectors.counting()));

        System.out.println("\n  ── Flagged Results ──────────────────────────────");

        results.stream()
                .filter(r -> !r.flags().isEmpty())
                .limit(20)
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

        if (flagged > 20) {
            System.out.println("  ... and " + (flagged - 20)
                    + " more flagged results in the Allure report");
        }

        System.out.println("\n  ══════════════════════════════════════════");
        System.out.println("    Chaos Monkey — Run Complete");
        System.out.println("  ══════════════════════════════════════════");
        System.out.printf("    Total requests   : %d%n", results.size());
        System.out.printf("    Flagged          : %d%n", flagged);
        System.out.printf("    Clean            : %d%n", clean);
        if (!byFlag.isEmpty()) {
            System.out.println("  ──────────────────────────────────────────");
            byFlag.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> System.out.printf("    %-32s : %d%n",
                            e.getKey(), e.getValue()));
        }
        System.out.println("  ──────────────────────────────────────────");
        System.out.println("    Output : " + outputDir);
        System.out.println("    Report : allure serve " + outputDir);
        System.out.println("  ══════════════════════════════════════════\n");
    }

    private String truncate(String s, int maxLength) {
        if (s == null) return "null";
        return s.length() <= maxLength ? s : s.substring(0, maxLength) + "...";
    }
}