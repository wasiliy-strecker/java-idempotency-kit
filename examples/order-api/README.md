# Idempotent Order API

This application proves the starter against a concrete business workflow instead of demonstrating an isolated annotation.

`POST /api/orders` accepts a validated order command and an `Idempotency-Key` header. The first request inserts one order inside a Spring transaction. An identical retry returns the original typed response. Reusing the key for changed order data returns a structured `409 Conflict`.

## Start

From the repository root:

```bash
docker compose up --build
```

The API listens on `http://localhost:8080`; PostgreSQL listens on `localhost:5432`. Flyway applies both the library-owned idempotency schema and the example-owned order schema.

## Exercise the behavior

```bash
curl -i http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-demo-1' \
  -d '{"customerReference":"customer-42","sku":"keyboard","quantity":2,"unitPrice":49.90}'
```

Repeat it unchanged, then change `quantity` while preserving the header. The first two responses represent the same persisted order; the changed command returns Problem Details with a safe key reference.

Useful operational endpoints:

- `/actuator/health`
- `/actuator/metrics/idempotency.operations`
- `/actuator/metrics/idempotency.operation.duration`
- `/actuator/metrics/idempotency.cleanup.records`

## Test

The end-to-end test starts PostgreSQL 18.3, boots the application on a random port, sends real HTTP requests, and asserts one business row:

```bash
./mvnw -Pintegration -pl examples/order-api -am verify
```

Docker is mandatory for this profile. A missing daemon fails the build so that PostgreSQL concurrency behavior is never reported as verified when it was skipped.
