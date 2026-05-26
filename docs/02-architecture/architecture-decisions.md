# Chaos Monkey — Architecture Decision Records

**Author:** Conor Griffin 
**Version:** 1.0 
**Date:** May 2026 
**Status:** Draft

---

## Overview

This document records the significant architectural decisions made during the design of Chaos
Monkey. Each decision is documented with its context, the options considered, the decision made,
and the rationale behind it. This format is known as an Architecture Decision Record (ADR).

---

## ADR-001: Language — Java 21

### Context
The tool needed a primary language. The realistic options were Java, Python, and JavaScript/Node.

### Options Considered

| Option | Pros | Cons |
|--------|------|------|
| Java 21 | REST Assured is best-in-class for Java API testing. Strong typing catches payload generation bugs early. Directly targets SDET/QA Engineer roles at enterprise companies. Allure has first-class Java support. | More verbose than Python for quick scripting. Slower to prototype. |
| Python | Fastest to prototype. `requests` library is simple. Large fuzzing ecosystem (Schemathesis). | Weaker typing. Less relevant for enterprise Java QA roles. REST Assured not available. |
| JavaScript/Node | Familiar if already working in a JS stack. | No strong API fuzzing ecosystem. Playwright and Cypress are UI tools, not API fuzzers. Fighting the language for this use case. |

### Decision
**Java 21**

### Rationale
REST Assured is the dominant Java API testing library and is already on the CV from DAFM
professional experience. Java's strong typing makes the payload generation logic safer and more
explicit — a wrong type in a fuzz payload is a compile error, not a runtime surprise. The target
audience for this tool's career value is enterprise QA/SDET roles where Java is the standard.
Java 21 specifically is chosen for records (used throughout the model layer), sealed classes
(future use), and pattern matching — modern Java features that keep the code clean without
requiring Spring.

---

## ADR-002: Spring Boot + Spring Shell

### Context
The application needs a structure for wiring components together and handling CLI argument
parsing. Given my background with Spring Boot, the question is whether to use
it here or opt for plain Java.

### Options Considered

| Option | Pros | Cons |
|--------|------|------|
| Spring Boot + Spring Shell | Familiar from ScriptDojo and DAFM. DI handles component wiring cleanly. Spring Shell purpose-built for Java CLIs. @Service/@Component maps directly onto the designed component structure. Strong testing infrastructure already known. | Startup overhead vs plain Java. Heavier than strictly necessary for a CLI. |
| Plain Java + Picocli | Lightweight. Fast startup. No framework overhead. | Manual component wiring gets messy as the codebase grows. Picocli is a new dependency with no career benefit over Spring. Loses familiar testing infrastructure. |

### Decision
**Spring Boot + Spring Shell**

### Rationale
The component structure already designed — CLI, Parser, Generator, Runner, Analyser,
Reporter — maps directly onto Spring beans. Wiring FuzzConfig through every component
manually is exactly the problem Spring DI solves. Spring Shell handles CLI argument parsing
cleanly without introducing an unfamiliar library. The testing infrastructure (SpringBootTest,
@Autowired, @MockBean) is already well understood from ScriptDojo, meaning productive
testing from day one. The startup overhead is irrelevant for a fuzzing tool where runs take
minutes. Using Spring Boot here deepens an existing skill that is already on the CV rather
than introducing Picocli for no career benefit.

---

## ADR-003: REST Assured for HTTP Execution

### Context
The runner component needs a library to fire HTTP requests and capture responses cleanly.

### Options Considered

| Option | Pros | Cons |
|--------|------|------|
| REST Assured | Industry-standard Java API testing library. Fluent DSL. Excellent response extraction. Already used professionally at DAFM. | Slightly more setup than raw HttpClient for non-test contexts. |
| Java HttpClient (built-in) | No dependency. Part of Java 11+. | Verbose. No fluent DSL. Response handling is boilerplate-heavy. |
| OkHttp | Lightweight. Good for production HTTP clients. | Not designed for testing scenarios. Less expressive for response assertion. |
| Apache HttpClient | Mature. Well-documented. | Very verbose. Older API design. |

### Decision
**REST Assured**

### Rationale
REST Assured is the correct tool for this job. It is built specifically for API testing, has a fluent
response extraction API that maps cleanly to the `FuzzResult` model, and is already on the CV
from professional use at DAFM. Using it here deepens that existing skill rather than introducing
a new HTTP library for no benefit.

---

## ADR-004: Swagger Parser for OpenAPI Parsing

### Context
The parser component needs to read and deserialise OpenAPI 3.x specifications into a usable
object model.

### Options Considered

| Option | Pros | Cons |
|--------|------|------|
| `io.swagger.parser.v3` (Swagger Parser) | Official Swagger/OpenAPI library. Handles JSON and YAML. Supports remote URLs and local files. Produces a full OpenAPI object model. Well maintained. | Brings in transitive dependencies. |
| Manual JSON parsing (Jackson) | No additional dependency beyond Jackson (already present). Full control. | Enormous amount of work. OpenAPI specs are complex — writing a full parser from scratch is not justified. |
| `openapi4j` | Alternative OpenAPI library. | Less mature. Smaller community. Less documentation. |

