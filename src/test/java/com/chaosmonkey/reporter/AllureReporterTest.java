package com.chaosmonkey.reporter;

import com.chaosmonkey.model.FuzzResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllureReporterTest {

    private AllureReporter reporter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        reporter = new AllureReporter();
    }

    private FuzzResult cleanResult() {
        return new FuzzResult(
                "https://api.example.com/test", "POST",
                "name", "payload",
                200, "{\"id\": 1}", 150L, List.of());
    }

    private FuzzResult flaggedResult(List<String> flags) {
        return new FuzzResult(
                "https://api.example.com/test", "POST",
                "name", "payload",
                500, "Internal Server Error", 150L, flags);
    }

    // AR01
    @Test
    void write_singleResult_resultFileWrittenToOutputDir() throws IOException {
        reporter.write(List.of(cleanResult()), tempDir.toString(), false);

        long jsonFiles = Files.list(tempDir)
                .filter(p -> p.toString().endsWith("-result.json"))
                .count();
        assertThat(jsonFiles).isEqualTo(1);
    }

    // AR02
    @Test
    void write_serverErrorFlaggedResult_statusIsFailed() throws IOException {
        reporter.write(List.of(flaggedResult(List.of("SERVER_ERROR"))), tempDir.toString(), false);

        Path resultFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith("-result.json"))
                .findFirst().orElseThrow();
        assertThat(Files.readString(resultFile)).contains("\"status\" : \"failed\"");
    }

    // AR03
    @Test
    void write_flaggedResultWithBody_responseBodyAttached() throws IOException {
        reporter.write(List.of(flaggedResult(List.of("SERVER_ERROR"))), tempDir.toString(), false);

        long attachmentFiles = Files.list(tempDir)
                .filter(p -> p.toString().endsWith("-attachment.txt"))
                .count();
        assertThat(attachmentFiles).isEqualTo(1);

        Path attachmentFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith("-attachment.txt"))
                .findFirst().orElseThrow();
        assertThat(Files.readString(attachmentFile)).isEqualTo("Internal Server Error");
    }

    // AR04
    @Test
    void write_cleanResult_statusIsPassed() throws IOException {
        reporter.write(List.of(cleanResult()), tempDir.toString(), false);

        Path resultFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith("-result.json"))
                .findFirst().orElseThrow();
        assertThat(Files.readString(resultFile)).contains("\"status\" : \"passed\"");
    }

    // AR05
    @Test
    void write_multipleResults_allFilesWritten() throws IOException {
        List<FuzzResult> results = List.of(
                cleanResult(), cleanResult(), cleanResult(),
                flaggedResult(List.of("SERVER_ERROR")),
                flaggedResult(List.of("STACK_TRACE_LEAKED"))
        );
        reporter.write(results, tempDir.toString(), false);

        long jsonFiles = Files.list(tempDir)
                .filter(p -> p.toString().endsWith("-result.json"))
                .count();
        assertThat(jsonFiles).isEqualTo(5);
    }

    @Test
    void write_flagOnlyTrue_onlyFlaggedResultsWritten() throws IOException {
        List<FuzzResult> results = List.of(
                cleanResult(), cleanResult(),
                flaggedResult(List.of("SERVER_ERROR"))
        );
        reporter.write(results, tempDir.toString(), true);

        long jsonFiles = Files.list(tempDir)
                .filter(p -> p.toString().endsWith("-result.json"))
                .count();
        assertThat(jsonFiles).isEqualTo(1);
    }

    @Test
    void write_unexpectedSuccessFlaggedResult_statusIsBroken() throws IOException {
        reporter.write(List.of(flaggedResult(List.of("UNEXPECTED_SUCCESS"))), tempDir.toString(), false);

        Path resultFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith("-result.json"))
                .findFirst().orElseThrow();
        assertThat(Files.readString(resultFile)).contains("\"status\" : \"broken\"");
    }

    @Test
    void write_stackTraceLeakedResult_statusIsFailed() throws IOException {
        reporter.write(List.of(flaggedResult(List.of("STACK_TRACE_LEAKED"))), tempDir.toString(), false);

        Path resultFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith("-result.json"))
                .findFirst().orElseThrow();
        assertThat(Files.readString(resultFile)).contains("\"status\" : \"failed\"");
    }
}