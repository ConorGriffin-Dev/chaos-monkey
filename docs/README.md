# Chaos Monkey — Documentation

This folder contains all design and planning documentation for the Chaos Monkey project.
Each document is written before the corresponding code, following a documentation-first approach.

---

## Structure

| Folder | Document | Description |
|--------|----------|-------------|
| `01-system-design/` | `system-design-document.md` | Full system design — goals, components, data models, payload strategy, response analysis, CLI interface |
| `01-system-design/diagrams/` | `system-context.puml` | C4 system context — actors, external systems, data flows |
| `01-system-design/diagrams/` | `component.puml` | Component breakdown — Spring beans, responsibilities, dependencies |
| `01-system-design/diagrams/` | `class.puml` | Class diagram — full model and service layer |
| `01-system-design/diagrams/` | `sequence-fuzz-run.puml` | Sequence diagram — full fuzz run from CLI input to Allure report |
| `01-system-design/diagrams/` | `sequence-openapi-parse.puml` | Sequence diagram — OpenAPI spec parsing internals |
| `02-architecture/` | `architecture-decisions.md` | Architecture Decision Records (ADRs) — every significant decision with rationale |
| `02-architecture/diagrams/` | `architecture-overview.puml` | High-level architecture overview diagram |
| `03-build-roadmap/` | `roadmap.md` | Versioned build plan — v0.1, v0.2, v0.3 with task checklists and definitions of done |
| `04-test-plan/` | `test-plan.md` | Test strategy, test scope, test cases, and coverage targets |
| `05-usage/` | `usage-guide.md` | Installation, CLI reference, usage examples, and report generation |

---

## Diagrams

All diagrams are written in PlantUML (`.puml`). They can be rendered:

- **IntelliJ IDEA** — via the PlantUML Integration plugin
- **VS Code** — via the PlantUML extension
- **Online** — at [plantuml.com/plantuml](https://www.plantuml.com/plantuml)
- **GitHub** — with the [PlantUML Proxy](https://github.com/plantuml/plantuml-server)

---

## Reading Order

If you are new to this project, read in this order:

1. `01-system-design/system-design-document.md` — understand what the tool does and why
2. `02-architecture/architecture-decisions.md` — understand the key technical decisions
3. `03-build-roadmap/roadmap.md` — understand the phased build plan
4. `04-test-plan/test-plan.md` — understand the testing strategy
5. `05-usage/usage-guide.md` — understand how to install and run the tool
