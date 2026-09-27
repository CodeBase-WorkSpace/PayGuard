# ADR-0001: Use jOOQ (not JPA) in wallet-service

## Status
Accepted

## Context
`wallet-service` owns the double-entry ledger for PayGuard: `Wallet`, `Transaction`, and
`LedgerEntry`. The invariants that matter here are financial-correctness invariants, not
convenience ones:

- Every `Transaction` must be written as a single atomic set of balanced `LedgerEntry` rows
  (sum of signed amounts = 0), computed inside the domain, not left to an ORM's dirty-checking
  to decide what gets flushed and when.
- `LedgerEntry` rows are append-only. Nothing about this service ever needs "load an entity,
  mutate a field, let the ORM figure out the UPDATE" — that whole class of behavior is actively
  something we want to *rule out*, not enable.
- Balance is derived by folding over entries (or snapshot + replay), which means the persistence
  layer is doing deliberate aggregation/window queries, not simple entity graph loading.
- Optimistic locking and idempotency checks need to be explicit, single round-trip SQL statements
  we can reason about precisely (`UPDATE ... WHERE version = ?`), not something implicit that an
  ORM's session/`@Version` machinery does on our behalf, sometimes with lazy-loading or flush-order
  surprises attached.

The other PayGuard services (`loan-service`, `collateral-service`) are closer to conventional
CRUD-with-workflow domains, where JPA's entity graph, cascades, and repository conventions save
real time and there's no analogous "invariant that ORM implicitness could quietly violate."

## Decision
Use **jOOQ** for `wallet-service` only, paired with Spring Modulith to keep the module's
persistence code physically isolated from the rest of the codebase. Use **JPA/Hibernate** in
`loan-service` and `collateral-service`, where the productivity win outweighs the precision cost.

This is a deliberate polyglot-persistence choice per service, not an inconsistency — each service
picks the tool that matches how strict its own write-path invariants are.

## Consequences

**Positive**
- Every SQL statement wallet-service issues is explicit and type-checked at compile time (jOOQ's
  generated DSL), so there's no hidden N+1, no surprise flush, no lazy-loading trap around
  money-moving code.
- The double-entry balance invariant is enforced in one place we control end to end: the domain
  builds the `LedgerEntry` list, and the jOOQ-backed repository adapter writes exactly that, in one
  statement/batch, inside one DB transaction.
- Easier to write and reason about the aggregation queries balance-derivation needs (window
  functions, running totals) than in JPQL/Criteria.

**Negative**
- More boilerplate than JPA for the parts of wallet-service that *are* simple CRUD (e.g. reading a
  Wallet by id) — jOOQ doesn't give us repository-pattern conveniences for free.
- Two persistence stacks in one codebase means two things to onboard a new contributor on, and two
  sets of testing conventions (jOOQ's generated code vs JPA entities/repositories).
- Schema-first workflow: jOOQ code generation depends on the Oracle schema already existing/being
  migrated, so migration-then-generate has to be a disciplined step in the build, not an afterthought.

## Alternatives considered
- **JPA/Hibernate everywhere** — rejected for wallet-service specifically because the implicit
  dirty-checking and cascade behavior is exactly the kind of "invisible mutation path" the
  double-entry invariant needs to not exist.
- **Plain JDBC / Spring `JdbcTemplate`** — rejected because it gives up jOOQ's compile-time-checked
  DSL and query composition for no real benefit over jOOQ here.
- **Event sourcing the ledger itself** (rather than jOOQ over relational tables) — considered
  heavier than needed for the current scope; revisit if audit/replay requirements grow.