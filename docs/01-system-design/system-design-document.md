# Chaos Monkey — System Design Document

**Author:** Conor Griffin 
**Version:** 1.0 
**Date:** May 2026 
**Status:** Draft

---

## Table of Contents

1. [Overview](#1-overview)
2. [Goals and Non-Goals](#2-goals-and-non-goals)
3. [Users and Use Cases](#3-users-and-use-cases)
4. [System Architecture](#4-system-architecture)
5. [Component Breakdown](#5-component-breakdown)
6. [Data Models](#6-data-models)
7. [Fuzz Payload Strategy](#7-fuzz-payload-strategy)
8. [Response Analysis Strategy](#8-response-analysis-strategy)
9. [Reporting](#9-reporting)
10. [CLI Interface](#10-cli-interface)
11. [Target API](#11-target-api)
12. [Constraints and Assumptions](#12-constraints-and-assumptions)
13. [Risks](#13-risks)

---

## 1. Overview

Chaos Monkey is an automated REST API fuzzer built in Java. It targets REST APIs described
by an OpenAPI 3.x specification and hammers every endpoint with malformed, boundary-busting,
and semantically invalid inputs — logging everything that breaks.

The tool is born from real frustration encountered during professional QA work on a national
government platform (DAFM/MyAgFood), where manual negative-path API testing was slow,
incomplete, and largely dependent on the tester remembering edge cases. Chaos Monkey
automates that process entirely.

The core loop is simple:

```
Parse OpenAPI Spec → Generate Fuzz Payloads → Execute Requests → Analyse Responses → Report
```

Every fuzz run produces a full Allure HTML report detailing which inputs caused server errors,
which caused unexpected successes, which leaked stack traces, and which caused performance
outliers.

---

## 2. Goals and Non-Goals

### Goals

| ID  | Goal |
|-----|------|
| G1  | Parse any valid OpenAPI 3.x specification from a URL or local file |
| G2  | Auto-generate schema-aware fuzz payloads per field based on declared type and constraints |
| G3  | Execute fuzz requests against a target API using REST Assured |
| G4  | Detect and flag interesting responses: 500s, stack trace leaks, unexpected 200s, slow responses, exposed DB errors |
| G5  | Produce a full Allure HTML report for every fuzz run |
| G6  | Expose a simple CLI: `--url`, `--spec`, `--output` |
| G7  | Be runnable against any publicly documented REST API without code changes |
| G8  | Be open-sourced on GitHub with a clear README and sample report |

### Non-Goals

| ID  | Non-Goal |
|-----|----------|
| NG1 | Chaos Monkey is not a security scanner — it does not exploit vulnerabilities, only surface them |
| NG2 | It does not test WebSocket, GraphQL, or gRPC endpoints |
| NG3 | It does not generate authenticated session flows (OAuth, SSO) — basic auth header injection is supported but full auth flows are out of scope for v1 |
| NG4 | It does not mutate binary file uploads |
| NG5 | It is not a load/performance testing tool — concurrent requests are used for sequence fuzzing only, not volume testing |

---

## 3. Users and Use Cases

### Primary User

A QA Engineer or SDET who wants to run automated negative-path testing against a REST API
without writing individual test cases for every field and endpoint.

### Use Cases

#### UC-01: Fuzz a Public API from its OpenAPI Spec

**Actor:** QA Engineer 
**Precondition:** Target API has a publicly accessible OpenAPI 3.x spec 
**Flow:**
1. Engineer runs `chaos-monkey --url https://api.example.com --spec https://api.example.com/openapi.json`
2. Tool parses the spec, extracts all endpoints and field schemas
3. Tool generates fuzz payloads per field based on declared types and constraints
4. Tool fires all fuzz requests against the live API
5. Tool generates an Allure report at `./allure-report/`
6. Engineer opens the report and reviews flagged responses

**Postcondition:** Engineer has a complete picture of how the API behaves under malformed input

---

#### UC-02: Run Against a Static Fuzz List (v0.1 Mode)

**Actor:** QA Engineer 
**Precondition:** Engineer has a target endpoint URL 
**Flow:**
1. Engineer runs the tool pointed at a single endpoint with no spec provided
2. Tool loads `payloads/fuzz-strings.json` and fires every payload at the endpoint
3. Every non-2xx response is logged to console and written to results

**Postcondition:** Quick smoke-level fuzz of a single endpoint with no spec required

---

#### UC-03: Review Findings in Allure Report

**Actor:** QA Engineer / Team Lead 
**Precondition:** A fuzz run has completed 
**Flow:**
1. Engineer opens the generated Allure HTML report in a browser
2. Report shows all fuzz cases grouped by endpoint
3. Flagged results (500s, stack traces, unexpected 200s) are highlighted
4. Engineer exports or shares the report with the engineering team

---

## 4. System Architecture

Chaos Monkey is a single-process Java CLI application. It has no server, no database, and no
persistent state between runs. Everything is computed at runtime and written to the
`allure-results/` directory for report generation.

### High-Level Flow

```
CLI Input
    │
    ▼
FuzzConfig (target URL, spec location, output path)
    │
    ├──► OpenApiParser ──► List<FuzzTarget> (endpoint + field schema per endpoint)
    │
    ▼
FuzzPayloadGenerator ──► List<FuzzCase> (one per field × payload combination)
    │
    ▼
FuzzRunner (REST Assured) ──► List<FuzzResult> (response per case)
    │
    ▼
ResponseAnalyser ──► List<FuzzResult> with flags attached
    │
    ▼
AllureReporter ──► allure-results/ ──► allure-report/ (HTML)
```

---

## 5. Component Breakdown

### 5.1 CLI (`cli/`)

**Class:** `ChaosMonkeyCLI` 
**Responsibility:** Entry point. Annotated with @SpringBootApplication. Uses Spring Shell (@ShellComponent) to handle CLI argument parsing and command registration. Spring DI wires all components automatically.

| Argument | Description | Required |
|----------|-------------|----------|
| `--url` | Base URL of the target API | Yes |
| `--spec` | Path or URL to the OpenAPI JSON/YAML spec | No (falls back to static payload list) |
| `--output` | Output directory for Allure results | No (defaults to `./allure-results`) |
| `--timeout` | Request timeout in milliseconds | No (defaults to 10000) |

---

### 5.2 Config (`config/`)

**Class:** `FuzzConfig` 
**Responsibility:** Holds all runtime configuration. Bound from CLI arguments via Spring Shell and passed through the component chain via Spring DI. Annotated with @ConfigurationProperties.

```java
public record FuzzConfig(
    String baseUrl,
    String specLocation,
    String outputDir,
    int timeoutMs
) {}
```

---

### 5.3 Parser (`parser/`)

**Class:** `OpenApiParser` 
**Responsibility:** Reads an OpenAPI 3.x specification and produces a list of `FuzzTarget` objects
— one per endpoint per HTTP method. Each `FuzzTarget` contains the path, method, and a map
of field names to their declared type and constraints.

**Library:** `io.swagger.parser.v3` (Swagger Parser)

**Key behaviour:**
- Supports both JSON and YAML specs
- Supports remote URLs and local file paths
- Extracts request body schemas for POST/PUT/PATCH
- Extracts path and query parameter schemas for GET/DELETE
- Falls back to untyped string fuzzing for fields with no schema declared

---

### 5.4 Fuzzer (`fuzzer/`)

**Class:** `FuzzPayloadGenerator` 
**Responsibility:** For each field in each `FuzzTarget`, generates a list of fuzz payloads based on
the declared type and constraints.

**Class:** `FuzzPayloadLibrary` 
**Responsibility:** Holds the static fuzz string list loaded from `payloads/fuzz-strings.json`.
Used both as a fallback when no spec is provided and as a supplement to schema-aware generation.

#### Payload Strategy by Type

| Field Type | Payloads Generated |
|------------|-------------------|
| `integer` | `null`, `0`, `-1`, `INT_MAX`, `INT_MIN`, `9999999999` (overflow), `"not_an_int"`, below min, above max |
| `string` | `null`, `""`, `" "`, 10,000 char string, SQL injection patterns, XSS payloads, null byte, Unicode edge cases, path traversal, below minLength, above maxLength |
| `boolean` | `null`, `"true"` (string), `1` (int), `"yes"`, `"false"` |
| `number` | Same as integer plus `NaN`, `Infinity`, `-Infinity` |
| `array` | `null`, `[]`, single-element, 10,000-element, wrong element types |
| `object` | `null`, `{}`, missing required fields, extra unknown fields |

---

### 5.5 Runner (`runner/`)

**Class:** `FuzzRunner` 
**Responsibility:** Executes each fuzz case against the target API using REST Assured. Records
the full response including status code, body, headers, and duration.

**Class:** `ResponseAnalyser` 
**Responsibility:** Examines each `FuzzResult` and attaches flags based on what the response
reveals about API behaviour.

---

### 5.6 Model (`model/`)

**Class:** `FuzzTarget` 
Represents a single endpoint and HTTP method extracted from the OpenAPI spec.

```java
public record FuzzTarget(
    String path,
    String method,
    Map<String, FieldSchema> fields
) {}
```

**Class:** `FieldSchema` 
Represents the type and constraints for a single field.

```java
public record FieldSchema(
    String type,
    FieldConstraints constraints
) {}
```

**Class:** `FieldConstraints` 
Holds all OpenAPI constraints declared for a field.

```java
public record FieldConstraints(
    Integer minimum,
    Integer maximum,
    Integer minLength,
    Integer maxLength,
    Boolean nullable,
    List<String> enumValues
) {}
```

**Class:** `FuzzResult` 
Represents the outcome of a single fuzz request.

```java
public record FuzzResult(
    String endpoint,
    String method,
    String fieldName,
    Object payload,
    int statusCode,
    String responseBody,
    long durationMs,
    List<String> flags
) {}
```

---

### 5.7 Reporter (`reporter/`)

**Class:** `AllureReporter` 
**Responsibility:** Writes Allure-compatible result files to the output directory after each fuzz run.
Each `FuzzResult` becomes an Allure test case with the payload as the test name, the flags as
labels, and the response body attached as a file attachment.

---

## 6. Data Models

### FuzzResult Flags

Flags are string constants attached to a `FuzzResult` by the `ResponseAnalyser`. They drive
how results are categorised in the Allure report.

| Flag | Trigger Condition |
|------|-------------------|
| `SERVER_ERROR` | Response status is 5xx |
| `UNEXPECTED_SUCCESS` | Response is 2xx but input was intentionally malformed |
| `STACK_TRACE_LEAKED` | Response body contains `at com.`, `Exception`, `NullPointerException`, or similar Java stack trace patterns |
| `DB_ERROR_EXPOSED` | Response body contains `SQLException`, `syntax error`, `ORA-`, or similar database error strings |
| `SLOW_RESPONSE_{N}ms` | Response took longer than the configured timeout threshold (default: 3000ms) |
| `EMPTY_SUCCESS_BODY` | Status is 2xx but body is null or blank |
| `VALIDATION_MISSING` | Status is 2xx when a 400 was expected for a clearly invalid input |

### fuzz-strings.json Structure

```json
{
  "strings": [
    "",
    " ",
    "null",
    "undefined",
    "'; DROP TABLE users;--",
    "<script>alert(1)</script>",
    "../../../etc/passwd",
    "\u0000",
    "🔥💀🎯",
    "AAAAAAAAAA..."
  ],
  "integers": [
    0, -1, 2147483647, -2147483648, 99999999999
  ],
  "booleans": [
    null, "true", "false", 1, 0, "yes", "no"
  ]
}
```

---

## 7. Fuzz Payload Strategy

### Schema-Aware Generation (v0.2+)

When an OpenAPI spec is available, the `FuzzPayloadGenerator` reads each field's declared
type and constraints and generates payloads that specifically violate them:

- A field declared as `integer` with `minimum: 1` will be tested with `0` and `-1`
- A field declared as `string` with `maxLength: 100` will be tested with a 101-character string
- A field declared as `boolean` will be tested with `"true"` (string coercion attack)
- A required field will be tested with `null` and with the field omitted entirely

### Static Fuzzing (v0.1 / fallback)

When no spec is available, `FuzzPayloadLibrary` loads `payloads/fuzz-strings.json` and fires
every payload at every field position. This is broader but less targeted than schema-aware
generation.

### Sequence Fuzzing (v0.3+)

Endpoints are tested out of their natural order. For example:
- Calling `DELETE /resource/{id}` before `POST /resource`
- Calling `GET /resource/{id}` with a non-existent ID
- Skipping authentication headers entirely on protected endpoints

---

## 8. Response Analysis Strategy

The `ResponseAnalyser` does not just check for 500s. It applies a full set of detection rules
to each response:

### Detection Rules

**Stack Trace Detection**
```
body.contains("at com.") 
|| body.contains("NullPointerException")
|| body.contains("IllegalArgumentException")
|| body.contains("StackTrace")
```
A stack trace in a production API response is always a finding — it exposes internal
implementation details regardless of the HTTP status code.

**Database Error Detection**
```
body.contains("SQLException")
|| body.contains("syntax error near")
|| body.contains("ORA-")
|| body.contains("ERROR: column")
```
Database error messages in API responses indicate insufficient error handling and
potentially exploitable injection surfaces.

**Unexpected Success Detection**
When a payload is known to be semantically invalid (null sent to a required field, a string
sent to an integer field), a 2xx response indicates the API is silently accepting bad data —
a data integrity risk.

**Performance Outlier Detection**
Responses that take significantly longer than the baseline for that endpoint may indicate
a ReDoS vulnerability, a missing database index being hit by a large input, or an
unguarded expensive operation.

---

## 9. Reporting

Every fuzz run produces Allure result files. Running `allure serve ./allure-results` generates
a full interactive HTML report.

### Report Structure

- **Overview:** Total fuzz cases, pass/fail/flagged breakdown, run duration
- **By Endpoint:** All fuzz cases grouped by API endpoint
- **By Flag:** All flagged results grouped by flag type (e.g. all `SERVER_ERROR` results together)
- **Detail View:** For each flagged result — the exact payload sent, the full response body,
  the response time, and the flag reason
- **Attachments:** Full response bodies attached as files for every flagged result

---

## 10. CLI Interface

```bash
# Minimum usage — spec-aware fuzz run
java -jar chaos-monkey.jar \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json

# With custom output directory
java -jar chaos-monkey.jar \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json \
  --output ./my-fuzz-results

# Static fuzz mode — no spec required
java -jar chaos-monkey.jar \
  --url https://jsonplaceholder.typicode.com

# Generate and open report after run
java -jar chaos-monkey.jar --url ... --spec ...
allure serve ./allure-results
```

---

## 11. Target API

For development and demonstration purposes, Chaos Monkey is tested against the
**Swagger Petstore v3** — the official OpenAPI demo API maintained by the Swagger team.

**Base URL:** `https://petstore3.swagger.io/api/v3` 
**Spec:** `https://petstore3.swagger.io/api/v3/openapi.json` 
**Why:** It is publicly available, has a well-documented OpenAPI spec, covers multiple
HTTP methods and field types, and is deliberately designed as a demo target — making
it appropriate for automated fuzzing without ethical concerns.

Secondary target for static fuzz mode: **JSONPlaceholder** (`https://jsonplaceholder.typicode.com`)

---

## 12. Constraints and Assumptions

| ID | Constraint / Assumption |
|----|------------------------|
| C1 | Target API must be accessible over HTTP/HTTPS from the machine running the tool |
| C2 | OpenAPI spec must be version 3.x — Swagger 2.0 specs are not supported in v1 |
| C3 | The tool fires real HTTP requests — it must not be run against production APIs without explicit permission |
| C4 | Java 21 and Maven are required to build and run the tool |
| C5 | Allure CLI must be installed separately to generate the HTML report from results |
| C6 | The tool assumes the target API accepts `application/json` request bodies |

---

## 13. Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|----|------|------------|--------|------------|
| R1 | Target API rate-limits or bans the fuzzer IP | Medium | High | Add configurable delay between requests; respect `Retry-After` headers |
| R2 | OpenAPI spec is incomplete or inaccurate | High | Medium | Fall back to static payload list for undocumented fields |
| R3 | Some fuzz payloads cause unrecoverable server state (e.g. deleting all resources) | Low | High | Prefer GET and POST over DELETE in early runs; add `--dry-run` flag in v0.3 |
| R4 | Allure report becomes unmanageable with thousands of results | Medium | Low | Add `--flag-only` mode that writes only flagged results to the report |
| R5 | False positives in stack trace detection | Medium | Low | Tune detection patterns based on real run results; allow pattern exclusion via config |
