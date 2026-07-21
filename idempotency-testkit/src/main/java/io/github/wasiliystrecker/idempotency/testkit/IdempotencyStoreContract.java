package io.github.wasiliystrecker.idempotency.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wasiliystrecker.idempotency.core.AcquireRequest;
import io.github.wasiliystrecker.idempotency.core.Acquisition;
import io.github.wasiliystrecker.idempotency.core.CompletionRequest;
import io.github.wasiliystrecker.idempotency.core.FailurePolicy;
import io.github.wasiliystrecker.idempotency.core.FailureRequest;
import io.github.wasiliystrecker.idempotency.core.IdempotencyCommand;
import io.github.wasiliystrecker.idempotency.core.IdempotencyKey;
import io.github.wasiliystrecker.idempotency.core.IdempotencyStore;
import io.github.wasiliystrecker.idempotency.core.Lease;
import io.github.wasiliystrecker.idempotency.core.LostOwnershipException;
import io.github.wasiliystrecker.idempotency.core.RequestFingerprint;
import io.github.wasiliystrecker.idempotency.core.StoredResult;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Technology-neutral behavioral contract that every store implementation must satisfy. Implementors
 * supply isolation and a way to advance their authoritative clock.
 */
public abstract class IdempotencyStoreContract {
  private static final Duration LEASE = Duration.ofSeconds(1);
  private static final Duration RETENTION = Duration.ofSeconds(5);
  private static final StoredResult RESULT = new StoredResult("text/v1", new byte[] {1, 2, 3});

  private IdempotencyStore store;

  /** Creates a store contract for a concrete implementation test. */
  protected IdempotencyStoreContract() {}

  /** Creates a clean store before each contract test. */
  @BeforeEach
  final void createCleanStore() {
    store = newStore();
  }

  /**
   * Creates an isolated, empty store.
   *
   * @return store under test
   */
  protected abstract IdempotencyStore newStore();

  /**
   * Advances the clock authoritative for the store.
   *
   * @param duration elapsed time
   */
  protected abstract void elapse(Duration duration);

  /** Verifies that a completed value is replayed without granting another lease. */
  @Test
  final void completesAndReplaysResult() {
    IdempotencyCommand command = command("checkout-1", "payload-a");
    Lease lease = acquired(acquire(command));

    store.complete(new CompletionRequest(lease, RESULT, RETENTION));

    assertThat(acquire(command))
        .isEqualTo(new Acquisition.Replay(new StoredResult("text/v1", new byte[] {1, 2, 3})));
  }

  /** Verifies that live ownership cannot be stolen. */
  @Test
  final void reportsAnUnexpiredOperationAsInProgress() {
    IdempotencyCommand command = command("checkout-2", "payload-a");
    Lease first = acquired(acquire(command));

    assertThat(acquire(command)).isEqualTo(new Acquisition.InProgress(first.expiresAt()));
  }

  /** Verifies that one key cannot silently identify different work. */
  @Test
  final void rejectsAChangedFingerprint() {
    acquired(acquire(command("checkout-3", "payload-a")));

    assertThat(acquire(command("checkout-3", "payload-b")))
        .isInstanceOf(Acquisition.Conflict.class);
  }

  /** Verifies takeover after expiry and compare-and-set protection against a stale owner. */
  @Test
  final void takesOverAnExpiredLeaseAndRejectsTheStaleOwner() {
    IdempotencyCommand command = command("checkout-4", "payload-a");
    Lease staleLease = acquired(acquire(command));
    elapse(LEASE.plusMillis(100));

    Lease currentLease = acquired(acquire(command));

    assertThat(currentLease.ownerToken()).isNotEqualTo(staleLease.ownerToken());
    assertThatThrownBy(() -> store.complete(new CompletionRequest(staleLease, RESULT, RETENTION)))
        .isInstanceOf(LostOwnershipException.class);
  }

  /** Verifies that RETRY failures immediately allow a new owner. */
  @Test
  final void reacquiresRetryableFailures() {
    IdempotencyCommand command = command("checkout-5", "payload-a");
    Lease failedLease = acquired(acquire(command));
    store.fail(new FailureRequest(failedLease, FailurePolicy.RETRY, RETENTION));

    Lease retryLease = acquired(acquire(command));

    assertThat(retryLease.ownerToken()).isNotEqualTo(failedLease.ownerToken());
  }

  /** Verifies that RETAIN failures block duplicate side effects. */
  @Test
  final void retainsNonRetryableFailures() {
    IdempotencyCommand command = command("checkout-6", "payload-a");
    Lease failedLease = acquired(acquire(command));
    store.fail(new FailureRequest(failedLease, FailurePolicy.RETAIN, RETENTION));

    assertThat(acquire(command)).isInstanceOf(Acquisition.PreviousFailure.class);
  }

  /** Verifies bounded cleanup and reacquisition after terminal retention expires. */
  @Test
  final void purgesOnlyTheRequestedNumberOfExpiredRecords() {
    complete(command("cleanup-1", "payload-a"));
    complete(command("cleanup-2", "payload-b"));
    elapse(RETENTION.plusMillis(100));

    assertThat(store.purgeExpired(1)).isEqualTo(1);
    assertThat(store.purgeExpired(10)).isEqualTo(1);
    assertThat(store.purgeExpired(10)).isZero();
  }

  /** Verifies that namespaces isolate identical raw keys. */
  @Test
  final void isolatesOperationNamespaces() {
    IdempotencyCommand orders = command("orders", "shared-key", "payload-a");
    IdempotencyCommand refunds = command("refunds", "shared-key", "payload-a");

    assertThat(acquire(orders)).isInstanceOf(Acquisition.Acquired.class);
    assertThat(acquire(refunds)).isInstanceOf(Acquisition.Acquired.class);
  }

  private Acquisition acquire(IdempotencyCommand command) {
    return store.acquire(new AcquireRequest(command, UUID.randomUUID()));
  }

  private void complete(IdempotencyCommand command) {
    store.complete(new CompletionRequest(acquired(acquire(command)), RESULT, RETENTION));
  }

  private static Lease acquired(Acquisition acquisition) {
    assertThat(acquisition).isInstanceOf(Acquisition.Acquired.class);
    return ((Acquisition.Acquired) acquisition).lease();
  }

  private static IdempotencyCommand command(String rawKey, String payload) {
    return command("orders", rawKey, payload);
  }

  private static IdempotencyCommand command(String namespace, String rawKey, String payload) {
    return new IdempotencyCommand(
        IdempotencyKey.of(namespace, rawKey),
        RequestFingerprint.sha256(payload),
        RETENTION,
        LEASE,
        FailurePolicy.RETAIN);
  }
}