### Decision
**`io.swagger.parser.v3` — Swagger Parser**

### Rationale
Writing an OpenAPI parser from scratch would be weeks of work and hundreds of edge cases.
The Swagger Parser is the canonical library for this, maintained by the same organisation that
defines the OpenAPI specification. It handles YAML and JSON, remote and local specs, and
`$ref` resolution automatically. There is no good reason not to use it.

---

## ADR-005: Allure for Reporting

### Context
The tool needs to produce reports that are readable by both the engineer who ran the tool and
anyone they share results with (team lead, engineering team).

### Options Considered

| Option | Pros | Cons |
|--------|------|------|
| Allure | Produces beautiful interactive HTML reports. Industry-recognised in QA teams. First-class Java support. Integrates with JUnit and TestNG. Familiar to QA engineers. | Requires Allure CLI to be installed separately for report generation. |
| Custom HTML reporter | No external dependency. Full control over format. | Significant development effort. Output will look amateur compared to Allure. No added value. |
| CSV / JSON output only | Simple. No dependencies. | Not human-readable without additional tooling. Useless for sharing with non-technical stakeholders. |
| ExtentReports | Good HTML reports. Java support. | Less common in QA teams than Allure. Less community support. |

### Decision
**Allure**

### Rationale
Allure is the standard reporting tool in professional Java QA teams. An interviewer or team lead
who receives an Allure report immediately recognises it as professional output. The interactive
HTML format — with grouped results, attachments, and timeline views — is far more useful than
a CSV dump. The only tradeoff is the separate Allure CLI installation, which is documented
clearly in the README and is a one-time setup step.

---

## ADR-006: Records for the Model Layer

### Context
The model layer (`FuzzTarget`, `FuzzCase`, `FuzzResult`, `FieldSchema`, `FieldConstraints`)
needs to be clean, immutable, and minimal in boilerplate.

### Options Considered

| Option | Pros | Cons |
|--------|------|------|
| Java 21 Records | Immutable by default. Zero boilerplate. `equals()`, `hashCode()`, `toString()` generated automatically. Canonical constructors. | Cannot be subclassed. Cannot have mutable fields (appropriate here). |
| POJOs with Lombok | Familiar from ScriptDojo. `@Builder`, `@Data` reduce boilerplate. | Requires Lombok dependency. Annotation processing adds build complexity. Records are the modern native alternative. |
| Plain POJOs (no Lombok) | No dependencies. | Enormous boilerplate for getters, setters, equals, hashCode, toString. |

### Decision
**Java 21 Records**

### Rationale
The model objects in Chaos Monkey are pure data carriers — they hold values, are passed
between components, and are never mutated after creation. This is exactly the use case Java
records were designed for. Using records here demonstrates awareness of modern Java features
and keeps the model layer clean without any annotation processing overhead. Lombok is not
needed when the language itself provides the same capability natively.

---

## ADR-007: Single Repo Structure

### Context
The project needs a repository structure that keeps the codebase and documentation together
without either cluttering the other.

### Options Considered

| Option | Pros | Cons |
|--------|------|------|
| Single repo — `src/` + `docs/` at root | One repo to clone. Docs version with code. GitHub renders `docs/` markdown natively. Standard open-source layout. | Slightly more complex root directory. |
| Two separate repos | Clean separation. | Docs can fall out of sync with code. Two repos to manage. Adds friction for contributors. |
| Docs inside `src/` | N/A | Makes no sense structurally. |

### Decision
**Single repo with `src/` for code and `docs/` for documentation**

### Rationale
Keeping docs in the same repo as code means they version together — a change to the
component structure is accompanied by a change to the component diagram in the same commit.
GitHub renders markdown files natively, so the `docs/` folder is browseable directly on GitHub
without any additional tooling. This is the standard structure for professional open-source
Java tooling projects.

---

## ADR-008: Target API — Swagger Petstore v3

### Context
The tool needs a public API to run against for development, testing, and demonstration purposes.

### Options Considered

| Option | Pros | Cons |
|--------|------|------|
| Swagger Petstore v3 | Official OpenAPI demo API. Has a public OpenAPI spec. Covers multiple HTTP methods and field types. Deliberately designed as a demo target. Safe to fuzz. | Limited real-world complexity. |
| JSONPlaceholder | Simple. Public. No auth required. | No OpenAPI spec available — limits schema-aware fuzzing demonstration. |
| OWASP Juice Shop | Deliberately vulnerable. Real security findings guaranteed. | Requires local Docker deployment. More complex setup for a demo. |
| A real production API | Most realistic findings. | Ethical and legal concerns. Rate limiting. Potential damage. Never appropriate without explicit permission. |

### Decision
**Swagger Petstore v3 as primary target. JSONPlaceholder as static-fuzz fallback.**

### Rationale
Petstore v3 is the canonical OpenAPI demo target — it exists specifically to be used by tooling
that consumes OpenAPI specs. It has a well-structured spec covering `string`, `integer`, `array`,
and `object` field types across multiple endpoints. Fuzzing it is ethical, expected, and produces
meaningful demonstration output. JSONPlaceholder serves as the fallback target for static fuzz
mode (no spec required), giving a second demo scenario without additional infrastructure.
