package io.github.wasiliystrecker.idempotency.store.memory;

import io.github.wasiliystrecker.idempotency.core.AcquireRequest;
import io.github.wasiliystrecker.idempotency.core.Acquisition;
import io.github.wasiliystrecker.idempotency.core.CompletionRequest;
import io.github.wasiliystrecker.idempotency.core.FailurePolicy;
import io.github.wasiliystrecker.idempotency.core.FailureRequest;
import io.github.wasiliystrecker.idempotency.core.IdempotencyKey;
import io.github.wasiliystrecker.idempotency.core.IdempotencyStore;
import io.github.wasiliystrecker.idempotency.core.Lease;
import io.github.wasiliystrecker.idempotency.core.LostOwnershipException;
import io.github.wasiliystrecker.idempotency.core.RequestFingerprint;
import io.github.wasiliystrecker.idempotency.core.StoredResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic in-process store. It is suitable for tests and single-JVM deployments, but does not
 * coordinate multiple application instances or survive restarts.
 */
public final class MemoryIdempotencyStore implements IdempotencyStore {
  private final Clock clock;
  private final ConcurrentHashMap<IdempotencyKey, Entry> entries = new ConcurrentHashMap<>();

  /** Creates a store using the UTC system clock. */
  public MemoryIdempotencyStore() {
    this(Clock.systemUTC());
  }

  /**
   * Creates a deterministic store with an injected clock.
   *
   * @param clock source of lease and retention time
   */
  public MemoryIdempotencyStore(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public Acquisition acquire(AcquireRequest request) {
    Objects.requireNonNull(request, "request");
    Instant now = clock.instant();
    AtomicReference<Acquisition> decision = new AtomicReference<>();
    entries.compute(
        request.command().key(),
        (ignored, current) -> {
          if (current == null || !current.expiresAt().isAfter(now)) {
            Entry acquired = processing(request, now);
            decision.set(new Acquisition.Acquired(acquired.lease(request.command().key())));
            return acquired;
          }
          if (!current.fingerprint().equals(request.command().fingerprint())) {
            decision.set(new Acquisition.Conflict());
            return current;
          }
          return switch (current.status()) {
            case COMPLETED -> {
              decision.set(new Acquisition.Replay(Objects.requireNonNull(current.result())));
              yield current;
            }
            case PROCESSING -> {
              if (current.leaseExpiresAt().isAfter(now)) {
                decision.set(new Acquisition.InProgress(current.leaseExpiresAt()));
                yield current;
              }
              Entry acquired = processing(request, now);
              decision.set(new Acquisition.Acquired(acquired.lease(request.command().key())));
              yield acquired;
            }
            case FAILED -> {
              if (!current.retryable()) {
                decision.set(new Acquisition.PreviousFailure());
                yield current;
              }
              Entry acquired = processing(request, now);
              decision.set(new Acquisition.Acquired(acquired.lease(request.command().key())));
              yield acquired;
            }
          };
        });
    return Objects.requireNonNull(decision.get(), "acquisition decision");
  }

  @Override
  public void complete(CompletionRequest request) {
    Objects.requireNonNull(request, "request");
    Instant now = clock.instant();
    AtomicReference<Boolean> completed = new AtomicReference<>(false);
    entries.computeIfPresent(
        request.lease().key(),
        (ignored, current) -> {
          if (!owns(current, request.lease(), now)) {
            return current;
          }
          completed.set(true);
          return new Entry(
              current.fingerprint(),
              Status.COMPLETED,
              null,
              null,
              now.plus(request.retention()),
              request.result(),
              false);
        });
    if (!completed.get()) {
      throw new LostOwnershipException(request.lease().key());
    }
  }

  @Override
  public void fail(FailureRequest request) {
    Objects.requireNonNull(request, "request");
    Instant now = clock.instant();
    AtomicReference<Boolean> failed = new AtomicReference<>(false);
    entries.computeIfPresent(
        request.lease().key(),
        (ignored, current) -> {
          if (!owns(current, request.lease(), now)) {
            return current;
          }
          failed.set(true);
          return new Entry(
              current.fingerprint(),
              Status.FAILED,
              null,
              null,
              now.plus(request.retention()),
              null,
              request.policy() == FailurePolicy.RETRY);
        });
    if (!failed.get()) {
      throw new LostOwnershipException(request.lease().key());
    }
  }

  @Override
  public int purgeExpired(int maxRecords) {
    if (maxRecords < 1) {
      throw new IllegalArgumentException("maxRecords must be positive");
    }
    Instant now = clock.instant();
    AtomicInteger removed = new AtomicInteger();
    for (var entry : entries.entrySet()) {
      if (removed.get() >= maxRecords) {
        break;
      }
      if (!entry.getValue().expiresAt().isAfter(now)
          && entries.remove(entry.getKey(), entry.getValue())) {
        removed.incrementAndGet();
      }
    }
    return removed.get();
  }

  /**
   * Returns the number of currently retained entries.
   *
   * @return local entry count
   */
  public int size() {
    return entries.size();
  }

  private static Entry processing(AcquireRequest request, Instant now) {
    return new Entry(
        request.command().fingerprint(),
        Status.PROCESSING,
        request.ownerToken(),
        now.plus(request.command().lease()),
        now.plus(request.command().retention()),
        null,
        false);
  }

  private static boolean owns(Entry entry, Lease lease, Instant now) {
    return entry.status() == Status.PROCESSING
        && Objects.equals(entry.ownerToken(), lease.ownerToken())
        && entry.fingerprint().equals(lease.fingerprint())
        && entry.leaseExpiresAt().isAfter(now);
  }

  private enum Status {
    PROCESSING,
    COMPLETED,
    FAILED
  }

  private record Entry(
      RequestFingerprint fingerprint,
      Status status,
      UUID ownerToken,
      Instant leaseExpiresAt,
      Instant expiresAt,
      StoredResult result,
      boolean retryable) {
    Lease lease(IdempotencyKey key) {
      return new Lease(key, fingerprint, ownerToken, leaseExpiresAt);
    }
  }
}
