# PayGuard Identity Service Architecture

**Scope:** `payguard-identity-service`\
**Root package:** `dev.amg.payguard.identity`\
**Status:** Target architecture; implementation and migration in progress\
**Last updated:** 2026-10-10

> Identity separates authentication integration, access policy, customer identity,
> verification, and registration. These responsibilities do not all have the same
> owner, and successful verification is not permission to execute a financial action.

## Contents

1. [Document scope and implementation status](#1-document-scope-and-implementation-status)
2. [Architectural decisions](#2-architectural-decisions)
3. [Module ownership](#3-module-ownership)
4. [Package structure and layer responsibilities](#4-package-structure-and-layer-responsibilities)
5. [Module contracts and dependency rules](#5-module-contracts-and-dependency-rules)
6. [Identity model and typed identifiers](#6-identity-model-and-typed-identifiers)
7. [Authentication and authorization boundaries](#7-authentication-and-authorization-boundaries)
8. [Verification model and state transitions](#8-verification-model-and-state-transitions)
9. [Application workflows](#9-application-workflows)
10. [Persistence, transactions, and concurrency](#10-persistence-transactions-and-concurrency)
11. [Security, privacy, and operations](#11-security-privacy-and-operations)
12. [Testing and architectural enforcement](#12-testing-and-architectural-enforcement)
13. [Migration and implementation order](#13-migration-and-implementation-order)
14. [Open decisions and extraction criteria](#14-open-decisions-and-extraction-criteria)
15. [References](#15-references)

## 1. Document scope and implementation status

This document is the implementation guide for Identity, not a description of the
entire PayGuard platform. Other services are mentioned only to define Identity's
boundaries. It contains no claim about Revolut's or Stripe's internal architecture.

The Verification baseline was inspected in the current working tree for this
refactoring. Other modules remain target responsibilities unless stated otherwise.
Update this file when implementation changes.

| Area | Current implementation status |
| --- | --- |
| OTP aggregate | Complete immutable-snapshot transitions retained; final success/failure and exception-safety regression tests pass. |
| Typed identifiers | `issue()`/`restore()`, use cases, audit, delivery, and repository use `OtpId`/`CustomerId`; inbound and persistence adapters parse strings. The inverted prefix guard was corrected without changing the specified formats or equality. |
| Package migration | Verification's packages, imports, adapter wiring, and test packages corrected. Existing IDs remain in `customer.domain.value_object`; final exported-ID ownership is still open. |
| Required outbound contracts | `OtpRepository` in Domain; delivery, audit, request quota, and trusted TOTP verification in Application. |
| Step-up tokens | Retired by explicit developer decision: issuance, Redis adapter, consumption endpoint, and token TTL removed. Verification returns a completion result, not an authorization credential. |
| Persistence | PostgreSQL JPA adapters preserve prefixed strings and UUID audit IDs. Row locks serialize verification; durable scope rows and a partial unique index serialize issuance. Historical Oracle V2 still refuses unmapped populated legacy tables. |
| Keycloak, access/customer/registration workflows | Architectural direction only; not implemented by this refactoring. |
| Concurrency, durable evidence, idempotency | Still unresolved; no distributed single-use or exactly-once claim. |


**Established decisions** below summarize the agreed direction. **Proposed
conventions** make that direction implementable but still require review. **Open
decisions** must not be silently treated as completed design.

## 2. Architectural decisions

Identity is one deployable Java/Spring Boot service organized around four business
modules: `access`, `customer`, `verification`, and `registration`. Each module owns
its rules and state. Internal calls normally use Java application contracts, not
HTTP or gRPC between modules in the same process.

The previous names `account` and `onboarding` are replaced by `access` and
`registration`. An Account remains a valid domain concept inside Access; it means
an internal login/access identity, not a financial ledger account.

| Decision                                 | Reason                                                          | Accepted cost                                                  |
|------------------------------------------|-----------------------------------------------------------------|----------------------------------------------------------------|
| Business modules before technical layers | Make responsibility and change ownership visible.               | Contracts, mapping, and boundary tests require effort.         |
| One deployment initially                 | Independent deployment is not yet justified by measured needs.  | Shared releases, resources, and process failure boundary.      |
| Keycloak owns authentication             | Adopt established identity protocols and credential mechanisms. | PayGuard must operate and integrate the provider reliably.     |
| PayGuard owns business authorization     | Permissions alone cannot decide every resource-specific action. | Services must enforce explicit, tested policies.               |
| Typed, prefixed domain identifiers       | Prevent accidental identifier substitution and validate format. | Boundary conversions and data migration are required.          |
| Verification produces bounded evidence   | Challenge completion and business permission are distinct.      | Consumers must validate evidence and their own business rules. |

This structure does not replace DDD, prove that four bounded contexts exist, or
make a layered alternative incorrect. It is a way to express the chosen ownership
boundaries. Package names alone do not enforce them.

A shared process is neither an automatic transaction nor a security isolation
boundary. Complete-state assignment is not distributed atomicity. Those guarantees
require separate mechanisms and tests.

## 3. Module ownership

### 3.1 Access

**Question:** Which internal account is acting, and what access relationships and
permissions does it have?

Access owns internal account identity and access status, external identity links,
tenant memberships, invitations, roles, and permission assignments. Tenant access
administration belongs here initially; a broader tenant/business-organization
lifecycle may justify a separate module later.

Key operations include resolving an external identity, suspending access, managing
membership, and evaluating access permissions. Other modules may request these
operations through Access's public contract.

Access does not store PayGuard login passwords, implement WebAuthn verification,
manage customer profiles, or decide whether funds can be withdrawn. It does not
need a Customer record for every account: an organizational employee can have
membership without being a financial customer.

### 3.2 Customer

**Question:** Which stable PayGuard business customer does this record represent?

Customer owns `CustomerId`, the customer record, its lifecycle, and its explicit
relationship to internal access accounts. Proposed lifecycle terms include
`PENDING`, `ACTIVE`, `SUSPENDED`, and `CLOSED`; the final transition rules require
implementation-specific decisions.

Changing a login provider or email must not regenerate the customer identifier.
Suspending access must not implicitly delete customer or financial records.

Customer owns neither credentials nor OTP state. Legal identity-checking providers
and their detailed workflows are outside this document; Customer may later keep
references to approved outcomes without implementing the checking mechanism.

### 3.3 Verification

**Question:** Has this challenge been completed for its intended subject and purpose?

Verification owns OTP challenges, expiration, attempt accounting, consumption,
invalidation, verification audit entries, and the planned verification-evidence
lifecycle. Its adapters integrate with hashing, delivery, persistence, and any
explicitly approved verification provider.

Verification does not complete registration, activate a customer, or authorize a
financial action on its own. It does not implement every authentication mechanism
supported by Keycloak.

A challenge subject is not necessarily an existing customer. Registration and
account-recovery subjects require an explicit contract before those flows ship.

### 3.4 Registration

**Question:** Which required registration steps have completed, and what remains?

Registration owns the registration identifier, progress, completion criteria,
expiry, and retry/recovery coordination. It requests behavior from Access,
Customer, and Verification through their contracts.

It must not write their tables, modify their aggregates, or bypass their rules.
A verified contact is input to registration completion, not completion itself.
Customer activation and access grants remain decisions of their respective owners.

Give Registration a domain model when its states and rules justify one. Do not
build empty abstractions only to mirror another module's directory structure.

## 4. Package structure and layer responsibilities

The following is a **target structure**, not an assertion that these directories
already exist. The `api` convention is proposed for explicit internal contracts.

```text
payguard-identity-service/
  ARCHITECTURE.md
  src/main/java/dev/amg/payguard/identity/
    access/
      api/
      domain/
      application/
      infrastructure/
        keycloak/
      interfaces/
    customer/
      api/
      domain/
      application/
      infrastructure/
      interfaces/
    verification/
      api/
      domain/
      application/
      infrastructure/
      interfaces/
    registration/
      api/
      domain/
      application/
      infrastructure/
      interfaces/
    shared/
      domain/
        identifier/
```

`api` is not another business layer. It is the small exported surface of a module.
`interfaces` contains transport adapters. They solve different problems.

| Package | Responsibility | Examples | Must not do |
| --- | --- | --- | --- |
| `api` | Publish internal application contracts and immutable results. | Request verification; ensure customer exists. | Expose repositories, builders, provider SDK types, or mutable aggregates. |
| `domain` | Define local business rules and valid state. | `OTP`, `OtpSnapshot`, policies, value objects. | Depend on Spring, SQL implementations, HTTP, or another module's implementation. |
| `application` | Execute a use case and coordinate dependencies. | Load OTP, invoke `verify`, persist the result. | Reimplement attempt counting or lifecycle rules already owned by the aggregate. |
| `infrastructure` | Implement outbound ports and technical integration. | PostgreSQL JPA repository, Vault adapter, Redis adapter, delivery client. | Become the owner of business eligibility rules. |
| `interfaces` | Receive external requests and map results/errors. | REST controllers, gRPC endpoints, message consumers. | Mutate domain state directly or trust unverified caller identity fields. |
| `shared/domain` | Hold deliberately shared, framework-free primitives. | `AggregateRoot`, `ValueObject`. | Contain workflows, repositories, or miscellaneous code without an owner. |

A port is an interface defined on the inside of a dependency boundary. Its adapter
is the external implementation. An aggregate repository contract may live in its
own domain; an application-only delivery or provider port may live in application.
Do not move every interface into Domain just because it is an interface.

Within a module, Interfaces calls the application contract, Application invokes
Domain and ports, and Infrastructure implements ports. Runtime dependency injection
connects them; Domain does not import Infrastructure.

Bootstrap and security wiring are composition concerns, not additional business
modules. Configure module detection intentionally rather than treating every
technical package as a capability.

### Where existing classes belong

| Class or concern | Owner / placement |
| --- | --- |
| `OTP`, `OtpSnapshot` | `verification.domain.model` |
| `OtpVerificationPolicy` and OTP enums | Verification domain |
| `OtpId`, `OtpAuditId` | Verification-owned value objects |
| `CustomerId` | Customer-owned value object |
| `OtpVerificationCommand` | Verification-owned contract; its exact layer depends on whether it is an application request or domain operation input. |
| `PrefixedIdValidator` | Customer domain value-object package; exact prefix and ASCII suffix validation |
| Keycloak API client implementation | `access.infrastructure.keycloak` |
| HTTP JWT security configuration | Service composition / inbound security, not a domain aggregate |

Keep the current `value_object` spelling during this migration. A naming-style
cleanup must not be mixed into behavioral changes.

## 5. Module contracts and dependency rules

**Baseline rules:** no circular module dependencies, no imports of another module's
persistence implementation, and no cross-module aggregate mutation. A module
exposes outcomes and operations, not its entire model.

The following capability-call direction is the proposed baseline:

| Caller | Allowed destination | Purpose |
| --- | --- | --- |
| Registration | Access, Customer, Verification APIs | Coordinate registration steps. |
| Customer | Access API, only where required | Establish or validate an account relationship. |
| Verification | Access API, only where required | Resolve trusted actor/access context for protected operations. |
| Access | No other Identity capability by default | Keep access identity independent from customer/registration workflows. |
| Any module | Approved shared primitives | Reuse pure validation or deliberately shared contracts. |

Do not add a reverse call for convenience. For example, Verification returns a
verification outcome; it does not call Registration to complete registration.
A consumer may depend on an event contract published by its owner, but asynchronous
communication does not excuse circular type dependencies or an undocumented flow.

A type reference is also a dependency. Verification may need an exported
`CustomerId` without permission to call Customer repositories. Export only the
required identity contract, not a whole domain package.

**Open contract placement:** the final exported-ID package arrangement must be
settled before architecture rules are enforced. In particular, making Verification
import `RegistrationId` from Registration while Registration depends on Verification
would create a cycle. Resolve this with an explicitly reviewed pure identity
contract or a Verification-owned subject/reference contract mapped by the caller.
Do not solve it with an unrestricted raw `String` or by opening both modules.

For Spring Modulith, a package called `api` is not automatically an exported named
interface. Its default API is the module base package; additional exports require
explicit configuration. Configure the chosen convention rather than relying on
its name. [Module fundamentals][4]

## 6. Identity model and typed identifiers

### 6.1 Concepts that must remain distinct

| Concept | Meaning | Owner |
| --- | --- | --- |
| External login identity | Trusted issuer and subject from the configured provider. | Keycloak; linked internally by Access. |
| Account | Stable internal access identity and access status. | Access |
| Customer | Person or business receiving PayGuard services. | Customer |
| Tenant | Organizational access/resource-isolation scope. | Access initially, subject to lifecycle review. |
| Membership | An account's relationship and permissions within a tenant. | Access |
| Registration | An unfinished or completed registration workflow. | Registration |
| Service principal | A machine identity, not a human customer session. | Provider integration and Access policy |

Customer relationships and tenant memberships branch from an internal account;
they are not a mandatory account-to-customer-to-membership chain. Cardinalities
and uniqueness constraints must match the chosen product model, not be inferred
from this table.

OpenID Connect specifies issuer and subject together as the stable provider
identity reference. Email is not an equivalent stable identifier. Provider changes
and account linking require an explicit, verified workflow. [OIDC Core][3]

### 6.2 Identifier contract

These are PayGuard conventions inspired by prefixed public IDs, not claims about
Stripe's internal generation rules.

| Value object | Prefix | Total ASCII length | Generation owner |
| --- | --- | --- | --- |
| `AccountId` | `acc_` | 26 | Access |
| `CustomerId` | `cus_` | 26 | Customer |
| `OtpId` | `otp_` | 26 | Verification |
| `OtpAuditId` | `oaud_` | 27 | Verification |
| `RegistrationId` | `reg_` | 26 | Registration |
| `TenantId` | `ten_` | 26 | Access initially |
| `MembershipId` | `mem_` | 26 | Access |

Every suffix has exactly 22 case-sensitive ASCII Base62 characters: `A-Z`, `a-z`,
and `0-9`. Reject nulls, incorrect prefixes, incorrect lengths, whitespace, and
all other characters. Do not trim, normalize, or change case.

Value objects are immutable `final` classes with private constructors, a
`parse(String)` factory, `value()`, and type-specific value equality/hash codes.
Constructor validation must prevent invalid construction; parsing does not generate
an identity or contact a database.

A separate generator supplies new IDs to the owning module. Use a cryptographically
secure source such as Java `SecureRandom` and an unbiased alphabet-selection
procedure. A random suffix is not an absolute uniqueness guarantee. [Java API][8]

Persistence must enforce uniqueness and preserve case-sensitive comparison
semantics. JSON, message payloads, database columns, and Redis serialization use
strings at their boundaries; internal application/domain contracts use typed IDs.

`CustomerId` parsing establishes format, not existence, ownership, or permission.
Identifiers must contain no customer information and must not serve as credentials.

### 6.3 Current migration rule

Both `OTP.issue()` and `OTP.restore()` must accept typed identifiers. Convert raw
incoming values in boundary adapters, not inside the aggregate. Repository adapters
reconstitute typed values when reading persisted representations.

Verification never generates a new customer identifier for each challenge. It
receives the existing relationship, or an explicitly supported non-customer subject.

## 7. Authentication and authorization boundaries

Keycloak is the selected authentication provider. Its supported capabilities include
credentials, sessions, authentication flows, and WebAuthn/passkeys; deployment and
configuration remain PayGuard's operational responsibility. [Keycloak guide][1]

An Access-to-Keycloak adapter is needed for actual provider API operations, not to
proxy every login through Identity. Domain types must not expose provider SDK types.
Login can take place through the provider's standard client flow.

The target inbound security contract requires appropriate access-token validation:
trusted issuer, intended audience, accepted algorithm, signature, and time claims.
Spring Security provides JWT resource-server mechanisms; explicitly configure the
checks against the versions pinned in the build. An ID token is not the API bearer
credential. [Resource server documentation][2] [OIDC Core][3]

PayGuard-specific authorization remains explicit. The selected tenant, actor,
resource, action, membership status, and applicable policy must agree. A supplied
customer ID, tenant ID, or internal-looking header is not trusted identity evidence.
Deny-by-default and request-level authorization are the baseline. [OWASP authorization][6]

Membership and permission changes require a documented freshness/revocation bound.
Do not assume logout or a role update makes every previously issued, locally
validated JWT immediately unusable. Define cache invalidation, session handling,
and rejection behavior before relying on them.

**Passkeys:** integrate the provider flow without adding a WebAuthn implementation
to `OTP` or `OtpSnapshot`. Login assurance may contribute to a sensitive-operation
policy, but is not approval of an arbitrary operation. Keycloak supplies the
mechanism; PayGuard defines the meaning of the result. [Keycloak guide][1]

The existing TOTP verification branch must receive a separate ownership review:
provider-owned login MFA must not silently become a second credential store in
Verification. A caller-supplied boolean is not trusted verification evidence.

## 8. Verification model and state transitions

### 8.1 Existing domain contract

The last supplied snapshot design enforces these rules:

| Rule | Meaning |
| --- | --- |
| `maxAttempts >= 1` | A challenge permits at least one attempt. |
| `0 <= attempts <= maxAttempts` | Recorded attempts remain within the configured limit. |
| `ACTIVE` implies `attempts < maxAttempts` | An active snapshot is not already exhausted. |
| `expiresAt > createdAt` | The validity interval is nonempty. |
| `USED` if and only if `consumedAt` is present | Consumption state and timestamp agree. |
| `createdAt <= consumedAt < expiresAt` | Consumption falls within the validity interval. |
| Random-numeric mode requires a nonblank hash | Verification data is consistent with the selected mode. |

Retention must satisfy the existing `OtpRetentionPolicy`; this document does not
invent a retention duration. `ACTIVE` is not sufficient eligibility by itself:
expiry, purpose, and other required context still have to be checked.

For an eligible attempt, the required completed transitions are:

| Outcome | Next status | Attempt change | Consumption time |
| --- | --- | --- | --- |
| Wrong code; another attempt remains | `ACTIVE` | Add one | Absent |
| Wrong code; final allowed attempt | `LOCKED` | Add one | Absent |
| Correct code, including the final attempt | `USED` | Add one | Set to the accepted operation time |

Successful code verification counts as an attempt in this contract. Preliminary
rejections are handled separately; preserve their existing precedence and counting
behavior unless a dedicated contract change explicitly alters them.

### 8.2 Complete-state construction

Verification determines eligibility and code outcome, constructs a complete valid
next snapshot, and only then assigns the aggregate field. A helper for final success
must set attempts, status, and consumption time before the snapshot is built.

Do not build an exhausted `ACTIVE` snapshot and try to repair it afterward. Do not
mutate the aggregate early and rely on a later exception to undo it.

An exception during hashing or next-state construction must leave the aggregate's
snapshot unchanged. A domain rejection such as an expired challenge can deliberately
produce a valid state change and a normal verification result.

A local snapshot variable is not mandatory: assigning a successfully constructed
final snapshot directly to the field is valid. The requirement is the completed
transition before assignment, not a variable-naming convention.

### 8.3 Unresolved input and subject contracts

The following require explicit decisions, not silent refactoring: handling a supplied
time before creation, required command fields, mode-specific hasher requirements,
preliminary rejection precedence, and trusted TOTP-result provenance.

The current mandatory `CustomerId` supports existing-customer challenges only.
Before pre-customer registration is supported, define the verification subject,
caller authorization, contact binding, and recovery rules. Do not use a fabricated
customer, a nullable collection of unrelated IDs, or an unrestricted generic ID.
The final Java representation is intentionally not selected in this document.

### 8.4 Implemented Verification contracts

- `OtpChallengeRepository.replaceActive(OTP)` invalidates ACTIVE challenges for the
  same customer and purpose, then saves the replacement in one adapter transaction.
  Channel and destination do not narrow this scope. Persistent customer/purpose scope rows
  serialize even first issuance; a partial unique index enforces one ACTIVE row.
  `save` inserts only. `withLockedChallenge` restores and mutates the current row under
  a PostgreSQL lock, committing before the application throws a business rejection.
- `OtpDelivery.submit(OtpDeliveryRequest)` is an application dependency. The request
  carries typed challenge/customer IDs and the actual code for adapter access; its
  diagnostics omit the code and destination. `destinationRef` remains a caller
  reference with no implemented provider-resolution contract. The mock discards
  codes; SMS/email/push modes now throw explicitly because their provider integrations
  are absent. Completion does not mean confirmed customer receipt.
- `VerificationAudit.record(VerificationAuditEntry)` preserves synchronous audit
  persistence and propagated failures. The entry contains a typed challenge and
  customer subject, closed action set (`ISSUED`, `VERIFY`), reason code, source IP,
  and timestamp. No trusted actor or correlation context is currently supplied;
  the subject is not labeled an authenticated actor. Audit commits separately from
  challenge state, so an audit failure can follow committed consumption.
- `VerificationQuota.tryAcquire(Request)` consumes quota for ISSUE or VERIFY,
  scoped by customer, purpose, and source IP. The existing Redis INCR and first-use
  EXPIRE behavior, limits, key format, and propagated storage errors are preserved.
  INCR is atomic; the two-command increment/expiry sequence is not atomic. There
  is no new gateway rate limiter, and per-challenge attempts are a separate control.
- `TotpVerification` verifies codes server-side via the existing trusted secret
  port. The HTTP request cannot supply `authenticatorCodeValid`. Missing secrets
  return absence; a denied or unavailable Vault dependency throws before code-check
  mutation. The production adapter reads customer-scoped KV v2 seeds; enrollment and
  recovery remain absent. LOGIN ownership conflicts with
  the intended provider-owned MFA and still requires a decision.
- `OtpCodeHasher` remains the single active hash/match contract. No `CodeHasher`
  exists in the inspected tree. Salted HMAC-SHA256 now stores `keyId:salt:digest`;
  old `salt:digest` requires an explicitly selected legacy key. Vault key refresh runs
  outside persistence locks and preserves the configured OTP compatibility window.

The current use case supports customer subjects only. Registration/account subjects,
trusted caller authentication/authorization, operation-bound evidence, and retryable
provider delivery are not implemented. A successful response reports `challengeId`,
`purpose`, and `verifiedAt`; it is not a transferable proof for a financial action.

The domain's existing minimum retention is three calendar years after expiry.
The application/resource defaults now use 1097 days from issuance to accommodate
that invariant for the default challenge lifetimes. Explicit shorter configurations
still fail validation. No purge scheduler is implemented here.

## 9. Application workflows

### 9.1 Existing-customer challenge verification

The inbound adapter validates request shape and parses IDs. The application use case
establishes trusted actor context, loads the challenge, and checks that the caller
may operate on that subject. The aggregate evaluates its own verification rules.
The application persists the resulting state under the chosen concurrency contract,
then returns the committed outcome through the adapter.

Do not report a successful consumption before its required persistence succeeds.
Do not return the full internal snapshot as a public response: it contains internal
verification material such as hashes.

### 9.2 Registration

A candidate product flow is: start a pending registration, request contact
verification, record the matching completed verification, ensure the required
Access account and Customer relationship exist, and mark registration complete.

The precise order of provider identity creation, internal account creation, and
contact verification is an open product/security decision. Public pre-login endpoints
need an explicit registration-session or equivalent trusted-flow contract; a public
`reg_...` identifier alone must not authorize progression or account linking.

Registration consumes Verification's result and applies its own completion rules.
It must bind the result to the same registration and contact; changing the contact
invalidates the previous proof for that step.

Retries must recover the existing logical registration/account/customer relationship.
A committed finalization followed by a lost HTTP response is not permission to create
another customer. For separately committed steps, persist progress and reconcile
completed effects after a crash.

### 9.3 Login and internal identity resolution

The client authenticates through Keycloak. Protected Identity endpoints validate the
appropriate credential and Access resolves the trusted provider identity to the
internal account. Customer links and tenant memberships are resolved independently
as required by the use case.

An unknown provider identity follows an explicit provisioning or rejection policy;
it must not silently become an active customer. Changing a provider link requires
verified account-linking behavior, not email matching alone.

### 9.4 Verification for a sensitive operation

The requesting service supplies an authorized, bounded operation context. Verification
issues and completes the relevant challenge and produces or records evidence. The
requesting service validates that evidence and retains the final execution decision.

Evidence must be bound to its subject, purpose, operation, validity window, and
significant approved terms or an immutable operation version. It must be trusted
and tamper-resistant, and the user must be able to identify the significant terms
being confirmed. Changing those terms invalidates the prior approval. These
requirements follow transaction-authorization guidance. [OWASP transaction authorization][7]

The evidence representation and consumption protocol are still open decisions.
An identifier alone is not evidence. Challenge consumption and evidence consumption
are separate state changes; neither proves exactly-once execution in another service.

## 10. Persistence, transactions, and concurrency

### 10.1 Ownership and transaction scope

The initial target allows a shared database, with owner-specific tables and migrations.
PostgreSQL/JPA and Vault belong behind infrastructure adapters; domain code must not import generated
records or SQL clients. Repository contracts express required behavior, including
conflict handling, rather than exposing adapter details.

Local transactions may span selected module operations only through their APIs and
compatible transaction participation. Document those cases. External provider,
delivery, and broker calls are not part of the database transaction.

Cross-module database constraints require explicit agreement. Prefer owner-supported
queries or dedicated read projections over hidden dependencies on another module's
schema. A shared database must not become unrestricted shared state.

### 10.2 Competing requests

Never assume one aggregate instance is safe for concurrent mutation. Loading one
instance per request also does not prevent two requests from updating the same row.

ADR 0014 selects database row locks for verification and durable scope locks for
issuance. Each verification re-evaluates current state and time after acquiring the
lock. Only one caller receives fresh VERIFIED; competitors receive ALREADY_USED.
Attempts and expiry rejection transitions commit before the HTTP/business exception.
Remote calls remain outside the lock; no quota or external side effect is retried.

Use concurrency tests with competing requests against the actual persistence adapter.
For one active challenge, only one distinct operation may commit its consumption.
The current API has no stored idempotent verification response; a replay returns
ALREADY_USED rather than a new success.

### 10.3 Retry and side-effect rules

An idempotency contract identifies the logical operation, caller scope, payload
compatibility, stored outcome, retention window, and recovery behavior. Database
constraints enforce the uniqueness relationships the contract relies on.

For durable work that must follow committed state, use a transactional outbox or
another explicitly durable mechanism. Consumers must handle duplicate delivery.
Do not assume an in-memory event or a database commit followed by a best-effort
publish is sufficient.

Do not hold database locks open while waiting for SMS, email, or Keycloak. Persist
retryable workflow state around external side effects. Expired challenges must not
be revived by a delayed delivery retry.

## 11. Security, privacy, and operations

Identity endpoints must enforce resource ownership and tenant membership in addition
to token validity. Apply admission/rate limits to issuing, verifying, and registration
flows; issuing a new challenge must not trivially bypass the intended abuse policy.
Choose rate-limit subjects and storage atomicity in a dedicated implementation ticket.

Secrets, OTP values, credential hashes, bearer tokens, and step-up tokens must not
appear in ordinary logs. Record only necessary actor references, action, outcome,
correlation context, and policy-relevant audit metadata. Public identifiers remain
potentially linkable information; being non-secret does not justify unrestricted
logging or retention.

Protect durable delivery data that temporarily needs a code, restrict access, and
remove it when no longer required. Do not persist delivery secrets merely because
an outbox is used. Retention and access rules apply to business data, audit records,
logs, and retry payloads separately.

Define behavior for missing authorization information, provider outages, unavailable
storage, and exhausted delivery retries. Protected operations must not become
permitted because a dependency failed. Continued use of existing sessions or cached
policy data requires an explicit bounded policy, not an accidental fallback.

Measure latency, rejection causes, persistence conflicts, provider failures, delivery
retries, and incomplete registration age. Never claim a capacity or availability
level without an appropriate measured workload and failure test.

## 12. Testing and architectural enforcement

These are completion requirements, not claims that the tests already exist.

| Test level | Required evidence |
| --- | --- |
| Identifier unit tests | Valid/invalid prefixes, 21/22/23-character suffixes, null/blank/Unicode inputs, exact case preservation, equality/hash-code contract, distinct ID types. |
| Snapshot and aggregate tests | Final-attempt success/failure, normal failure, expired/terminal behavior, hash/build exceptions preserving old state, restoration rejecting exhausted active state. |
| Application tests | Authorization precedes protected effects; failed persistence cannot return committed success; retry results are consistent. |
| Persistence integration tests | Typed-ID round trips, case-sensitive uniqueness, rollback, competing updates, and idempotency constraints. |
| Boundary tests | Malformed IDs, wrong token issuer/audience/signature/time, spoofed tenant/customer context, no secret-bearing response payloads. |
| Registration integration tests | Duplicate finalization, lost response, crashes between separately committed steps, changed contact, incorrect challenge subject. |
| Architecture tests | Allowed exports only, no cycles, no cross-module repository access, framework-free domain, no provider SDK in public contracts. |

Spring Modulith's verification can check cycles and internal-package access. Its
allowed-dependency restriction is optional and must be configured. Additional
architecture rules may be needed for the layering and framework constraints above.
[Module verification][5]

An exception-safe aggregate, a passing unit test, and a passing concurrency test
prove different things. Record test scope and actual results separately.

## 13. Migration and implementation order

The following are planning references, not claims that tickets were created in an
external tracker. Keep correctness fixes separate from package moves and new behavior.

| Order | Work | Completion condition |
| --- | --- | --- |
| 1 | Finish typed issuance (`PG-IDENTITY-109`). | `issue()` and `restore()` accept typed IDs; boundary callers compile; contract tests pass. |
| 2 | Review identifier implementations and tests. | Shared format rules and every ID's equality/validation contract are demonstrated. |
| 3 | Correct ownership imports (`PG-IDENTITY-110`). | `OtpId`/OTP enums belong to Verification; changes preserve behavior. |
| 4 | Decide exported contracts and enforce boundaries. | API/ID exports and allowed dependency graph are explicit and tested. |
| 5 | Complete non-customer subject design (`PG-IDENTITY-111`). | Registration challenges work without fake customers or dependency cycles. |
| 6 | Implement provider integration and account mapping. | Resource-server boundary and trusted identity mapping pass integration tests. |
| 7 | Implement registration/access/customer use cases. | Lifecycle, uniqueness, retry, and membership policies are demonstrated. |
| 8 | Harden persistence and verification evidence. | Concurrent consumption, replay prevention, delivery recovery, and revocation behavior are tested. |

When replacing UUID challenges or raw external user IDs, choose a compatibility
window or explicit migration. Preserve existing links, outstanding challenges,
Redis keys, and dependent evidence. Never generate a fresh customer identity for
each historical request. Do not deploy incompatible API and persistence formats
without an agreed rollout sequence.

**Completed refactoring:** typed customer/challenge contracts and Verification
package wiring, PostgreSQL concurrency control and scoped Vault secret adapters.
Keycloak setup, non-customer subjects and operation-bound evidence remain separate work.
See [the implementation guide](../docs/guides/postgresql-vault-migration.md) for exact
versions, role boundaries, migration history, key/pool lifecycle and test evidence.

## 14. Open decisions and extraction criteria

| Decision still needed | Must be settled before |
| --- | --- |
| Exported identifier packages and non-customer subject representation | Enforcing module dependencies and shipping registration verification. |
| Account/customer relationship cardinality and tenant lifecycle ownership | Finalizing schemas and uniqueness constraints. |
| Provider provisioning order and registration-session trust | Shipping pre-login registration and account linking. |
| Required verification inputs and pre-creation timestamp handling | Declaring the verification input contract complete. |
| Financial operation idempotency and durable publication | Extending the verified OTP concurrency contract to downstream financial effects. |
| Evidence format, consumer contract, and recovery semantics | Shipping sensitive-operation approval. |
| Token, permission, and cache freshness/revocation limits | Claiming timely account or membership revocation. |
| Outbox/delivery secret handling and retention | Enabling durable asynchronous delivery. |
| Pinned compatible framework/provider versions | Shipping the integration; current documentation is not a version selection. |

A module becomes a candidate for extraction when measured workload asymmetry,
independent release needs, availability requirements, or security isolation justify
a separate deployment. Extraction must revisit network contracts, authentication,
data ownership, timeouts, retries, and local transaction assumptions.

Four folders are not evidence that four services are needed. Shared deployment is
not a commitment to remain shared forever.

Changes to module ownership, public contracts, trust boundaries, and transaction
scope require a reviewed architecture decision and an update to this document.

## 15. References

These primary references support the protocol and framework statements. PayGuard's
ownership, packaging, and workflow choices are project design decisions, not claims
that a referenced organization uses this architecture. References checked on
2026-10-10; use documentation compatible with the versions actually pinned in the build.

1. [Keycloak Server Administration Guide][1] - authentication, sessions, WebAuthn, and passkeys.
2. [Spring Security JWT Resource Server][2] - token validation and audience configuration.
3. [OpenID Connect Core 1.0][3] - identity claims and issuer/subject stability.
4. [Spring Modulith Fundamentals][4] - module APIs, named interfaces, and dependency declarations.
5. [Spring Modulith Structure Verification][5] - architectural verification and its limits.
6. [OWASP Authorization Cheat Sheet][6] - deny-by-default and request-level access checks.
7. [OWASP Transaction Authorization Cheat Sheet][7] - operation binding and confirmation integrity.
8. [Java 21 SecureRandom API][8] - cryptographically strong random generation.

[1]: https://www.keycloak.org/docs/latest/server_admin/index.html
[2]: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
[3]: https://openid.net/specs/openid-connect-core-1_0.html#ClaimStability
[4]: https://docs.spring.io/spring-modulith/reference/fundamentals.html
[5]: https://docs.spring.io/spring-modulith/reference/verification.html
[6]: https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
[7]: https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html
[8]: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureRandom.html
