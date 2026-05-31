package com.chaosmonkey.reporter;

import com.chaosmonkey.model.FuzzResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
public class AllureReporter {

    private final ObjectMapper objectMapper;

    public AllureReporter() {
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void write(List<FuzzResult> results, String outputDir, boolean flagOnly) {
        Path outPath = Path.of(outputDir);
        try {
            Files.createDirectories(outPath);
        } catch (IOException e) {
            log.error("Failed to create output directory: {}", outputDir, e);
            return;
        }

        List<FuzzResult> toWrite = flagOnly
                ? results.stream().filter(r -> !r.flags().isEmpty()).toList()
                : results;

        int written = 0;
        for (FuzzResult result : toWrite) {
            try {
                writeResult(result, outPath);
                written++;
            } catch (IOException e) {
                log.error("Failed to write result for {} | {}", result.endpoint(), result.fieldName(), e);
            }
        }

        log.info("AllureReporter complete — {} result files written to {}", written, outputDir);
    }

    private void writeResult(FuzzResult result, Path outPath) throws IOException {
        String uuid = UUID.randomUUID().toString();
        String status = resolveStatus(result);

        List<Map<String, String>> labels = new ArrayList<>();
        for (String flag : result.flags()) {
            labels.add(Map.of("name", "tag", "value", flag));
        }
        labels.add(Map.of("name", "suite", "value", result.endpoint()));
        labels.add(Map.of("name", "feature", "value", result.method() + " " + result.endpoint()));

        List<Map<String, String>> attachments = new ArrayList<>();
        if (!result.flags().isEmpty()
                && result.responseBody() != null
                && !result.responseBody().isBlank()) {
            String attachmentSource = uuid + "-attachment.txt";
            Files.writeString(outPath.resolve(attachmentSource), result.responseBody());
            attachments.add(Map.of(
                    "name", "Response Body",
                    "source", attachmentSource,
                    "type", "text/plain"
            ));
        }

        String testName = result.method() + " " + result.endpoint()
                + " | " + result.fieldName()
                + " | " + result.payload();

        Map<String, Object> allureResult = new LinkedHashMap<>();
        allureResult.put("uuid", uuid);
        allureResult.put("name", testName);
        allureResult.put("status", status);
        allureResult.put("labels", labels);
        allureResult.put("attachments", attachments);
        allureResult.put("start", System.currentTimeMillis());
        allureResult.put("stop", System.currentTimeMillis() + result.durationMs());

        objectMapper.writeValue(outPath.resolve(uuid + "-result.json").toFile(), allureResult);
    }

    private String resolveStatus(FuzzResult result) {
        if (result.flags().isEmpty()) return "passed";
        if (result.flags().contains("SERVER_ERROR")
                || result.flags().contains("STACK_TRACE_LEAKED")) {
            return "failed";
        }
        return "broken";
    }
}