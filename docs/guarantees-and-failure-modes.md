# Guarantees and failure modes

## Precise guarantee

For one store identity `(namespace, key digest)`, a conforming store atomically grants at most one **live lease** at a time. Only the current owner token may complete or fail that attempt while its lease remains live. A completed result is replayed only when the request fingerprint and codec identifier match.

This is an ownership and replay guarantee. It is not a universal exactly-once guarantee.

## Failure matrix

| Failure point | Persisted state | Next identical request | Duplicate side-effect risk |
|---|---|---|---:|
| Before acquisition commits | No new owner | Acquires normally | No |
| After acquisition, before business work | `PROCESSING` | Waits, then takes over after lease | No business effect yet |
| Business method throws `Exception` | `FAILED` | Retries or blocks according to policy | Depends on partial business effects |
| Process dies during business work | `PROCESSING` | Takes over after lease | Yes, if work committed externally |
| Business transaction commits, process dies before `complete` | `PROCESSING` | Takes over after lease | **Yes** |
| Result serialization fails | `FAILED` with `RETAIN` | Blocks until retention expires | Business work may already have committed |
| `complete` commits, HTTP response is lost | `COMPLETED` | Replays the stored response | No repeated library-wrapped work |
| Lease expires before terminal write | Another owner may take over | Stale owner receives `LostOwnershipException` | Yes if stale work still commits |
| Store is unavailable before acquisition | No decision | Request fails closed | No wrapped work is started |

The most important window is a committed business effect followed by a crash before the idempotency result commits. No generic library can atomically cover that window across arbitrary databases, payment providers, HTTP services, files, and message brokers.

## Choosing a failure policy

### `RETAIN`

Use `RETAIN` when blindly repeating a partially successful effect is more dangerous than manual recovery. Later identical calls receive `PreviousAttemptFailedException` until the record expires.

Typical examples are payment submission, external provisioning, or an operation whose exception does not prove that the remote side rejected it.

### `RETRY`

Use `RETRY` only when the operation is safe to attempt again or has its own deduplication boundary. The next identical request can acquire immediately after a recorded application exception.

Typical examples are deterministic calculations, local writes protected by another unique constraint, or a remote API that accepts the same idempotency key.

Errors outside `Exception`, including serious JVM errors, are not converted to a failed record. The processing lease remains until takeover becomes legal.

## Lease sizing

The lease must exceed the expected high-percentile operation duration plus scheduling and store latency. A lease that is too short permits concurrent stale and replacement work. A lease that is too long delays recovery after a dead process.

The current version does not renew leases. Long-running or unbounded jobs should use a different orchestration model or split work into bounded, independently idempotent steps.

## Reducing duplicate risk

- Pass the same idempotency key to downstream systems that support one.
- Protect local business identities with database unique constraints.
- Use an outbox for committed events and make consumers idempotent.
- Reconcile uncertain external outcomes before selecting `RETRY`.
- Keep the wrapped business operation smaller than its lease.
- Alert on `LostOwnershipException`, repeated `in_progress`, and store failures.

If the business row and idempotency result must commit atomically in one database transaction, build a domain-specific integration that owns that transaction boundary. The generic Spring aspect intentionally uses a separate acquisition and completion lifecycle.

## Identity and confidentiality

- Raw idempotency keys are accepted transiently, hashed immediately, and not retained by the core identity type.
- Request fingerprints are SHA-256 digests of canonical input bytes.
- Exceptions expose only a 12-character digest reference for correlation.
- Metrics use static namespaces and bounded outcome labels.
- Successful result payloads **are stored** and are not encrypted by this library.

Database access controls, encryption at rest, backups, and retention must match the sensitivity of serialized results. SHA-256 does not hide a low-entropy key from an offline guessing attack, so clients should generate high-entropy idempotency keys.

## Operational PostgreSQL notes

- Apply `db/idempotency/postgresql/V1__create_idempotency_record.sql` before accepting traffic.
- Keep database clocks synchronized; the adapter deliberately trusts database time rather than JVM time.
- Monitor table growth and keep bounded cleanup enabled or provide equivalent retention maintenance.
- Run multiple cleanup workers safely; `SKIP LOCKED` partitions each batch.
- Treat manual row deletion as an operation that can permit work to execute again.
- Back up completed result bytes if replay across disaster recovery is required.
