# Chaos Monkey — Test Plan

**Author:** Conor Griffin 
**Version:** 1.0 
**Date:** May 2026 
**Status:** Draft

---

## Table of Contents

1. [Objectives](#1-objectives)
2. [Scope](#2-scope)
3. [Test Strategy](#3-test-strategy)
4. [Test Environment](#4-test-environment)
5. [Test Organisation](#5-test-organisation)
6. [Test Cases](#6-test-cases)
7. [Coverage Targets](#7-coverage-targets)
8. [Known Limitations](#8-known-limitations)

---

## 1. Objectives

1. Verify that each component of Chaos Monkey behaves correctly in isolation via unit tests
2. Verify that the full fuzz run pipeline produces correct output end-to-end via integration tests
3. Verify that the `ResponseAnalyser` correctly flags every defined flag type
4. Verify that the `FuzzPayloadGenerator` produces correct and complete payloads per field type and constraint
5. Verify that the `OpenApiParser` correctly extracts endpoints, fields, types, and constraints from a real OpenAPI spec
6. Verify that the `AllureReporter` writes correctly structured result files to the output directory

---

## 2. Scope

### In Scope

- All service and component classes: `OpenApiParser`, `FuzzPayloadGenerator`, `FuzzPayloadLibrary`, `FuzzRunner`, `ResponseAnalyser`, `AllureReporter`
- All model records: `FuzzTarget`, `FieldSchema`, `FieldConstraints`, `FuzzCase`, `FuzzResult`
- CLI argument parsing via Spring Shell
- Full pipeline integration test against Swagger Petstore v3
- Flag detection logic across all seven defined flag types

### Out of Scope

- UI testing — this is a CLI tool, there is no UI
- Load or performance testing of the tool itself
- Testing the target API (Petstore) — we are testing Chaos Monkey, not its target
- Manual testing of the Allure HTML report rendering — verified visually, not automated
- Authentication flows beyond basic header injection

---

## 3. Test Strategy

### Unit Tests — JUnit 5 + Mockito

Every service class is tested in isolation. External dependencies (REST Assured HTTP calls,
file system reads) are mocked via Mockito. Unit tests are fast, deterministic, and run on
every build.

### Integration Tests — SpringBootTest

A small number of integration tests verify the full pipeline end-to-end. These tests fire
real HTTP requests against the Swagger Petstore v3 API and verify that the output contains
at least one flagged result. Integration tests are tagged `@Tag("integration")` and can be
excluded from the standard build via Maven profile.

### Test Naming Convention

All test method names follow the pattern:

```
methodName_stateUnderTest_expectedBehaviour()
```

Examples:
- `analyse_500Response_serverErrorFlagAttached()`
- `generateForType_integerWithMaxConstraint_aboveMaxPayloadIncluded()`
- `parse_validPetstoreSpec_allEndpointsExtracted()`

### Test Data

- Mock HTTP responses are built inline in each test using Mockito
- A sample OpenAPI spec (`test-petstore.json`) is stored in `src/test/resources/` for
  parser tests — this avoids network calls in unit tests
- `fuzz-strings.json` is loaded from `src/test/resources/payloads/` in tests to avoid
  dependency on the production payload file

---

## 4. Test Environment

| Component | Tool / Version |
|-----------|---------------|
| Test framework | JUnit 5 (junit-jupiter) |
| Mocking | Mockito |
| Spring test support | SpringBootTest, @MockBean |
| Assertions | AssertJ |
| Integration target | Swagger Petstore v3 (live) |
| Build tool | Maven |
| CI | GitHub Actions |

---

## 5. Test Organisation

| Test Class | Package | Covers |
|------------|---------|--------|
| `ResponseAnalyserTest` | `runner/` | All seven flag types, clean response, multiple flags on one response |
| `FuzzPayloadGeneratorTest` | `fuzzer/` | All field types, constraint-aware boundary generation, null/missing field payloads |
| `FuzzPayloadLibraryTest` | `fuzzer/` | JSON loads correctly, all payload categories present, no empty categories |
| `OpenApiParserTest` | `parser/` | Endpoint extraction, field type extraction, constraint extraction, fallback for undeclared fields |
| `FuzzRunnerTest` | `runner/` | Request fired per fuzz case, FuzzResult populated correctly, timeout handled |
| `AllureReporterTest` | `reporter/` | Result files written to output directory, flagged results marked with correct status |
| `FuzzPayloadGeneratorIntegrationTest` | `fuzzer/` | Schema-aware payloads generated correctly from a real parsed spec |
| `ChaosMonkeyIntegrationTest` | `cli/` | Full pipeline run against Petstore — at least one flagged result produced |

---

## 6. Test Cases

### ResponseAnalyser

| ID | Test Name | Input | Expected |
|----|-----------|-------|----------|
| RA01 | `analyse_500Response_serverErrorFlagAttached` | Response with status 500 | `SERVER_ERROR` flag present |
| RA02 | `analyse_502Response_serverErrorFlagAttached` | Response with status 502 | `SERVER_ERROR` flag present |
| RA03 | `analyse_200Response_cleanInput_noFlagsAttached` | Status 200, valid input, normal body | No flags |
| RA04 | `analyse_bodyContainsNullPointerException_stackTraceFlagAttached` | Body: `"NullPointerException at com.example..."` | `STACK_TRACE_LEAKED` flag present |
| RA05 | `analyse_bodyContainsAtCom_stackTraceFlagAttached` | Body contains `"at com."` | `STACK_TRACE_LEAKED` flag present |
| RA06 | `analyse_bodyContainsSQLException_dbErrorFlagAttached` | Body: `"SQLException: syntax error"` | `DB_ERROR_EXPOSED` flag present |
| RA07 | `analyse_bodyContainsOraError_dbErrorFlagAttached` | Body contains `"ORA-00942"` | `DB_ERROR_EXPOSED` flag present |
| RA08 | `analyse_200OnNullPayload_unexpectedSuccessFlagAttached` | Status 200, payload was `null` on required field | `UNEXPECTED_SUCCESS` flag present |
| RA09 | `analyse_200OnStringToIntField_unexpectedSuccessFlagAttached` | Status 200, string sent to integer field | `UNEXPECTED_SUCCESS` flag present |
| RA10 | `analyse_responseTakes4000ms_slowResponseFlagAttached` | Duration: 4000ms, threshold: 3000ms | `SLOW_RESPONSE_4000ms` flag present |
| RA11 | `analyse_responseTakes2000ms_noSlowFlag` | Duration: 2000ms, threshold: 3000ms | No `SLOW_RESPONSE` flag |
| RA12 | `analyse_200WithEmptyBody_emptySuccessFlagAttached` | Status 200, body: `""` | `EMPTY_SUCCESS_BODY` flag present |
| RA13 | `analyse_400OnInvalidInput_noValidationMissingFlag` | Status 400, clearly invalid input | No `VALIDATION_MISSING` flag |
| RA14 | `analyse_200OnClearlyInvalidInput_validationMissingFlagAttached` | Status 200, 10000-char string on short field | `VALIDATION_MISSING` flag present |
| RA15 | `analyse_multipleIssues_multipleFlags` | Status 500, body contains stack trace | Both `SERVER_ERROR` and `STACK_TRACE_LEAKED` present |

---

### FuzzPayloadGenerator

| ID | Test Name | Input | Expected |
|----|-----------|-------|----------|
| FG01 | `generateForType_integer_nullPayloadIncluded` | Type: `integer` | `null` in payload list |
| FG02 | `generateForType_integer_intMaxIncluded` | Type: `integer` | `Integer.MAX_VALUE` in payload list |
| FG03 | `generateForType_integer_intMinIncluded` | Type: `integer` | `Integer.MIN_VALUE` in payload list |
| FG04 | `generateForType_integer_wrongTypeIncluded` | Type: `integer` | `"not_an_int"` in payload list |
| FG05 | `generateForType_integerWithMinConstraint_belowMinIncluded` | Type: `integer`, min: 1 | `0` in payload list |
| FG06 | `generateForType_integerWithMaxConstraint_aboveMaxIncluded` | Type: `integer`, max: 100 | `101` in payload list |
| FG07 | `generateForType_string_nullPayloadIncluded` | Type: `string` | `null` in payload list |
| FG08 | `generateForType_string_emptyStringIncluded` | Type: `string` | `""` in payload list |
| FG09 | `generateForType_string_sqlInjectionIncluded` | Type: `string` | SQL injection string in payload list |
| FG10 | `generateForType_string_xssPayloadIncluded` | Type: `string` | XSS string in payload list |
| FG11 | `generateForType_string_hugeSringIncluded` | Type: `string` | 10000-char string in payload list |
| FG12 | `generateForType_stringWithMaxLength_aboveMaxLengthIncluded` | Type: `string`, maxLength: 50 | 51-char string in payload list |
| FG13 | `generateForType_boolean_nullIncluded` | Type: `boolean` | `null` in payload list |
| FG14 | `generateForType_boolean_stringTrueIncluded` | Type: `boolean` | `"true"` (string) in payload list |
| FG15 | `generateForType_boolean_intOneIncluded` | Type: `boolean` | `1` (integer) in payload list |
| FG16 | `generateForType_unknownType_staticPayloadsUsed` | Type: `unknown` | Falls back to static string list |

---

### OpenApiParser

| ID | Test Name | Input | Expected |
|----|-----------|-------|----------|
| OP01 | `parse_validPetstoreSpec_correctNumberOfEndpointsExtracted` | Petstore spec | Known number of endpoints returned |
| OP02 | `parse_validSpec_postEndpointHasRequestBodyFields` | Spec with POST /pet | `FuzzTarget` for POST /pet has field map populated |
| OP03 | `parse_validSpec_fieldTypeExtractedCorrectly` | Field declared as `integer` | `FieldSchema.type` equals `"integer"` |
| OP04 | `parse_validSpec_minConstraintExtracted` | Field with `minimum: 1` | `FieldConstraints.minimum` equals `1` |
| OP05 | `parse_validSpec_maxLengthConstraintExtracted` | Field with `maxLength: 100` | `FieldConstraints.maxLength` equals `100` |
| OP06 | `parse_validSpec_nullableFieldExtracted` | Field with `nullable: true` | `FieldConstraints.nullable` is `true` |
| OP07 | `parse_specWithGetEndpoint_queryParamsExtracted` | GET endpoint with query params | Query params appear as fields in `FuzzTarget` |
| OP08 | `parse_fieldWithNoSchema_fallbackToStringType` | Field with no type declared | `FieldSchema.type` equals `"string"` |
| OP09 | `parse_localFileSpec_parsedCorrectly` | Local `test-petstore.json` file | Same result as remote URL parse |
| OP10 | `parse_invalidSpecLocation_exceptionThrownGracefully` | Non-existent URL | Exception thrown with descriptive message |

---

### FuzzRunner

| ID | Test Name | Input | Expected |
|----|-----------|-------|----------|
| FR01 | `run_singleFuzzCase_httpRequestFired` | One `FuzzCase` | REST Assured fires one request |
| FR02 | `run_singleFuzzCase_statusCodeCaptured` | Mock response: 400 | `FuzzResult.statusCode` equals `400` |
| FR03 | `run_singleFuzzCase_responseBodyCaptured` | Mock response body: `"error"` | `FuzzResult.responseBody` equals `"error"` |
| FR04 | `run_singleFuzzCase_durationCaptured` | Any response | `FuzzResult.durationMs` is greater than `0` |
| FR05 | `run_multipleFuzzCases_allResultsReturned` | Ten `FuzzCase` objects | Ten `FuzzResult` objects returned |
| FR06 | `run_requestTimesOut_timeoutResultRecorded` | Timeout configured at 100ms, slow mock server | `FuzzResult` recorded with timeout flag |

---

### AllureReporter

| ID | Test Name | Input | Expected |
|----|-----------|-------|----------|
| AR01 | `write_singleResult_resultFileWrittenToOutputDir` | One `FuzzResult`, output dir configured | One `.json` file written to output dir |
| AR02 | `write_flaggedResult_resultMarkedFailed` | `FuzzResult` with `SERVER_ERROR` flag | Allure result status is `FAILED` |
| AR03 | `write_flaggedResult_responseBodyAttached` | `FuzzResult` with flag and response body | Attachment present in result file |
| AR04 | `write_cleanResult_resultMarkedPassed` | `FuzzResult` with no flags | Allure result status is `PASSED` |
| AR05 | `write_multipleResults_allFilesWritten` | Ten `FuzzResult` objects | Ten result files in output dir |

---

## 7. Coverage Targets

| Layer | Target |
|-------|--------|
| `ResponseAnalyser` | 100% — every flag type must have at least one test |
| `FuzzPayloadGenerator` | 100% — every field type and every constraint type must be tested |
| `OpenApiParser` | 90% — core extraction paths fully covered, edge cases where possible |
| `FuzzRunner` | 80% — core execution path and timeout handling covered |
| `AllureReporter` | 80% — result writing and status mapping covered |
| Overall | 85% line coverage minimum |

---

## 8. Known Limitations

| ID | Limitation |
|----|------------|
| KL1 | Integration tests require a live internet connection to reach Petstore v3 — excluded from CI by default via `@Tag("integration")` |
| KL2 | `FuzzRunner` unit tests use Mockito to mock REST Assured — the actual HTTP behaviour is only validated in integration tests |
| KL3 | Allure report rendering is verified visually, not via automated assertion |
| KL4 | Sequence fuzzing (out-of-order endpoint calls) is tested manually for v0.3 — automated sequence fuzz tests are a future task |
