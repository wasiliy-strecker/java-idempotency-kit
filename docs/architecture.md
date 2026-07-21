# Architecture

## Design goals

The library separates idempotency semantics from storage and framework integration:

- one explicit state machine across all stores;
- no Spring, JDBC, JSON, or logging dependency in core;
- atomic decisions delegated to each store implementation;
- no raw request keys or request payloads in persisted identity records;
- bounded result storage and bounded cleanup work;
- stable behavior that can be verified by a reusable contract suite.

## Module dependency direction

```mermaid
flowchart TD
    Core[idempotency-core]
    Testkit[idempotency-testkit]
    Memory[idempotency-store-memory]
    JDBC[idempotency-store-jdbc]
    Auto[idempotency-spring-boot-autoconfigure]
    Starter[idempotency-spring-boot-starter]
    Example[examples/order-api]

    Testkit --> Core
    Memory --> Core
    JDBC --> Core
    Auto --> Core
    Auto --> Memory
    Auto --> JDBC
    Starter --> Auto
    Starter --> Memory
    Starter --> JDBC
    Example --> Starter
```

Store modules never depend on Spring. The example is a consumer, not a source of shared library behavior.

## State machine

```mermaid
stateDiagram-v2
    [*] --> PROCESSING: acquire new key
    PROCESSING --> PROCESSING: lease expired / atomic takeover
    PROCESSING --> COMPLETED: current owner stores result
    PROCESSING --> FAILED_RETRY: current owner fails with RETRY
    PROCESSING --> FAILED_RETAIN: current owner fails with RETAIN
    FAILED_RETRY --> PROCESSING: next identical request
    FAILED_RETAIN --> PROCESSING: retention expired
    COMPLETED --> PROCESSING: retention expired
    COMPLETED --> COMPLETED: identical request replays result
```

At every protected state, a changed request fingerprint produces `Conflict`. A live `PROCESSING` lease produces `InProgress`. Terminal writes require the namespace, key digest, fingerprint, state, owner token, and a still-live lease to match.

## PostgreSQL acquisition

The JDBC adapter uses database time and a short transaction:

1. `INSERT ... ON CONFLICT DO NOTHING RETURNING` handles the common new-key path.
2. The existing-key path selects the row `FOR UPDATE`.
3. The locked row is evaluated for retention expiry, fingerprint conflict, state, and lease expiry.
4. A takeover replaces the owner token and deadlines in the same transaction.
5. The transaction commits before business code runs.

Completion and failure are single guarded `UPDATE` statements. A stale owner updates zero rows and receives `LostOwnershipException`. Cleanup selects a bounded batch with `FOR UPDATE SKIP LOCKED`, so concurrent cleanup workers do not serialize the whole table.

The schema also enforces valid shapes for processing, completed, and failed records with a database check constraint.

## Spring invocation flow

```mermaid
sequenceDiagram
    participant C as HTTP client
    participant A as @Idempotent aspect
    participant S as IdempotencyStore
    participant T as Business transaction

    C->>A: key + request
    A->>A: hash key and canonical fingerprint
    A->>S: acquire(owner token, lease)
    alt completed
        S-->>A: stored codec + bytes
        A-->>C: decoded result
    else acquired
        S-->>A: exclusive lease
        A->>T: invoke annotated method
        T-->>A: committed result
        A->>S: complete if owner and lease still match
        A-->>C: result
    else conflict or live owner
        S-->>A: typed rejection
        A-->>C: application maps rejection
    end
```

The aspect is ordered ahead of transaction advice. It supports synchronous, non-void methods. Spring proxy limitations still apply: self-invocation is not intercepted, and final methods/classes cannot use class-based proxies.

## Result compatibility

Spring results are serialized with the application `ObjectMapper`. The stored codec identifier includes a SHA-256 digest of the declared generic return type and the annotation's explicit `resultVersion`. Changing the return type or incrementing `resultVersion` while old records remain produces `ResultCodecMismatchException` instead of attempting an unsafe decode.

For long-lived public APIs, introduce explicit versioned DTO return types, increment `resultVersion` before incompatible field changes, and coordinate schema evolution with the configured retention period.

## Observability

Micrometer records operation counts and durations with two bounded tags:

- `namespace`: the static annotation namespace;
- `outcome`: `executed`, `replayed`, `in_progress`, `conflict`, `previous_failure`, or `error`.

Raw keys, digest values, request bodies, result bodies, and exception messages are not meter tags.
