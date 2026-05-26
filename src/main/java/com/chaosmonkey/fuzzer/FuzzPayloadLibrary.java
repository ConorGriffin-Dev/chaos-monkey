package com.chaosmonkey.fuzzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the static fuzz payload list from payloads/fuzz-strings.json
 * at startup and exposes it for use by FuzzRunner (v0.1) and
 * FuzzPayloadGenerator (v0.2+).
 *
 * All three payload categories are loaded — strings, integers, booleans.
 * getAll() returns the combined list used in v0.1 static fuzz mode.
 */
@Slf4j
@Component
public class FuzzPayloadLibrary {

    private final List<Object> strings = new ArrayList<>();
    private final List<Object> integers = new ArrayList<>();
    private final List<Object> booleans = new ArrayList<>();

    @PostConstruct
    public void load() {
        try (InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("payloads/fuzz-strings.json")) {

            if (is == null) {
                throw new IllegalStateException(
                        "payloads/fuzz-strings.json not found on classpath");
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);

            root.get("strings").forEach(node ->
                    strings.add(node.isNull() ? null : node.asText()));

            root.get("integers").forEach(node ->
                    integers.add(node.isNull() ? null : node.longValue()));

            root.get("booleans").forEach(node ->
                    booleans.add(node.isNull() ? null : node.asText()));

            log.info("FuzzPayloadLibrary loaded — {} strings, {} integers, {} booleans",
                    strings.size(), integers.size(), booleans.size());

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load fuzz payload library: " + e.getMessage(), e);
        }
    }

    public List<Object> getStrings() { return strings; }
    public List<Object> getIntegers() { return integers; }
    public List<Object> getBooleans() { return booleans; }

    /**
     * Returns all payloads combined — used by FuzzRunner in v0.1 static mode.
     */
    public List<Object> getAll() {
        List<Object> all = new ArrayList<>();
        all.addAll(strings);
        all.addAll(integers);
        all.addAll(booleans);
        return all;
    }
}