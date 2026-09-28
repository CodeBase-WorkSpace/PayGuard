# ADR-0003: Adopt DDD with Hexagonal Architecture across PayGuard

## Status
Accepted

## Context
PayGuard's three services (wallet, loan, collateral) each protect financial-correctness
invariants that must hold regardless of which framework, database, or transport touches them —
the double-entry balance rule, the LTV/liquidation threshold, the loan state machine. A few
concrete pressures shaped this decision:

- **Polyglot persistence per service** (JOOQ in wallet-service, JPA in loan-service and
  collateral-service — see ADR-0001) means the domain model cannot afford to leak persistence
  concerns into itself; if it did, each service's domain layer would look and behave differently
  just because of a technology choice.
- **Invariants must be enforced in one place**, inside the aggregate, not re-implemented at every
  entry point (REST controller, Kafka consumer, gRPC handler) that can trigger a mutation. A
  hexagonal boundary is what makes "the domain doesn't know or care who's calling it" actually true.
- **Testability**: domain logic (state transitions, LTV math, balance invariants) needs to be unit
  testable with zero infrastructure — no Testcontainers, no Spring context — which only works if
  the domain has no framework/persistence dependencies to begin with.
- **This is a portfolio project built to demonstrate depth in backend architecture** for the EU
  Java market; DDD + hexagonal is the architecture pattern most directly aligned with a
  depth-over-breadth, strong-opinions-on-architecture positioning.

## Decision
Structure every PayGuard service as **DDD tactical patterns (Aggregates, Value Objects, Domain
Events) inside a Hexagonal (Ports & Adapters) boundary**:

- `domain` package: aggregates, value objects, domain services — no Spring, no jOOQ/JPA, no Kafka
  imports.
- `application` package: use cases / orchestration, defining outbound ports (repository
  interfaces, event-publisher interfaces) that the domain layer's use cases depend on.
- `adapter` package(s): inbound (REST/gRPC controllers, Kafka consumers) and outbound (jOOQ/JPA
  repository implementations, Kafka producers) — these depend inward on `application`/`domain`,
  never the reverse.

CQRS is deliberately **not** adopted for now (per current architecture decisions) — the
added complexity of separate read/write models isn't justified yet at this project's scale;
this can be revisited per-service if a specific read pattern demands it later.

## Consequences

**Positive**
- Domain invariants (e.g. `Transaction.post()`'s balance check, `Loan`'s state machine, `
  CollateralPosition`'s LTV guard) are unit-testable in plain JUnit, no framework bootstrap needed.
- Swapping jOOQ for JPA (or vice versa) in a given service is an adapter-level change; the domain
  and application layers are untouched.
- Consistent shape across all three services makes the codebase easier to onboard teammates into
  and easier to narrate in an interview — the pattern doesn't change even though the persistence
  tech does.

**Negative**
- More upfront structure/ceremony (ports, adapters, mapping between domain objects and
  persistence records) than a simpler layered or transaction-script approach — slower to build the
  first version of any given feature.
- Risk of over-engineering small, genuinely CRUD-like slices (e.g. a trivial lookup endpoint)
  with the full port/adapter ceremony when a simpler pass-through would do; needs judgment per
  use case rather than blind consistency.
- No CQRS means read-heavy queries (e.g. a dashboard needing joined data across aggregates) will
  go through the same write-side domain model for now, which may need revisiting if query
  complexity grows.

## Alternatives considered
- **Simple layered architecture (Controller → Service → Repository, anemic domain model)** —
  rejected because it tends to let invariant-checking logic leak into service classes and get
  duplicated or forgotten at a second entry point.
- **Full CQRS + Event Sourcing from the start** — rejected as more complexity than the current
  scope justifies; the append-only ledger in wallet-service already gets much of the audit benefit
  without a full event-sourced write model everywhere.