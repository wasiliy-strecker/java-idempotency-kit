# Repository guide

## Scope

This repository is a production-minded idempotency library. Preserve explicit guarantees, atomic store transitions, bounded retention, safe diagnostics, and a framework-free core.

## Architecture

- `idempotency-core` owns the public state-machine contract and has no external runtime dependencies.
- Store modules implement the same contract; PostgreSQL-specific behavior stays out of core.
- Spring auto-configuration may depend on adapters, but adapters never depend on Spring.
- `examples/order-api` is a consumer and never becomes a dependency of library modules.

## Commands

```bash
./mvnw spotless:apply
./mvnw verify
./mvnw -Pintegration verify
```

The integration profile must fail when Docker is unavailable; it must never silently skip PostgreSQL guarantees.

## Change rules

- Never claim exactly-once execution.
- Never persist or log raw idempotency keys, payload fingerprints, request bodies, or exception messages.
- Every store transition change requires memory and PostgreSQL contract coverage.
- Keep Java 21 compatibility and avoid preview APIs.
- Run the complete reactor and Javadoc checks before committing.
