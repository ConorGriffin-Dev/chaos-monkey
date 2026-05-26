# Chaos Monkey — Build Roadmap

**Author:** Conor Griffin 
**Version:** 1.0 
**Date:** May 2026 
**Status:** Draft

---

## Overview

Chaos Monkey is built in three versioned milestones. Each version is a fully working,
demonstrable tool — not a partial state. The goal of this phased approach is to have
something runnable and useful at the end of every version, rather than building all
components in parallel and having nothing to show until the end.

---

## Version 0.1 — Static Fuzzer (Get Something Breaking)

**Goal:** A working tool that fires a hardcoded list of fuzz payloads at a target endpoint
and logs everything that breaks. No OpenAPI parsing. No Allure. Just results.

**The definition of done for v0.1:** Run the tool against JSONPlaceholder, see at least
one flagged response in the console output.

### Tasks

#### Project Setup
- [ ] Initialise Spring Boot project via Spring Initializr
- [ ] Add dependencies: Spring Shell, REST Assured, Jackson, Lombok
- [ ] Set up Maven project structure matching the defined file directory
- [ ] Configure `.gitignore`, push initial commit to GitHub

#### Model Layer
- [ ] Implement `FuzzResult` record
- [ ] Implement `FuzzConfig` record
- [ ] Implement `FieldConstraints` record

#### Payload Library
- [ ] Create `payloads/fuzz-strings.json` with initial static payload list
  - Null values, empty strings, whitespace-only strings
  - SQL injection patterns
  - XSS payloads
  - Unicode edge cases and null bytes
  - Boundary integers (INT_MAX, INT_MIN, 0, -1, overflow)
  - Path traversal strings
- [ ] Implement `FuzzPayloadLibrary` — loads and exposes the JSON payload list

#### Runner
- [ ] Implement `FuzzRunner` — fires each payload at a hardcoded endpoint via REST Assured
- [ ] Capture status code, response body, and duration per request
- [ ] Implement `ResponseAnalyser` — apply all detection rules:
  - `SERVER_ERROR` (5xx)
  - `STACK_TRACE_LEAKED`
  - `DB_ERROR_EXPOSED`
  - `UNEXPECTED_SUCCESS`
  - `SLOW_RESPONSE`
  - `EMPTY_SUCCESS_BODY`
  - `VALIDATION_MISSING`

#### CLI
- [ ] Implement `ChaosMonkeyCLI` with Spring Shell
- [ ] Accept `--url` argument
- [ ] Log every flagged response to console with payload, status code, and flag

#### Testing
- [ ] Unit tests for `ResponseAnalyser` — verify each flag fires correctly against mock responses
- [ ] Unit tests for `FuzzPayloadLibrary` — verify JSON loads correctly and all payload
      categories are present

#### Demo
- [ ] Run against `https://jsonplaceholder.typicode.com/posts`
- [ ] Document at least one flagged finding in the README

---

## Version 0.2 — OpenAPI Parser (Make It Smart)

**Goal:** Replace the hardcoded endpoint with full OpenAPI spec parsing. The tool reads
any OpenAPI 3.x spec, extracts every endpoint and field schema, and auto-generates
targeted fuzz payloads per field based on declared type and constraints.

**The definition of done for v0.2:** Point the tool at the Swagger Petstore v3 OpenAPI spec,
have it automatically discover all endpoints, generate schema-aware payloads, and produce
flagged results without any hardcoded endpoint or field configuration.

### Tasks

#### Model Layer
- [ ] Implement `FuzzTarget` record
- [ ] Implement `FieldSchema` record
- [ ] Implement `FuzzCase` record
- [ ] Update `FuzzConfig` to include `specLocation` field

#### Parser
- [ ] Add `io.swagger.parser.v3` dependency to `pom.xml`
- [ ] Implement `OpenApiParser`
  - Fetch spec from remote URL or local file path
  - Support both JSON and YAML formats
  - Extract all paths and HTTP methods
  - Extract request body schemas for POST/PUT/PATCH
  - Extract path and query parameter schemas for GET/DELETE
  - Build `FuzzTarget` list with `FieldSchema` and `FieldConstraints` per field
  - Fall back to untyped string fuzzing for undeclared fields

#### Fuzzer
- [ ] Implement `FuzzPayloadGenerator`
  - `generateForType()` per field type: `integer`, `string`, `boolean`, `number`, `array`, `object`
  - Apply constraint-aware boundary generation:
    - Below `minimum` and above `maximum` for integers
    - Below `minLength` and above `maxLength` for strings
    - Test `null` against non-nullable fields
    - Test missing required fields
    - Test wrong types against every field
  - Supplement schema-aware payloads with relevant entries from `FuzzPayloadLibrary`

#### CLI
- [ ] Add `--spec` argument to `ChaosMonkeyCLI`
- [ ] Wire `OpenApiParser` and `FuzzPayloadGenerator` into the run flow
- [ ] Log per-endpoint progress during the run

