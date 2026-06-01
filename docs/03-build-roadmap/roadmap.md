# Chaos Monkey — Build Roadmap

**Author:** Conor Griffin  
**Version:** 2.0  
**Date:** June 2026  
**Status:** Living document

---

## Overview

Chaos Monkey is built in versioned milestones. Each version is a fully working,
demonstrable tool - not a partial state - so there's always something runnable
and useful at the end of every version. Versions 0.1–0.4 are complete; 0.5
onward is planned work, ordered by value and dependency. This is a statement of
intent, not a delivery schedule - no dates are promised.

---

## Version 0.1: Static Fuzzer ✅ DONE

**Goal:** Fire a hardcoded list of fuzz payloads at a target endpoint and log
everything that breaks. No OpenAPI parsing, no Allure - just results.

Delivered: `FuzzResult`/`FuzzConfig`/`FieldConstraints` records, static
`FuzzPayloadLibrary` (`payloads/fuzz-strings.json`), `FuzzRunner` over REST
Assured, `ResponseAnalyser` with all seven flag types, `--url` CLI via Spring
Shell, unit tests for analyser + library. Demo against JSONPlaceholder.

---

## Version 0.2: OpenAPI Parser ✅ DONE

**Goal:** Replace the hardcoded endpoint with full OpenAPI 3.x parsing -
auto-discover every endpoint and field, generate schema-aware payloads per field
from declared type and constraints.

Delivered: `FuzzTarget`/`FieldSchema`/`FuzzCase` records, `OpenApiParser`
(`io.swagger.parser.v3`, JSON/YAML, remote/local, `$ref` resolution),
`FuzzPayloadGenerator` with per-type and constraint-aware boundary generation,
`--spec` CLI argument, parser + generator unit tests. Demo against Swagger
Petstore v3.

---

## Version 0.3: Allure Reporting + CLI Polish + Open Source ✅ DONE

**Goal:** Every run produces a full Allure HTML report; the CLI is complete and
documented; the project is open-sourced with a professional README and CI.

Delivered: `AllureReporter` (result-per-`FuzzResult`, flag labels, status
mapping, response-body attachments), `--output`/`--timeout`/`--flag-only`/
`--dry-run` flags, console summary, README + LICENSE (MIT) + CONTRIBUTING,
GitHub Actions CI (build + test, integration tests excluded by default),
`AllureReporter` tests + Petstore integration test.

---

## Version 0.4: Auth + Real-World Hardening ✅ DONE

**Goal:** Fuzz behind authentication and handle real-world API shapes the
Petstore demo didn't exercise.

Delivered:
- `--bearer-token` and `--auth-header` flags - inject an Authorization header on
  every request so protected endpoints can be fuzzed behind a login wall.
- Path-parameter substitution - `{id}` segments resolved (fuzzed value or a
  type-aware placeholder) instead of failing on the literal `{`.
- Header-parameter routing - params declared `in: header` fired as headers, not
  query params.
- Multipart endpoint tagging - `multipart/form-data` endpoints marked
  `MULTIPART_UNSUPPORTED` rather than miscounted as server errors.

Validated against a peer's Spring Boot app (22 endpoints, 1,045 payloads, run
behind a JWT), which surfaced a real path-parameter validation gap. See the
README case study.

---

## Version 0.5: Auth Ergonomics

**Goal:** Make authentication practical for CI and shared dev environments,
where a token must never appear on the command line.

### Tasks
- [ ] Read the auth token from the `CHAOS_MONKEY_AUTH_TOKEN` environment variable
  as a fallback when no auth flag is passed. Precedence:
  `--auth-header` > `--bearer-token` > `CHAOS_MONKEY_AUTH_TOKEN`.
- [ ] Banner shows token source (flag vs env) for debuggability.
- [ ] Login/refresh flow - given a login endpoint and credentials, fetch and
  auto-refresh the token so short-lived JWTs don't expire mid-run.
- [ ] Docs: README auth section + usage example + precedence note.

---

## Version 0.6: Extensibility

**Goal:** Let users add scenarios without modifying core classes.

### Tasks
- [ ] Pluggable payload-generation strategies - a strategy interface so custom
  generators can be registered without editing `FuzzPayloadGenerator`.
- [ ] Pluggable assertion/detection strategies - the same for `ResponseAnalyser`
  flag rules.
- [ ] Custom payload injection via CLI/config - supply your own payload lists.
- [ ] Refine `UNEXPECTED_SUCCESS` / `VALIDATION_MISSING` heuristics to suppress
  false positives on endpoints that don't consume the fuzzed field
  (e.g. GET with no matching parameter).

---

## Version 0.7: Stateful Sequence Testing

**Goal:** Move beyond per-field fuzzing to fuzzing sequences of operations —
the known weak area of the current stateless approach.

### Tasks
- [ ] Chain operations using endpoint relationships from the spec (e.g. POST a
  resource, capture its ID, then fuzz GET/PUT/DELETE against it).
- [ ] Out-of-order sequence fuzzing - DELETE before POST, GET non-existent IDs,
  operate on resources created then removed.
- [ ] Detect state-dependent failures the stateless per-field approach can't
  reach.

The most research-heavy direction, aligning the tool with stateful fuzzers like
RESTler and EvoMaster.

---

## Milestone Summary

| Version | Focus | Outcome |
|---------|-------|---------|
| v0.1 | Static fuzzer | Working tool, console output, real findings |
| v0.2 | OpenAPI parser | Schema-aware fuzzing, auto-discovers endpoints |
| v0.3 | Allure + CLI + OSS | Professional report, complete CLI, GitHub ready |
| v0.4 | Auth + hardening | Fuzzes behind auth; path/header/multipart handling; field-validated on a real app |
| v0.5 | Auth ergonomics | Env-var token, login/refresh flow |
| v0.6 | Extensibility | Pluggable payload + assertion strategies |
| v0.7 | Stateful testing | Sequence-aware fuzzing |

---

## Backlog (Unscheduled)

Genuinely-someday items, not committed to a version:

| Feature | Description |
|---------|-------------|
| Rate-limit handling | Detect `429`, respect `Retry-After`, configurable delay between requests |
| Swagger 2.0 support | Extend the parser to handle legacy Swagger 2.0 specs |
| YAML config file | Define all CLI arguments in a `chaos-monkey.yml` |
| Parallel execution | Fire requests concurrently with a configurable thread pool |
| Spec-less discovery | Crawl/probe mode, or import from HAR / Postman collections, for APIs with no published spec |
| Docker image | Package as a Docker image for zero-install usage |