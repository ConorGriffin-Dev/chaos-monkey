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

## Running the Tests

```bash
mvn test
```

99 tests, 0 failures. Integration tests (which fire real requests at Petstore) are tagged `@Tag("integration")` and excluded from the standard build. To run them:

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

---

## Why Chaos Monkey

Most API fuzzing tools are either security scanners (Burp Suite), Python-first (Schemathesis), or require significant configuration to produce readable output. Chaos Monkey is:

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