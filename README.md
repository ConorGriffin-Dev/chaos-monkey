# Chaos Monkey — Automated REST API Fuzzer

Chaos Monkey is a Java 21 CLI tool that takes an OpenAPI 3.x specification, generates schema-aware fuzz payloads for every field on every endpoint, fires them at a live API via REST Assured, and produces a full Allure HTML report detailing every server error, stack trace leak, unexpected success, and performance outlier it finds.
Built out of real frustration with manual negative-path API testing on a national government platform — where remembering edge cases was the tester's problem. Chaos Monkey makes it nobody's problem.

---

## Prerequisites

| Requirement | Version | Check |
|-------------|---------|-------|
| Java JDK | 21+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Allure CLI | Latest | `allure --version` |
| Git | Any | `git --version` |

**Install Allure CLI:**

macOS: `brew install allure`  
Windows: `scoop install allure`  
Linux: download from [allure releases](https://github.com/allure-framework/allure2/releases) and add to PATH

---

## Installation

```bash
git clone https://github.com/YoungGriff11/chaos-monkey.git
cd chaos-monkey
mvn clean package -DskipTests
```

Verify:
```bash
java -jar target/chaos-monkey.jar --help
```

---

## Usage

### Spec-aware fuzz run (recommended)
```bash
java -jar target/chaos-monkey.jar fuzz \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json
```

### Custom output directory
```bash
java -jar target/chaos-monkey.jar fuzz \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json \
  --output ./my-fuzz-results
```

### Custom timeout
```bash
java -jar target/chaos-monkey.jar fuzz \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json \
  --timeout 5000
```

### Flag-only mode (large APIs — filters report to findings only)
```bash
java -jar target/chaos-monkey.jar fuzz \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json \
  --flag-only
```

### Dry run (preview payloads without firing requests)
```bash
java -jar target/chaos-monkey.jar fuzz \
  --url https://petstore3.swagger.io/api/v3 \
  --spec https://petstore3.swagger.io/api/v3/openapi.json \
  --dry-run
```

### Static fuzz mode (no spec required)
```bash
java -jar target/chaos-monkey.jar fuzz \
  --url https://jsonplaceholder.typicode.com
```

---

## Generating the Report

```bash
allure serve ./allure-results
```

Or generate a static folder:
```bash
allure generate ./allure-results --output ./allure-report --clean
```

---

## Sample Report

![Allure Report](docs/01-system-design/diagrams/allure-report-screenshot.png)

*3343 fuzz cases across 60 suites against Swagger Petstore v3. Run duration: 1h 05m.*

---

## Findings from Petstore v3

Running against `https://petstore3.swagger.io/api/v3`:

- **SERVER_ERROR** on write endpoints — `POST /user`, `POST /pet`, `POST /store/order`, `PUT /pet`, `DELETE /pet/{petId}` all return 500s under malformed input. The server crashes rather than validates.
- **UNEXPECTED_SUCCESS** on `GET /user/login` — sending `null` credentials returns a token. The endpoint accepts inputs it should reject.
- **VALIDATION_MISSING** on `GET /user/login` — oversized strings on the username and password fields are accepted with a 200 rather than a 400.

> **Note:** Petstore v3 is a shared public demo server with no persistent state. Exact counts vary between runs (a run producing ~975 requests may flag anywhere from 487 to 506 results depending on server load). The pattern of findings is stable; the numbers are not.

---

## Case Study — Real-World App (Currently)

Beyond the Petstore demo, Chaos Monkey was run against **Currently**, a peer's
Spring Boot energy-tracking API (with the author's permission), to test it
against an app it had never seen.

The target shipped no OpenAPI spec, so springdoc was added locally to publish
one. Chaos Monkey then discovered **22 endpoints** automatically and fired
**1,045 schema-aware payloads** — running with a valid JWT so it exercised the
authenticated endpoints rather than bouncing off `401`s. The run targeted a
local instance backed by a throwaway database.

**Headline finding:** a consistent input-validation gap — roughly **30 endpoints
returned `500 Internal Server Error` instead of `400 Bad Request`** when a path
parameter couldn't bind to its expected type. Examples:

- `DELETE /api/users/me/rooms/not_an_int` — non-integer ID
- `PUT /api/users/me/appliances/3.14` — decimal where an integer ID is expected
- `DELETE /api/vault/files/9999999999` — integer overflow
- `PUT /api/users/me/rooms/` — write to a collection root with no ID

The root cause was the same each time: a path variable fails to bind, Spring
throws, and the request falls through to the generic 500 handler instead of
being caught as a client error.

**What held up:** every `500` returned a clean error envelope with **no stack
traces and no database errors leaked**, the JWT auth wall **rejected every
malformed payload** against protected endpoints, and no write endpoint silently
accepted invalid data. The tool also correctly **categorised its own
non-findings** — separating genuine errors from multipart endpoints it can't
fuzz and from public read-only endpoints — so the real finding wasn't buried in
noise.

This run drove two tool improvements now in the codebase: **authentication
support** (`--bearer-token` / `--auth-header`) to fuzz behind a login wall, and
**multipart endpoint tagging** so file-upload endpoints aren't miscounted as
server errors.

---

## Running the Tests

```bash
mvn test
```

101 tests, 0 failures. Integration tests (which fire real requests at Petstore) are tagged `@Tag("integration")` and excluded from the standard build. To run them:

```bash
mvn test -Dgroups=integration
```

---

## CLI Reference

| Argument | Required | Default | Description |
|----------|----------|---------|-------------|
| `--url` | Yes | — | Base URL of the target API |
| `--spec` | No | — | Path or URL to OpenAPI 3.x spec. Omit for static fuzz mode |
| `--output` | No | `./allure-results` | Output directory for Allure result files |
| `--timeout` | No | `10000` | Request timeout in milliseconds |
| `--flag-only` | No | `false` | Write only flagged results to the report |
| `--dry-run` | No | `false` | Parse and generate payloads without firing requests |
| `--bearer-token` | No | — | JWT/token sent as `Authorization: Bearer <token>` on every request |
| `--auth-header` | No | — | Full `Authorization` header value (e.g. `ApiKey xyz`). Overrides `--bearer-token` |
---

## Why Chaos Monkey

Most API fuzzing tools are either security scanners (Burp Suite) or require significant configuration to produce readable output. Chaos Monkey is:

- **Java-first** — built with REST Assured and Spring Boot, the stack QA engineers in enterprise Java teams already use
- **Allure-native** — every run produces a report QA teams already know how to read
- **QA-audience** — built to find data integrity and error handling gaps, not to exploit vulnerabilities

---

## Documentation

Full design documentation lives in `docs/`:

- `docs/01-system-design/` — system design document and diagrams
- `docs/02-architecture/` — architecture decision records
- `docs/03-build-roadmap/` — versioned build roadmap
- `docs/04-test-plan/` — full test plan with test case IDs
- `docs/05-usage/` — detailed usage guide

---

## License

MIT — see [LICENSE](LICENSE)
