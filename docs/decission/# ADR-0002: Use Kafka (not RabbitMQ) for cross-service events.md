# ADR-0002: Use Kafka (not RabbitMQ) for cross-service events

## Status
Accepted

## Context
`wallet-service`, `loan-service`, and `collateral-service` need to coordinate on events like
`LoanActivationRequested`, `CollateralLocked`, `CollateralLiquidated`, and `FundsDisbursed` — the
backbone of the loan-origination saga described in our domain-invariants discussion. Requirements
that shape the messaging choice:

- **Replay/audit**: a fintech-style system needs to be able to reconstruct "what happened and in
  what order" after the fact — for reconciliation, debugging a saga that got stuck, or answering
  "why is this loan in this state." A message that's gone the moment it's consumed doesn't support
  that.
- **Multiple independent consumers per event**: e.g. `CollateralLiquidated` may need to be consumed
  by `loan-service` (to transition the Loan) and potentially a future audit/reporting service,
  without them competing for the same message.
- **Ordering per aggregate**: events about the same `Loan`/`CollateralPosition` need to be processed
  in order relative to each other, which maps naturally onto partitioning by aggregate id.
- **This is a portfolio project aimed at the EU fintech job market**, where Kafka is the de facto
  standard for event-driven backends — demonstrable Kafka experience (consumer groups, partitioning,
  offset management, schema evolution) is more directly relevant to target roles than RabbitMQ
  experience.

## Decision
Use **Kafka** as the backbone for all cross-service domain events in PayGuard (wallet-service,
loan-service, collateral-service), with topics partitioned by aggregate id (e.g. `loanId`,
`collateralPositionId`) to preserve per-aggregate ordering.

## Consequences

**Positive**
- Events are retained on the log (per configured retention), so replay for debugging, audit, or
  rebuilding a read model later is possible without extra plumbing.
- Consumer groups let each service (and any future service) read the same event stream
  independently, at its own pace, without competing consumption.
- Partitioning by aggregate id gives ordering guarantees exactly where the saga needs them, without
  needing a global lock.
- Directly demonstrates the event-streaming skill set most relevant to the target job market.

**Negative**
- Kafka is heavier to operate than RabbitMQ (ZooKeeper/KRaft, broker configuration, topic/partition
  planning) — meaningful overhead for a project run by a small team.
- Request/reply-style interactions (if any service ever needs a synchronous "ask and wait") are
  awkward on Kafka compared to RabbitMQ's native RPC-style patterns; those cases should go over
  gRPC instead, not be forced onto Kafka.
- No native per-message TTL/priority queues the way RabbitMQ has; anything needing that has to be
  built on top (e.g. a delay via a scheduled retry topic).

## Alternatives considered
- **RabbitMQ** — better fit if the system were purely command/task-queue style with no replay need,
  and operationally simpler. Rejected because audit/replay and multi-consumer fan-out are core
  requirements here, not edge cases.
- **Direct synchronous calls (gRPC/REST) for saga coordination** — rejected as the primary
  mechanism because it couples services' availability together and gives up the event log; gRPC is
  still used for API-gateway-to-service calls where synchronous request/response is the right
  shape, just not for saga coordination.