package com.chaosmonkey.cli;

import com.chaosmonkey.fuzzer.FuzzPayloadLibrary;
import com.chaosmonkey.model.FuzzConfig;
import com.chaosmonkey.model.FuzzResult;
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
 * Registered as a Spring Shell component — each @ShellMethod
 * maps to a command the user can run from the terminal.
 *
 * v0.1: single "fuzz" command accepting --url only.
 * --spec and --output are wired in v0.2 and v0.3 respectively.
 */
@ShellComponent
public class ChaosMonkeyCLI {

    private static final Logger log = LoggerFactory.getLogger(ChaosMonkeyCLI.class);

    private final FuzzPayloadLibrary payloadLibrary;
    private final FuzzRunner fuzzRunner;
    private final ResponseAnalyser responseAnalyser;

    public ChaosMonkeyCLI(FuzzPayloadLibrary payloadLibrary,
                          FuzzRunner fuzzRunner,
                          ResponseAnalyser responseAnalyser) {
        this.payloadLibrary = payloadLibrary;
        this.fuzzRunner = fuzzRunner;
        this.responseAnalyser = responseAnalyser;
    }

    /**
     * Main fuzz command — v0.1 static mode.
     *
     * Usage:
     *   fuzz --url https://jsonplaceholder.typicode.com/posts
     *
     * Fires every payload in fuzz-strings.json at the target URL,
     * analyses each response, and prints all flagged results to console.
     */
    @ShellMethod(value = "Fuzz a target API endpoint with the static payload list", key = "fuzz")
    public void fuzz(
            @ShellOption(value = "--url", help = "Base URL of the target API endpoint to fuzz")
            String url,

            @ShellOption(value = "--timeout",
                    help = "Request timeout in milliseconds",
                    defaultValue = "10000")
            int timeoutMs
    ) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Chaos Monkey — v0.1 Static Fuzzer");
        System.out.println("  Target : " + url);
        System.out.println("  Timeout: " + timeoutMs + "ms");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        FuzzConfig config = new FuzzConfig(url, null, FuzzConfig.DEFAULT_OUTPUT_DIR, timeoutMs);

        // Load all payloads from fuzz-strings.json
        List<Object> payloads = payloadLibrary.getAll();
        System.out.println("  Payloads loaded: " + payloads.size());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Fire all payloads at the target
        List<FuzzResult> raw = fuzzRunner.run(url, payloads, config);

        // Analyse responses and attach flags
        List<FuzzResult> analysed = responseAnalyser.analyseAll(raw);

        // Print results summary
        printSummary(analysed);
    }

    /**
     * Prints flagged results to console and a final summary line.
     * Clean results (no flags) are counted but not printed individually
     * to keep the output focused on actual findings.
     */
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
                    System.out.println("  Field    : " + r.fieldName());
                    System.out.println("  Payload  : " + r.payload());
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