# Contributing to Chaos Monkey

## Prerequisites

- Java JDK 21+
- Maven 3.8+
- Allure CLI (for report generation only — not required to build or test)

## Build

```bash
mvn clean package -DskipTests
```

## Running Tests

```bash
mvn test
```

99 unit tests. All should pass with no network connection required.

Integration tests fire real HTTP requests at Swagger Petstore v3 and require a live internet connection. They are tagged `@Tag("integration")` and excluded from the standard build. Run them explicitly with:

```bash
mvn test -Dgroups=integration
```

Do not include integration tests in a PR without noting that they require internet access.

## Test Naming Convention

All test methods follow this pattern:
methodName_stateUnderTest_expectedBehaviour()

Examples:
- `analyse_500Response_serverErrorFlagAttached()`
- `generateForType_integerWithMaxConstraint_aboveMaxPayloadIncluded()`
- `parse_validPetstoreSpec_allEndpointsExtracted()`

Stick to this convention — it makes the test output self-documenting.

## Model Layer Note

All model classes (`FuzzResult`, `FuzzTarget`, `FuzzCase`, `FieldSchema`, `FieldConstraints`, `FuzzConfig`) are immutable Java 21 records. Any operation that appears to "modify" a result must return a new instance. This is what caused the stale-flags bug in v0.2 — do not repeat it.

## Non-Goals

Chaos Monkey is not a security scanner. It does not exploit vulnerabilities. It does not support WebSocket, GraphQL, gRPC, binary file uploads, or full OAuth/SSO flows. Keep contributions within scope.