#### Testing
- [ ] Unit tests for `OpenApiParser`
  - Verify correct extraction of endpoints from a sample spec
  - Verify correct field types and constraints extracted
  - Verify fallback behaviour for undeclared fields
- [ ] Unit tests for `FuzzPayloadGenerator`
  - Verify correct payloads generated per type
  - Verify boundary payloads generated correctly from constraints
  - Verify null and missing-field payloads generated for required fields

#### Demo
- [ ] Run against Swagger Petstore v3
  - `--url https://petstore3.swagger.io/api/v3`
  - `--spec https://petstore3.swagger.io/api/v3/openapi.json`
- [ ] Document flagged findings — record at least one real bug or unexpected behaviour

---

## Version 0.3 — Allure Reporting + CLI Polish + Open Source

**Goal:** Every run produces a full Allure HTML report. The CLI is complete and documented.
The project is open-sourced on GitHub with a professional README, sample report screenshot,
and usage instructions clear enough for another QA engineer to clone and run immediately.

**The definition of done for v0.3:** A QA engineer with no prior knowledge of the tool can
clone the repo, follow the README, run it against Petstore, and open a full Allure report
in their browser — all within 10 minutes.

### Tasks

#### Allure Integration
- [ ] Add Allure dependencies to `pom.xml`:
  - `allure-java-commons`
  - `allure-rest-assured` (for automatic REST Assured request/response capture)
- [ ] Implement `AllureReporter`
  - Write one Allure `TestResult` per `FuzzResult`
  - Use endpoint + payload as the test name
  - Attach full response body to every flagged result
  - Apply flag strings as Allure labels for filtering in the report
  - Mark `SERVER_ERROR` and `STACK_TRACE_LEAKED` results as `FAILED`
  - Mark `UNEXPECTED_SUCCESS` and `VALIDATION_MISSING` as `BROKEN`
  - Mark clean results as `PASSED`
- [ ] Verify report generates correctly via `allure serve ./allure-results`

#### CLI Polish
- [ ] Add `--output` argument (output directory for Allure results)
- [ ] Add `--timeout` argument (request timeout in milliseconds)
- [ ] Add `--flag-only` flag (write only flagged results to Allure — reduces report noise)
- [ ] Add `--dry-run` flag (parse spec and generate payloads but do not fire requests)
- [ ] Add summary output to console at end of run:
  - Total fuzz cases executed
  - Total flagged results by flag type
  - Output directory path
  - Command to open Allure report

#### Sequence Fuzzing
- [ ] Implement basic out-of-order endpoint testing:
  - Call DELETE before POST
  - Call GET with non-existent IDs
  - Skip required auth headers on protected endpoints
- [ ] Log sequence fuzz results separately in the Allure report

#### Open Source Preparation
- [ ] Write `README.md` at repo root:
  - What it does (one paragraph)
  - Prerequisites (Java 21, Maven, Allure CLI)
  - Installation instructions
  - Usage examples (minimum, full, static mode)
  - Sample Allure report screenshot
  - Findings from Petstore run
  - How to run the tests
- [ ] Add `LICENSE` (MIT)
- [ ] Add `CONTRIBUTING.md`
- [ ] Tag `v0.3.0` release on GitHub
- [ ] Add GitHub Actions CI workflow:
  - Build on push to main
  - Run all unit tests
  - Fail build on test failure

#### Testing
- [ ] Unit tests for `AllureReporter`
  - Verify correct result files written to output directory
  - Verify flagged results marked with correct Allure status
- [ ] Integration test — full run against Petstore spec
  - Verify at least one flagged result is produced
  - Verify Allure results directory is populated

---

## Milestone Summary

| Version | Focus | Outcome |
|---------|-------|---------|
| v0.1 | Static fuzzer | Working tool, console output, real findings |
| v0.2 | OpenAPI parser | Schema-aware fuzzing, auto-discovers endpoints |
| v0.3 | Allure + CLI + OSS | Professional report, complete CLI, GitHub ready |

---

## Future Versions (Post v0.3)

These are not in scope for the current build but are documented for completeness.

| Feature | Description |
|---------|-------------|
| Auth support | Inject Bearer tokens, API keys, or Basic auth headers via CLI argument |
| Rate limit handling | Detect `429` responses, respect `Retry-After`, add configurable delay between requests |
| Swagger 2.0 support | Extend the parser to handle legacy Swagger 2.0 specs |
| YAML config file | Allow all CLI arguments to be defined in a `chaos-monkey.yml` config file |
| Parallel execution | Fire multiple fuzz requests concurrently with configurable thread pool |
| Custom payload injection | Allow users to supply their own payload lists via CLI argument |
| Docker image | Package the tool as a Docker image for zero-install usage |
