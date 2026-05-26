# Chaos Monkey — Usage Guide

**Author:** Conor Griffin 
**Version:** 1.0 
**Date:** May 2026 
**Status:** Draft — updated per version release

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Installation](#2-installation)
3. [CLI Reference](#3-cli-reference)
4. [Usage Examples](#4-usage-examples)
5. [Generating the Allure Report](#5-generating-the-allure-report)
6. [Reading the Report](#6-reading-the-report)
7. [Understanding Flags](#7-understanding-flags)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java JDK | 21+ | Must be JDK not JRE — verify with `java -version` |
| Maven | 3.8+ | For building the project — verify with `mvn -version` |
| Allure CLI | Latest | For generating HTML reports — see installation below |
| Git | Any | For cloning the repository |

### Installing Allure CLI

**macOS (Homebrew):**
```bash
brew install allure
```

**Windows (Scoop):**
```bash
scoop install allure
```

**Linux:**
```bash
# Download the latest release from https://github.com/allure-framework/allure2/releases
# Extract and add to PATH
```

Verify installation:
```bash
allure --version
```

---

## 2. Installation

### Clone the Repository

```bash
git clone https://github.com/YoungGriff11/chaos-monkey.git
cd chaos-monkey
```

### Build the Project

```bash
mvn clean package -DskipTests
```

This produces `target/chaos-monkey.jar` — the executable JAR.

### Verify the Build

```bash
java -jar target/chaos-monkey.jar --help
```

Expected output:
```
Chaos Monkey — Automated REST API Fuzzer
Usage: chaos-monkey [--url] [--spec] [--output] [--timeout] [--flag-only] [--dry-run]
...
```

---

## 3. CLI Reference

| Argument | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `--url` | String | Yes | — | Base URL of the target API |
| `--spec` | String | No | — | Path or URL to the OpenAPI 3.x JSON or YAML spec. If omitted, falls back to static fuzz list |
| `--output` | String | No | `./allure-results` | Output directory for Allure result files |
| `--timeout` | Integer | No | `10000` | Request timeout in milliseconds |
| `--flag-only` | Boolean | No | `false` | Write only flagged results to Allure — reduces report noise on large APIs |
| `--dry-run` | Boolean | No | `false` | Parse spec and generate payloads but do not fire any HTTP requests |

---

## 4. Usage Examples

### Minimum — Spec-Aware Fuzz Run

Provide a base URL and an OpenAPI spec. The tool will auto-discover all endpoints,
generate schema-aware fuzz payloads, and report everything it finds.

```bash
java -jar target/chaos-monkey.jar \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json
```

---

### With Custom Output Directory

```bash
java -jar target/chaos-monkey.jar \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json \
  --output ./my-fuzz-results
```

---

### With Custom Timeout

Useful when testing APIs that are known to be slow, or to tighten the slow response
threshold for faster APIs.

```bash
java -jar target/chaos-monkey.jar \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json \
  --timeout 5000
```

---

### Flag-Only Mode

On large APIs with many endpoints, the full report can contain thousands of results.
`--flag-only` writes only the results that triggered at least one flag — keeping the
report focused on actual findings.

```bash
java -jar target/chaos-monkey.jar \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json \
  --flag-only
```

---

### Dry Run — Preview Payloads Without Firing Requests

Parses the spec and generates all fuzz payloads, logging them to console, but does
not fire any HTTP requests. Useful for verifying the tool has correctly read the spec
before running a full fuzz against a sensitive environment.

```bash
java -jar target/chaos-monkey.jar \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json \
  --dry-run
```

---

### Static Fuzz Mode — No Spec Required

If no `--spec` is provided, the tool falls back to the static fuzz payload list in
`payloads/fuzz-strings.json` and fires every payload at every endpoint it can infer
from the base URL. This is broader but less targeted than spec-aware mode.

```bash
java -jar target/chaos-monkey.jar \
  --url https://jsonplaceholder.typicode.com
```

---

### Local Spec File

The `--spec` argument accepts a local file path as well as a remote URL.

```bash
java -jar target/chaos-monkey.jar \
  --url https://api.example.com \
  --spec ./openapi.json
```

---

## 5. Generating the Allure Report

After a fuzz run completes, result files are written to the output directory
(default: `./allure-results`). Run the following command to generate and open
the HTML report in your browser:

```bash
allure serve ./allure-results
```

This starts a local HTTP server and opens the report automatically.

To generate the report as a static HTML folder without opening it:

```bash
allure generate ./allure-results --output ./allure-report --clean
```

Then open `./allure-report/index.html` in any browser.

---

## 6. Reading the Report

The Allure report organises fuzz results into the following views:

### Overview
- Total fuzz cases executed
- Pass / Fail / Broken breakdown
- Run duration and timestamp

### Suites View
Results grouped by endpoint. Each endpoint shows all fuzz cases that were run
against it and their outcomes.

### Behaviours View
Results grouped by flag type. Use this view to see all `SERVER_ERROR` results
together, all `STACK_TRACE_LEAKED` results together, and so on.

### Result Status Mapping

| Allure Status | Chaos Monkey Meaning |
|---------------|----------------------|
| `FAILED` | `SERVER_ERROR` or `STACK_TRACE_LEAKED` — the API broke or leaked internals |
| `BROKEN` | `UNEXPECTED_SUCCESS`, `VALIDATION_MISSING`, or `DB_ERROR_EXPOSED` — the API behaved unexpectedly |
| `PASSED` | No flags — the API handled the malformed input correctly |

### Attachments
Every flagged result has the full response body attached. Click on a result to
expand it and view the exact payload that was sent and the full response that
was received.

---

## 7. Understanding Flags

| Flag | What It Means | Severity |
|------|---------------|----------|
| `SERVER_ERROR` | The API returned a 5xx response to a fuzz input. The server crashed or errored internally. | High |
| `STACK_TRACE_LEAKED` | The response body contains a Java stack trace or exception class name. Internal implementation details are exposed. | High |
| `DB_ERROR_EXPOSED` | The response body contains a database error message (SQLException, ORA- error etc). Potential injection surface. | High |
| `UNEXPECTED_SUCCESS` | The API returned 2xx to a clearly invalid input (null on a required field, wrong type). The API is silently accepting bad data. | Medium |
| `VALIDATION_MISSING` | The API returned 2xx when a 400 Bad Request was expected for a clearly out-of-range input. Input validation is absent or incomplete. | Medium |
| `SLOW_RESPONSE_{N}ms` | The API took longer than the configured timeout threshold to respond. Possible ReDoS, missing index, or unguarded expensive operation. | Low |
| `EMPTY_SUCCESS_BODY` | The API returned 2xx with a blank or null response body. May indicate silent failure. | Low |

---

## 8. Troubleshooting

| Problem | Likely Cause | Solution |
|---------|-------------|----------|
| `java: command not found` | Java not installed or not on PATH | Install JDK 21 and add to PATH |
| `Could not find or load main class` | JAR not built | Run `mvn clean package -DskipTests` first |
| `Unable to read spec from [URL]` | Spec URL unreachable or invalid | Verify the URL returns a valid OpenAPI JSON or YAML in the browser |
| `Connection refused` to target API | Target API is down or URL is wrong | Verify the base URL is reachable — try `curl [url]` |
| `allure: command not found` | Allure CLI not installed | Follow the Allure installation steps in Section 1 |
| Report is empty | No results written to output dir | Check the output dir path — default is `./allure-results` relative to where you ran the JAR |
| All results passing — nothing flagged | Target API handles all inputs correctly, or payloads not reaching the API | Try `--dry-run` to verify payloads are being generated, check network connectivity |
| Too many results in report | Large API with many endpoints and fields | Re-run with `--flag-only` to filter report to findings only |
| `429 Too Many Requests` from target | API is rate limiting the fuzzer | Add `--timeout` with a higher value to slow the run, or run during off-peak hours |
